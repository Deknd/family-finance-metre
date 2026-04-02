# Отказоустойчивый алгоритм двух workflow для `n8n`

## 1. Назначение

Этот документ описывает верхнеуровневый, но decision-complete алгоритм для двух workflow в `n8n`:

- `Workflow A / Intake Orchestrator`;
- `Workflow B / Telegram Conversation Agent`.

Цель документа:

- зафиксировать устойчивое поведение цепочки `server -> n8n -> Telegram -> server`;
- разделить deterministic-логику и LLM-логику;
- описать, как система должна вести себя при дублях, сетевых ошибках, таймаутах и некорректных ответах пользователя;
- исключить появление "зомби-сессий", двойных callback и параллельных диалогов в одном Telegram-чате.

Этот документ дополняет, а не заменяет остальные спецификации:

- [llm-agent-trigger-spec.md](./llm-agent-trigger-spec.md)
- [telegram-finance-intake-agent-spec.md](./telegram-finance-intake-agent-spec.md)
- [telegram-finance-intake-implementation-plan.md](./telegram-finance-intake-implementation-plan.md)
- [api-spec.md](../server/api-spec.md)

## 2. Границы алгоритма

В рамках этой версии фиксируются такие ограничения:

- публичные HTTP-контракты не меняются;
- стартовый webhook остается `POST /webhook/finance-intake-start`;
- успешный callback остается `POST /api/v1/intake/user-finance-data`;
- отдельный failure callback в серверное API не добавляется;
- описываются только два основных workflow, без выделения третьего workflow под manual review или alerting;
- канал общения только `Telegram`;
- пользовательские ответы считаются текстовыми; голос, файлы и фотографии не входят в этот алгоритм.

Важно:

- partial payload на сервер не отправляется;
- успешным завершением процесса считается только `202 Accepted` или `409 Conflict` с дедупликацией на intake callback;
- финальный неуспех фиксируется локально на стороне `n8n`.

## 3. Инварианты

Вне зависимости от конкретной реализации должны выполняться такие правила:

- одновременно допускается не более одной активной intake-сессии на один `request_id`;
- одновременно допускается не более одной активной intake-сессии на один `telegram_chat_id`;
- новый запуск при занятом `telegram_chat_id` отклоняется, а не замещает старую сессию и не ставится в очередь;
- `request_id` остается главным correlation id во всей цепочке;
- `external_submission_id` генерируется один раз на intake-сессию и переиспользуется во всех retry callback;
- `Workflow A` возвращает `202 Accepted` только после того, как:
  1. session state создан;
  2. активный chat-index зарегистрирован;
  3. первое сообщение в Telegram успешно отправлено;
- при любой ошибке до этой точки accepted session state и chat-index должны быть удалены или помечены как неактивные до ответа серверу;
- после перехода в терминальный статус `completed`, `failed` или `cancelled` активный chat-index удаляется немедленно;
- при retry webhook с тем же `request_id` второй диалог не запускается и второе стартовое сообщение не отправляется.

## 4. Разделение deterministic-логики и LLM-логики

### 4.1. За что отвечает deterministic-ветка

Deterministic-ветка обязана принимать решения по таким вопросам:

- проверка `Authorization`;
- schema validation стартового webhook и server callback payload;
- canonical ordering полей опроса;
- взятие и освобождение lock;
- дедупликация по `request_id`, `telegram_chat_id` и `telegram update_id`;
- управление session state и Redis keyspace;
- terminal-status policy;
- retryability callback на сервер;
- cleanup при pre-start ошибках;
- timeout, reminder, cancel и restart policy.

### 4.2. За что отвечает LLM

LLM разрешено делать только следующее:

- интерпретировать свободный ответ пользователя;
- извлекать значение для текущего поля;
- формулировать следующее сообщение пользователю;
- возвращать structured output с предполагаемым обновлением текущего шага.

LLM не имеет права самостоятельно определять:

- что webhook валиден;
- что дубль можно игнорировать;
- что callback надо ретраить или не ретраить;
- что сессию можно завершить в `completed`, `failed` или `cancelled`;
- что можно отправить payload на сервер до review и явного подтверждения пользователя.

### 4.3. Canonical ordering полей

Порядок полей фиксируется и не зависит от порядка в пользовательских сообщениях:

1. `monthly_income`
2. `monthly_expenses`
3. `monthly_credit_payments`
4. `liquid_savings`

Если в `collection_scope.fields` передан поднабор, workflow использует пересечение этого списка с canonical ordering и задает вопросы только по этим полям.

Mapping поля в session status фиксируется так:

- `monthly_income` -> `awaiting_income`;
- `monthly_expenses` -> `awaiting_expenses`;
- `monthly_credit_payments` -> `awaiting_credit_payments`;
- `liquid_savings` -> `awaiting_savings`.

## 5. Workflow A. `Intake Orchestrator`

### 5.1. Основной алгоритм

`Workflow A` обязан выполнять шаги в таком порядке:

1. Принять `POST /webhook/finance-intake-start`.
2. Проверить `Authorization: Bearer <token>`.
3. Провалидировать JSON-структуру и обязательные поля по контракту из [llm-agent-trigger-spec.md](./llm-agent-trigger-spec.md).
4. Определить `requested_fields` в canonical order.
5. Вычислить `initial_field` как первый элемент `requested_fields`.
6. Сгенерировать или получить `workflow_run_id`.
7. Взять lock по `request_id` и lock по `telegram_chat_id`.
8. Проверить, существует ли уже активная сессия по `request_id`.
9. Проверить, занят ли `telegram_chat_id` другой активной сессией.
10. Если chat-index указывает на несуществующую или терминальную сессию, удалить stale-index и продолжить.
11. Если найден активный конфликт по `request_id`, вернуть `409 Conflict`.
12. Если найден активный конфликт по `telegram_chat_id`, вернуть `409 Conflict`.
13. Сформировать `external_submission_id`.
14. Создать session state со статусом `starting`.
15. Записать active-chat index для `telegram_chat_id`.
16. Сформировать детерминированный стартовый вопрос для `initial_field`.
17. Отправить первое сообщение пользователю в Telegram.
18. Если отправка успешна, обновить session state:
    `status -> awaiting_<field>`, `current_field -> initial_field`, `last_question -> текст стартового вопроса`.
19. Освободить lock.
20. Вернуть серверу `202 Accepted` через `Respond to Webhook`.

### 5.2. Граница accepted

Считается, что опрос начался только после шага 18.

До этого момента workflow не имеет права:

- возвращать `202 Accepted`;
- оставлять активную сессию без стартового сообщения;
- оставлять занятый `telegram_chat_id`, если сообщение в Telegram не ушло.

### 5.3. Error policy до точки accepted

Если ошибка происходит на шагах 1-17, `Workflow A` обязан:

1. удалить session state, если он уже создан;
2. удалить active-chat index, если он уже записан;
3. освободить lock;
4. вернуть ошибку вызывающему серверу;
5. не отправлять повторное стартовое сообщение.

Рекомендуемая интерпретация ошибок:

- `401 Unauthorized` при неверном `Authorization`;
- `422 Unprocessable Entity` при невалидной схеме, невозможности начать Telegram-диалог или некорректном `telegram_chat_id`;
- `409 Conflict` при duplicate `request_id` или занятом `telegram_chat_id`;
- `5xx` при технической ошибке `Telegram` или `Redis`.

### 5.4. Поведение при retry стартового webhook

Если сервер не получил ответ и повторил тот же запрос:

- уже созданная активная сессия должна быть обнаружена по `request_id`;
- второе стартовое сообщение не отправляется;
- workflow возвращает `409 Conflict`;
- текущая активная сессия продолжает жить без изменений.

## 6. Workflow B. `Telegram Conversation Agent`

### 6.1. Trigger path

На каждое новое событие `Telegram Trigger` workflow обязан выполнять шаги в таком порядке:

1. Извлечь `telegram_update_id`, `telegram_chat_id`, тип сообщения и текст.
2. Попытаться создать dedupe-key по `telegram_update_id`.
3. Если dedupe-key уже существует, завершить обработку без повторного вопроса и без повторного callback.
4. По `telegram_chat_id` найти active-chat index.
5. Если active-chat index отсутствует, вежливо ответить, что активный опрос не найден.
6. Если active-chat index найден, загрузить session state по `request_id`.
7. Если session state отсутствует или уже терминален, удалить stale-index и ответить, что активный опрос не найден.
8. Взять lock по `request_id`.
9. Если lock не получен, ответить пользователю, что предыдущее сообщение еще обрабатывается, и завершить текущий запуск.
10. Если сообщение не текстовое, попросить прислать ответ обычным текстом и сохранить session state без продвижения диалога.
11. Если статус сессии равен `submitting`, ответить, что данные уже отправляются на сервер.
12. Если статус терминальный, удалить active-chat index и ответить, что активный опрос уже завершен.
13. Если сообщение является сервисной командой, обработать команду deterministic-веткой.
14. Во всех остальных случаях передать в LLM только текущее поле, последний вопрос, текст пользователя и компактный session context.

### 6.2. Structured output contract

LLM должен возвращать structured output как минимум в такой форме:

```json
{
  "reply_text": "Приняла. Теперь подскажите расходы за месяц.",
  "field_updates": {
    "monthly_income": 120000
  },
  "missing_fields": [
    "monthly_expenses",
    "monthly_credit_payments",
    "liquid_savings"
  ],
  "next_state": "awaiting_expenses",
  "is_complete": false,
  "confidence": "medium",
  "notes": [
    "Ответ был приблизительным"
  ],
  "needs_human_review": false,
  "should_submit": false
}
```

После ответа LLM deterministic-ветка обязана проверить:

- `field_updates` содержит только допустимые поля;
- вне `review` принимается обновление только для `current_field`;
- все суммы являются целыми числами `>= 0`;
- `next_state` либо совпадает с детерминированно ожидаемым состоянием, либо игнорируется;
- `missing_fields` соответствует уже заполненным `answers`;
- `should_submit = true` допускается только в состоянии `review` после явного подтверждения пользователя;
- `needs_human_review` в этой версии не переводит процесс в отдельный режим, а считается сигналом к recovery/fail policy.

Если structured output не проходит проверку, это считается ошибкой разбора текущего шага, а не успехом.

### 6.3. Нормальный шаг диалога

Если ответ пользователя успешно распознан, `Workflow B` делает следующее:

1. Нормализует значение текущего поля в целое число.
2. Сохраняет значение в `answers[current_field]`.
3. Обновляет `notes`, `confidence`, `updated_at`.
4. Удаляет поле из `missing_fields`.
5. Если есть следующее незаполненное поле, переводит сессию в статус `awaiting_<next_field>`.
6. Формирует и отправляет следующий вопрос.
7. Сохраняет новый `last_question`.

Если после обновления `missing_fields` становится пустым:

1. Сессия переводится в `review`.
2. Пользователю отправляется детерминированный summary по всем собранным полям.
3. Summary всегда показывает поля в canonical order и нумерует их.
4. Пользователю предлагается ответить:
   `да` для отправки результата или номером поля для исправления.

### 6.4. Review policy

В состоянии `review` допускаются только такие сценарии:

- `да`, `подтверждаю`, `верно`, `отправить` -> переход в `submitting`;
- номер поля из summary -> возврат к соответствующему `awaiting_<field>`;
- любой другой ответ -> детерминированное сообщение:
  `Ответьте "да", чтобы отправить данные, или номером поля, которое нужно исправить.`

Если пользователь выбрал исправление поля:

1. соответствующее значение в `answers[field]` сбрасывается в `null`;
2. поле возвращается в `missing_fields`;
3. `clarification_count[field]` сбрасывается в `0`;
4. `recovery_attempted[field]` сбрасывается в `false`;
5. workflow задает вопрос только по выбранному полю.

### 6.5. Диалоговая деградация

Для каждого поля действует такой порядок:

1. Пока `clarification_count[field] < instructions.max_clarifying_questions_per_field`, workflow задает обычный уточняющий вопрос.
2. После исчерпания лимита допускается ровно одна recovery-попытка с жесткой формулировкой:
   `Пришлите только число в рублях, без текста и символов.`
3. Если recovery-попытка уже использована и надежное значение снова не получено, сессия переводится в локальный `failed`.

При локальном `failed` workflow обязан:

- сохранить `last_error`;
- удалить active-chat index;
- уведомить пользователя, что данные сейчас не удалось сохранить и нужно начать опрос заново позже;
- создать технический сигнал для разбора;
- не отправлять partial payload на сервер.

### 6.6. Команды пользователя

Workflow обязан поддерживать команды:

- `отмена` -> переход в `cancelled`, удаление active-chat index, подтверждение отмены пользователю;
- `начать заново` -> сброс всех `answers`, `missing_fields`, `clarification_count`, `recovery_attempted`, `callback_attempts`, `last_error`, `last_server_response`, `collected_at`, `reminder_sent_at`, возврат к первому полю того же `request_id`;
- `помощь` -> короткая подсказка по текущему шагу без изменения статуса.

## 7. Submit и callback на сервер

### 7.1. Когда разрешен submit

Отправка на сервер разрешена только если одновременно выполнены условия:

- session state находится в `review`;
- пользователь дал явное подтверждение;
- все поля из `requested_fields` заполнены;
- session state не содержит активной recovery-ошибки.

### 7.2. Формирование payload

Payload для `POST /api/v1/intake/user-finance-data` собирается только deterministic-веткой из session state.

Обязательные правила:

- `request_id` возвращается без изменений;
- `external_submission_id` берется из session state и не меняется при retry;
- `source = telegram`;
- `collected_at` берется на момент первого перехода в `submitting`;
- `meta.telegram_chat_id` берется из session state;
- `meta.confidence` вычисляется по последнему валидному результату LLM;
- `meta.notes` собирается из заметок о приблизительных ответах и recovery-событиях.

### 7.3. Retry policy callback

Для callback фиксируется такая политика:

- попытка `1` выполняется сразу;
- попытка `2` через `1 минуту` с jitter `±20%`;
- попытка `3` через `5 минут` с jitter `±20%`;
- попытка `4` через `30 минут` с jitter `±20%`;
- попытка `5` через `2 часа` с jitter `±20%`;
- после пятой неуспешной попытки сессия переводится в локальный `failed`.

Результаты callback трактуются так:

- `202 Accepted` -> терминальный успех, переход в `completed`;
- `409 Conflict` с дедупликацией `external_submission_id` -> терминальный успех, переход в `completed`;
- сетевые ошибки и `5xx` -> retriable, увеличить `callback_attempts`, сохранить `last_error`, оставить статус `submitting`;
- `401 Unauthorized` -> non-retriable, перейти в локальный `failed`;
- `422 Unprocessable Entity` -> non-retriable, перейти в локальный `failed`;
- любой другой `4xx` -> non-retriable, перейти в локальный `failed`.

### 7.4. Завершение submit

При `completed` workflow обязан:

- сохранить `last_server_response`;
- удалить active-chat index;
- отправить пользователю подтверждение, что данные успешно приняты;
- сохранить session state до конца TTL для диагностики.

При финальном `failed` после callback workflow обязан:

- сохранить `last_error`, `last_server_response`, `callback_attempts`;
- удалить active-chat index;
- уведомить пользователя о технической неудаче;
- создать технический сигнал для разбора минимум в execution log `n8n` с `request_id`, `telegram_chat_id` и текстом ошибки;
- не отправлять новый payload с другим `external_submission_id`.

## 8. Business session state и Redis keyspace

### 8.1. Session state

Business session state хранится в Redis по ключу:

- `finance:intake:session:{request_id}`

TTL для session state:

- `7 дней`

Рекомендуемая структура:

```json
{
  "request_id": "99999999-9999-9999-9999-999999999999",
  "workflow_run_id": "n8n-run-001",
  "external_submission_id": "n8n-run-2026-03-15-001",
  "telegram_chat_id": "123456789",
  "family_id": "11111111-1111-1111-1111-111111111111",
  "member_id": "22222222-2222-2222-2222-222222222222",
  "status": "awaiting_income",
  "current_field": "monthly_income",
  "requested_fields": [
    "monthly_income",
    "monthly_expenses",
    "monthly_credit_payments",
    "liquid_savings"
  ],
  "answers": {
    "monthly_income": null,
    "monthly_expenses": null,
    "monthly_credit_payments": null,
    "liquid_savings": null
  },
  "missing_fields": [
    "monthly_income",
    "monthly_expenses",
    "monthly_credit_payments",
    "liquid_savings"
  ],
  "clarification_count": {
    "monthly_income": 0,
    "monthly_expenses": 0,
    "monthly_credit_payments": 0,
    "liquid_savings": 0
  },
  "recovery_attempted": {
    "monthly_income": false,
    "monthly_expenses": false,
    "monthly_credit_payments": false,
    "liquid_savings": false
  },
  "callback_attempts": 0,
  "last_question": "Сколько вы получили по текущей выплате?",
  "last_error": null,
  "last_server_response": null,
  "reminder_sent_at": null,
  "confidence": "medium",
  "notes": [],
  "started_at": "2026-03-15T09:00:00+03:00",
  "updated_at": "2026-03-15T09:05:00+03:00",
  "collected_at": null
}
```

### 8.2. Redis keyspace

Кроме session state должны использоваться такие ключи:

- `finance:intake:chat:{telegram_chat_id}` -> активный `request_id` для чата, TTL `7 дней`, удаляется сразу при терминальном статусе;
- `finance:intake:lock:request:{request_id}` -> lock обработки конкретной сессии, `SET NX EX 180`;
- `finance:intake:lock:chat:{telegram_chat_id}` -> lock старта новой сессии по чату, `SET NX EX 180`;
- `finance:intake:dedupe:telegram-update:{update_id}` -> защита от повторной обработки одного Telegram update, TTL `7 дней`.

### 8.3. Правила обновления keyspace

При каждом успешном пользовательском шаге workflow обязан:

- обновлять session state целиком;
- продлевать TTL session state;
- продлевать TTL active-chat index;
- освобождать request lock в конце обработки.

Если найден stale active-chat index, workflow обязан:

- удалить stale-index;
- не считать это активной сессией;
- продолжить обработку по обычному правилу.

## 9. Retry и failure matrix

| Событие | Действие workflow | Итоговый статус |
|---|---|---|
| Невалидный стартовый webhook | Вернуть `401` или `422`, не создавать активную сессию | Нет сессии |
| Duplicate `request_id` | Вернуть `409`, не отправлять второе стартовое сообщение | Старая сессия продолжает жить |
| Активный конфликт по `telegram_chat_id` | Вернуть `409`, не запускать новый диалог | Старая сессия продолжает жить |
| Сбой Telegram до точки accepted | Удалить session state и chat-index, вернуть `5xx` | Нет сессии |
| Duplicate `telegram update_id` | Завершить без побочных эффектов | Текущая сессия без изменений |
| Нет активной сессии по чату | Вежливо ответить пользователю | Нет активной сессии |
| Некорректный structured output от LLM | Считать ошибкой текущего шага, уйти в clarification или recovery | Сессия активна |
| Не удалось извлечь число после clarification и recovery | Локальный fail, без callback на сервер | `failed` |
| Callback получил `202` | Завершить успешно | `completed` |
| Callback получил `409` по дедупликации | Считать терминальным успехом | `completed` |
| Callback получил сеть/`5xx` | Выполнить retry с тем же `external_submission_id` | `submitting` |
| Callback получил `401` или `422` | Не ретраить, сохранить ошибку, уведомить пользователя | `failed` |
| Истек TTL сессии | Закрыть сессию без partial submit | `failed` |

## 10. Timeout, reminder, cancel policy

Фиксируется такая политика:

- TTL активной сессии составляет `7 дней` с момента последней активности;
- если пользователь молчит `24 часа`, отправляется один soft-reminder;
- reminder отправляется не более одного раза на одну сессию;
- если после reminder пользователь отвечает, сессия продолжается в обычном режиме;
- если TTL истек, сессия переводится в `failed`, active-chat index удаляется, partial payload не отправляется;
- если пользователь прислал `отмена`, сессия переходит в `cancelled` и сразу освобождает чат;
- если пользователь прислал сообщение после `failed` или `cancelled`, workflow отвечает, что активный опрос не найден.

## 11. Тестовые сценарии

Минимальный набор сценариев для проверки алгоритма:

- valid webhook -> создание session state -> успешная отправка первого Telegram-сообщения -> `202 Accepted`;
- retry webhook с тем же `request_id` не создает вторую сессию и не отправляет второе стартовое сообщение;
- новый запуск при активном `telegram_chat_id` получает `409 Conflict`;
- duplicate `telegram update_id` не приводит к двойному вопросу и двойному callback;
- happy path по всем requested fields заканчивается `review`, затем `completed`;
- approximate answer по полю сохраняется с пониженным `confidence`;
- некорректный формат суммы приводит к clarification, затем к recovery prompt;
- после исчерпания clarification и recovery происходит локальный `failed`;
- `отмена` переводит сессию в `cancelled` и освобождает chat-index;
- `начать заново` сбрасывает собранные значения в пределах того же `request_id`;
- `202 Accepted` от intake callback завершает сессию в `completed`;
- `409 DUPLICATE_SUBMISSION` от intake callback трактуется как успешная дедупликация;
- сетевые ошибки callback запускают retry с тем же `external_submission_id`;
- `422 VALIDATION_ERROR` от сервера завершает сессию в `failed`;
- session TTL expire завершает сессию без partial submit и без "зомби-состояния".

## 12. Future improvement

Вне текущей версии остаются такие улучшения:

- отдельный failure callback в серверное API;
- отдельный manual review workflow;
- явный операторский takeover;
- multi-channel routing помимо Telegram;
- хранение operational alerting в отдельной БД или monitoring-системе.
