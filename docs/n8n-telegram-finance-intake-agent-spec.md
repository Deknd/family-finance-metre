# Спецификация двухуровневого n8n AI intake-агента

## 1. Назначение

Этот документ фиксирует целевую архитектуру `n8n`-решения для сбора финансовых данных у пользователя через Telegram.

Документ описывает:

- двухуровневую схему из 2 workflow;
- роли AI-оркестратора и AI-собеседника;
- рекомендованные узлы `n8n`;
- модель хранения состояния диалога;
- правила формирования итогового JSON для callback на сервер;
- сценарии подтверждения, эскалации и отмены.

Документ не заменяет серверные API-контракты. Для внешних HTTP-интерфейсов нужно использовать:

- [llm-agent-trigger-spec.md](/D:/IdeaProjects/family-finance-metre/docs/llm-agent-trigger-spec.md)
- [server-api-spec.md](/D:/IdeaProjects/family-finance-metre/docs/server-api-spec.md)

Пошаговый план реализации описан в
[n8n-telegram-finance-intake-implementation-plan.md](/D:/IdeaProjects/family-finance-metre/docs/n8n-telegram-finance-intake-implementation-plan.md).

## 2. Границы MVP

Для первой версии фиксируются такие ограничения:

- канал общения только `Telegram`;
- диалог текстовый, без голосовых сообщений, файлов и фотографий;
- сервер инициирует intake через webhook;
- пользователь отвечает в произвольной текстовой форме;
- `n8n` само ведет диалог, нормализует ответы и отправляет итоговый payload обратно на сервер;
- community/HITL-ноды не входят в базовую архитектуру.

Важно:

- MCP-сервер `n8n` в этой сессии ориентирован на `latest` версию `n8n`;
- перед реализацией нужно сверить фактическую версию инстанса `n8n` с поддерживаемой версией и при расхождении перепроверить свойства узлов.

## 3. Общая архитектура

### 3.1. Двухуровневая схема

Рекомендуемая архитектура состоит из двух независимых workflow.

### Workflow A. `Intake Orchestrator Agent`

Назначение:

- принять стартовый webhook от сервера;
- провалидировать входной payload;
- принять решение, можно ли запускать Telegram intake;
- создать или переиспользовать активную intake-сессию;
- инициировать старт диалога;
- сразу вернуть серверу `202 Accepted`.

Этот workflow не держит живой разговор между сообщениями пользователя.

### Workflow B. `Telegram Conversation Agent`

Назначение:

- реагировать на каждое новое входящее сообщение от пользователя в Telegram;
- загружать текущее состояние intake-сессии;
- решать, какой вопрос задать следующим;
- извлекать и нормализовать значения из свободного текста;
- показывать пользователю summary;
- отправлять финальный JSON на сервер.

Именно этот workflow реализует основной живой диалог.

### 3.2. Почему не `send-and-wait` как основной паттерн

Для MVP базовый паттерн должен быть `Telegram Trigger + AI session state`, а не `send-and-wait`.

Причины:

- Telegram-диалог состоит из нескольких независимых пользовательских сообщений;
- каждое сообщение приходит как новое событие;
- между сообщениями нужно хранить устойчивое состояние процесса;
- AI-агент должен на каждом шаге понимать, что уже собрано и что еще нужно спросить.

Паттерн `Human review` или `send-and-wait` допускается только как дополнительный механизм:

- финальное подтверждение перед отправкой;
- ручной takeover;
- эскалация, если AI не смог надежно извлечь сумму;
- пауза с последующим ручным продолжением.

Но он не должен быть основой полного многошагового Telegram-опроса.

## 4. Рекомендуемые узлы n8n

Ниже зафиксирован рекомендуемый базовый стек узлов по данным MCP.

- `Webhook` `2.1`
- `Respond to Webhook` `1.5`
- `Telegram Trigger` `1.2`
- `Telegram` `1.2`
- `AI Agent` `3.1`
- `Call n8n Sub-Workflow Tool` `2.2`
- `Execute Workflow Trigger` `1.1`
- `Structured Output Parser` `1.3`
- `HTTP Request` `4.4`
- `Redis` `1`
- `Redis Chat Memory` `1.5`

Допустимо использовать также:

- `Set` для явной сборки промежуточных JSON;
- `IF` или `Switch` для ветвления статусов;
- `Execute Sub-workflow` для явного вызова подworkflow без tool-варианта.

## 5. Workflow A. Intake Orchestrator Agent

### 5.1. Trigger и ответ серверу

`Workflow A` должен начинаться с `Webhook`.

Рекомендуемый endpoint:

- `POST /webhook/finance-intake-start`

После базовой валидации workflow должен как можно раньше завершать HTTP-взаимодействие через `Respond to Webhook`.

Рекомендуемый ответ:

```json
{
  "status": "accepted",
  "request_id": "99999999-9999-9999-9999-999999999999",
  "workflow_run_id": "n8n-run-001"
}
```

HTTP-статус:

- `202 Accepted`

### 5.2. Ответственность AI-оркестратора

AI-оркестратор внутри `Workflow A` отвечает за:

- валидацию структуры стартового payload;
- проверку, что сценарий подходит для Telegram intake;
- выбор действия: создать новую сессию, переиспользовать текущую активную или отклонить запуск;
- запуск подзадач через `Call n8n Sub-Workflow Tool` или `Execute Sub-workflow`;
- инициализацию состояния сессии;
- стартовое сообщение пользователю;
- retry, cancel и escalation policy на уровне orchestration.

AI-оркестратор не должен:

- вести длительный диалог с пользователем;
- ожидать все следующие реплики пользователя внутри одного запуска;
- собирать финальный intake payload между событиями Telegram.

### 5.3. Входные данные

`Workflow A` принимает payload, описанный в [llm-agent-trigger-spec.md](/D:/IdeaProjects/family-finance-metre/docs/llm-agent-trigger-spec.md).

Критично сохранить в orchestration state:

- `request_id`
- `workflow_run_id`
- `triggered_at`
- `reason`
- `family.id`
- `member.id`
- `member.telegram_chat_id`
- `collection_scope.period_year`
- `collection_scope.period_month`
- `collection_scope.fields`
- `instructions`
- `callback.submit_url`

### 5.4. Поведение при дублях

Если активная сессия для этого `request_id` уже существует, `Workflow A` должен:

- не создавать вторую независимую сессию;
- вернуть `409 Conflict` либо мягко переиспользовать текущую активную сессию в зависимости от выбранной policy;
- использовать один и тот же `request_id` как главный correlation id.

Для MVP рекомендуется policy:

- если workflow уже запущен и не завершен, вернуть `409 Conflict`.

## 6. Workflow B. Telegram Conversation Agent

### 6.1. Trigger

`Workflow B` должен начинаться с `Telegram Trigger`.

На каждое новое сообщение пользователя workflow должен:

- извлечь `telegram_chat_id`;
- загрузить соответствующую intake-сессию;
- определить текущее состояние;
- передать текущий контекст в conversational AI-агента;
- получить структурированный ответ агента;
- обновить session state;
- отправить пользователю следующее сообщение либо выполнить submit.

### 6.2. Ответственность conversational AI-агента

Conversational AI-агент отвечает за:

- ведение живого диалога;
- интерпретацию свободных текстовых ответов пользователя;
- извлечение значений для финансовых полей;
- решение, нужен ли уточняющий вопрос;
- переход между этапами state machine;
- формирование финального summary;
- решение о готовности к submit;
- эскалацию в ручной режим при высокой неоднозначности.

### 6.3. Правила общения

Для MVP агент должен следовать таким правилам:

- задавать только один основной вопрос за раз;
- не собирать сразу несколько полей в одном сообщении, если это можно избежать;
- принимать приблизительные значения, если `allow_approximate_values = true`;
- задавать не более одного уточняющего вопроса на поле;
- использовать понятный русский язык;
- поддерживать команды `отмена`, `начать заново`, `помощь`;
- перед submit показывать summary и просить явное подтверждение;
- при невозможности надежно извлечь значение переводить сессию в `needs_human_review`.

## 7. Слои состояния

В документации обязательно фиксируются 3 разных слоя состояния.

### 7.1. Business session state

Хранится в Redis и описывает состояние intake-процесса.

Рекомендуемый ключ:

- `finance:intake:session:{request_id}`

Рекомендуемый TTL:

- `7 дней`

Пример структуры:

```json
{
  "request_id": "99999999-9999-9999-9999-999999999999",
  "workflow_run_id": "n8n-run-001",
  "telegram_chat_id": "123456789",
  "family_id": "11111111-1111-1111-1111-111111111111",
  "member_id": "22222222-2222-2222-2222-222222222222",
  "period": {
    "year": 2026,
    "month": 3
  },
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
  "status": "awaiting_income",
  "last_question": "Сколько вы получили по текущей выплате?",
  "clarification_count": {
    "monthly_income": 0,
    "monthly_expenses": 0,
    "monthly_credit_payments": 0,
    "liquid_savings": 0
  },
  "confidence": "medium",
  "notes": [],
  "external_submission_id": "n8n-run-2026-03-15-001",
  "created_at": "2026-03-15T09:00:00+03:00",
  "updated_at": "2026-03-15T09:05:00+03:00"
}
```

### 7.2. Chat memory

`Redis Chat Memory` хранит только conversational context:

- недавние реплики пользователя;
- ответы агента;
- краткий рабочий контекст разговора.

`Chat memory` не должна быть единственным источником бизнес-состояния. Даже при наличии полной истории чата система должна опираться на отдельный `business session state`.

### 7.3. Final normalized payload

После завершения диалога должен собираться отдельный нормализованный JSON-объект, который отправляется на сервер.

Этот объект не должен напрямую зависеть от формулировок пользователя; он должен собираться из нормализованных полей session state.

## 8. State machine разговора

Для MVP фиксируется следующая state machine:

- `accepted`
- `awaiting_income`
- `awaiting_expenses`
- `awaiting_credit_payments`
- `awaiting_savings`
- `review`
- `submitting`
- `completed`
- `failed`
- `cancelled`
- `needs_human_review`

Правила переходов:

- после создания сессии `accepted -> awaiting_income`;
- после успешного извлечения значения для поля переход к следующему обязательному полю;
- если все обязательные поля собраны `-> review`;
- после подтверждения пользователем `review -> submitting`;
- после успешного callback `submitting -> completed`;
- при явной отмене пользователя `-> cancelled`;
- при таймауте, ошибке или потере контекста `-> failed`;
- при высокой неоднозначности `-> needs_human_review`.

## 9. Structured output AI-агента

AI-агент не должен возвращать свободный текст без структуры. Для него нужно использовать `Structured Output Parser`.

Минимальный контракт внутреннего ответа:

```json
{
  "reply_text": "Приняла. Теперь подскажите, сколько составляют все ваши расходы за март?",
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
    "Пользователь сообщил доход приблизительно"
  ],
  "needs_human_review": false,
  "should_submit": false
}
```

Обязательные поля structured output:

- `reply_text`
- `field_updates`
- `missing_fields`
- `next_state`
- `is_complete`
- `confidence`
- `notes`
- `needs_human_review`
- `should_submit`

Рекомендуемая схема:

- `field_updates` содержит только успешно извлеченные и нормализованные значения;
- `missing_fields` содержит еще не собранные поля;
- `is_complete = true`, если собраны все обязательные поля;
- `should_submit = true` только после финального подтверждения пользователем;
- `needs_human_review = true`, если значение нельзя извлечь надежно даже после уточнения.

## 10. Семантика собираемых полей

Семантика полей должна полностью совпадать с серверной логикой.

- `monthly_income` - сумма именно по текущей выплате или текущему payroll-событию;
- `monthly_expenses` - актуальная оценка всех расходов пользователя за целевой месяц на момент ответа;
- `monthly_credit_payments` - актуальная оценка всех кредитных платежей пользователя за целевой месяц на момент ответа;
- `liquid_savings` - доступные накопления пользователя на момент ответа.

Если пользователь говорит приблизительно, это допустимо, но:

- значение все равно нормализуется в целое число;
- в `meta.notes` должно попадать пояснение о приблизительной оценке;
- `meta.confidence` не должен быть `high`, если агент опирался на приблизительный ответ.

## 11. Mapping итогового callback на сервер

Итоговый callback должен соответствовать [server-api-spec.md](/D:/IdeaProjects/family-finance-metre/docs/server-api-spec.md) и фактическому DTO `UserFinanceIntakeRequest`.

### 11.1. Ограничения формата

- `family_id` - только UUID-строка;
- `member_id` - только UUID-строка;
- `source` - строго `telegram`;
- `collected_at` - ISO-8601 с offset;
- `period.month` - `1..12`;
- финансовые значения - целые числа `>= 0`;
- `meta.confidence` - `low`, `medium` или `high`.

### 11.2. Политика для `request_id`

Для payroll-triggered intake в этом документе считается обязательным, что `request_id` передается обратно в callback.

Причина:

- именно `request_id` связывает callback с `llm_collection_request`;
- сервер создает `request_id` как UUID-строку и ожидает, что `n8n` вернет его без изменений;
- если `request_id` передан, но не существует, сервер возвращает `422 VALIDATION_ERROR`.

### 11.3. Правила заполнения meta

- `meta.telegram_chat_id` заполняется из session state;
- `meta.confidence` определяется по результату работы AI-агента;
- `meta.notes` собирается из заметок агента и признаков approximate input.

### 11.4. Идемпотентность callback

`external_submission_id` должен:

- генерироваться один раз на сессию intake;
- сохраняться в session state;
- переиспользоваться при retry того же callback;
- не меняться при повторной отправке после сетевой ошибки.

Если сервер отвечает `409 Conflict` из-за уже обработанного `external_submission_id`, это считается корректным сигналом дедупликации.

### 11.5. Пример финального payload

```json
{
  "external_submission_id": "n8n-run-2026-03-15-001",
  "request_id": "99999999-9999-9999-9999-999999999999",
  "family_id": "11111111-1111-1111-1111-111111111111",
  "member_id": "22222222-2222-2222-2222-222222222222",
  "source": "telegram",
  "collected_at": "2026-03-15T08:40:00+03:00",
  "period": {
    "year": 2026,
    "month": 3
  },
  "finance_input": {
    "monthly_income": 120000,
    "monthly_expenses": 50000,
    "monthly_credit_payments": 18000,
    "liquid_savings": 150000
  },
  "meta": {
    "telegram_chat_id": "123456789",
    "confidence": "medium",
    "notes": "User provided approximate values"
  }
}
```

## 12. Optional Human Review / Send-and-Wait

Эта механика остается допустимой, но только как вспомогательная.

Разрешенные сценарии:

- финальное подтверждение собранных данных перед отправкой;
- ручной takeover оператора;
- fallback при неоднозначном ответе пользователя;
- ручное продолжение после паузы.

Нежелательный сценарий:

- строить весь основной intake-опрос как линейный `send question -> wait -> next question` без отдельного session state и без `Telegram Trigger`.

## 13. Ошибки и отказоустойчивость

### 13.1. Ошибки orchestration workflow

Если стартовый payload невалиден, `Workflow A` не должен создавать intake-сессию.

Если запуск Telegram intake невозможен, допустимы варианты:

- вернуть `422`;
- вернуть `409`, если конфликт связан с дублем активного процесса;
- перевести задачу во внутренний статус `failed`.

### 13.2. Ошибки conversational workflow

Если `Workflow B` не находит активную сессию по `telegram_chat_id`, рекомендуется:

- не отправлять callback на сервер;
- ответить пользователю сообщением о том, что активный опрос не найден;
- предложить дождаться нового приглашения или обратиться к оператору.

### 13.3. Таймауты

Если пользователь не отвечает слишком долго, рекомендуется:

- завершать сессию по TTL;
- переводить статус в `failed` или `cancelled`;
- не отправлять частичный payload на сервер без явной policy.

## 14. Тестовые сценарии для документации

Ниже фиксируется минимальный набор сценариев, который должна покрывать реализация и проверка документации.

- старт intake через `Workflow A` и ответ `202 Accepted`;
- успешная инициализация Telegram session;
- многошаговый диалог через `Workflow B` до сбора всех 4 полей;
- ответ пользователя с приблизительными значениями и `confidence = medium`;
- финальное подтверждение перед submit;
- успешный callback на сервер;
- duplicate callback по `external_submission_id`;
- callback с несуществующим `request_id` и ответом `422 VALIDATION_ERROR`;
- ручная отмена пользователем;
- escalation в human review;
- abandoned session по TTL или timeout.

## 15. Критерии согласованности с сервером

Документация считается согласованной с текущей серверной реализацией, если:

- все примеры callback JSON соответствуют `UserFinanceIntakeRequest`;
- в примерах используются UUID для `family_id` и `member_id`;
- `request_id` в примерах и правилах описан как UUID-строка, возвращаемая без изменений;
- `source` всегда равен `telegram`;
- поведение `request_id` описано как строгая валидация при его наличии;
- примеры ошибок и идемпотентности не противоречат текущим controller/service тестам.
