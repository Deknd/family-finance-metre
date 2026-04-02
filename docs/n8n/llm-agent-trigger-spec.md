# Спецификация запуска двухуровневого n8n intake-агента из сервера

## 1. Назначение

Этот документ описывает контракт, по которому сервер запускает intake-агента в `n8n`.

Задача этого контракта:

- сообщить, кого нужно опросить;
- передать контекст семьи и пользователя;
- передать причину запуска;
- запустить orchestration workflow в `n8n`;
- инициировать Telegram-сессию сбора данных.

Этот документ не описывает обратную отправку результата на сервер. Она описана в
[api-spec.md](../server/api-spec.md).

Подробная внутренняя архитектура `n8n` описана в
[telegram-finance-intake-agent-spec.md](./telegram-finance-intake-agent-spec.md).

## 2. Когда сервер вызывает LLM-агента

Базовый сценарий MVP:

- сервер знает даты зарплаты членов семьи;
- на следующий день после зарплаты сервер инициирует опрос;
- сервер передает в `n8n`, какого пользователя нужно опросить;
- `n8n` запускает orchestration workflow;
- orchestration workflow инициализирует Telegram intake-сессию;
- conversational workflow в Telegram собирает данные у пользователя;
- после завершения опроса `n8n` отправляет результат обратно на сервер.

## 3. Формат интеграции

Для MVP удобнее всего использовать webhook в `n8n`.

Рекомендуемый контракт:

- сервер вызывает HTTP endpoint `n8n`;
- orchestration workflow в `n8n` отвечает, что задача принята;
- дальше работа идет асинхронно.

## 4. Endpoint webhook со стороны n8n

`POST /webhook/finance-intake-start`

Это логическое имя endpoint'а. Конкретный URL будет зависеть от конфигурации `n8n`.

Пример:

- `https://n8n.example.com/webhook/finance-intake-start`

## 5. Назначение endpoint'а

Endpoint запускает `Workflow A / Intake Orchestrator Agent`.

`n8n` после получения запроса должно:

- принять задачу;
- создать run orchestration workflow;
- провалидировать стартовый payload;
- создать или переиспользовать intake-сессию;
- при необходимости отправить первое сообщение пользователю в Telegram;
- после завершения отправить собранные данные обратно на сервер.

## 6. Аутентификация

Для MVP достаточно одного из вариантов:

- `X-N8N-Webhook-Token`
- `Authorization: Bearer <token>`

Рекомендуется использовать `Authorization: Bearer`.

Для MVP под `Bearer` здесь понимается не OAuth2 access token и не JWT,
выданный внешним identity provider. Это заранее согласованный общий секрет
между сервером и `n8n`, который обе стороны хранят в своей конфигурации.

В первой версии не требуется отдельный issuer, introspection endpoint,
валидация подписи JWT или Keycloak в цепочке. `n8n` достаточно проверять
точное совпадение значения заголовка `Authorization`.

Если позже появится требование к централизованной выдаче токенов, сроку жизни,
ролям или federated SSO, эту схему можно заменить на полноценный OAuth2/OIDC.

## 7. Тело запроса от сервера в n8n

```json
{
  "request_id": "99999999-9999-9999-9999-999999999999",
  "triggered_at": "2026-03-15T09:00:00+03:00",
  "reason": "day_after_salary",
  "family": {
    "id": "11111111-1111-1111-1111-111111111111",
    "name": "Ivanov family"
  },
  "member": {
    "id": "22222222-2222-2222-2222-222222222222",
    "name": "Anna",
    "telegram_chat_id": "123456789"
  },
  "payroll_event": {
    "schedule_id": "33333333-3333-3333-3333-333333333333",
    "schedule_type": "last_day_of_month",
    "day_of_month": null,
    "nominal_payroll_date": "2026-03-31",
    "effective_payroll_date": "2026-03-31",
    "trigger_delay_days": 1,
    "scheduled_trigger_date": "2026-04-01"
  },
  "collection_scope": {
    "period_year": 2026,
    "period_month": 3,
    "fields": [
      "monthly_income",
      "monthly_expenses",
      "monthly_credit_payments",
      "liquid_savings"
    ]
  },
  "instructions": {
    "locale": "ru-RU",
    "currency": "RUB",
    "allow_approximate_values": true,
    "max_clarifying_questions_per_field": 1
  },
  "callback": {
    "submit_url": "https://server.example.com/api/v1/intake/user-finance-data"
  }
}
```

## 8. Поля запроса

- `request_id` - UUID-строка, которую сервер генерирует для payroll-triggered запуска и использует как основной correlation id цепочки;
- в callback обратно на сервер `n8n` должно вернуть это значение без изменений и без собственной нормализации;
- `triggered_at` - время запуска
- `reason` - причина запуска, например `day_after_salary`
- `family.id` - идентификатор семьи в формате UUID
- `family.name` - необязательное имя семьи
- `member.id` - идентификатор пользователя в формате UUID
- `member.name` - имя пользователя
- `member.telegram_chat_id` - Telegram chat id для начала диалога
- `payroll_event.schedule_id` - идентификатор правила выплаты
- `payroll_event.schedule_type` - тип правила выплаты
- `payroll_event.day_of_month` - день месяца для фиксированной выплаты, если применимо
- `payroll_event.nominal_payroll_date` - исходная дата выплаты без переноса
- `payroll_event.effective_payroll_date` - фактическая дата выплаты после переноса
- `payroll_event.trigger_delay_days` - через сколько дней после выплаты запускать опрос
- `payroll_event.scheduled_trigger_date` - дата, в которую сервер инициировал запуск
- `collection_scope.period_year` - год собираемого периода
- `collection_scope.period_month` - месяц собираемого периода
- `collection_scope.fields` - список полей, которые нужно собрать
- `instructions.locale` - локаль диалога
- `instructions.currency` - валюта
- `instructions.allow_approximate_values` - можно ли принимать приблизительные ответы
- `instructions.max_clarifying_questions_per_field` - лимит уточняющих вопросов
- `callback.submit_url` - URL сервера, куда `n8n` отправит результат

## 8.1. Семантика собираемых полей

- `monthly_income` должен описывать только сумму текущей выплаты, по которой был запущен опрос;
- `monthly_expenses` должен описывать актуальную оценку всех расходов пользователя за целевой месяц на момент опроса;
- `monthly_credit_payments` должен описывать актуальную оценку всех кредитных платежей пользователя за целевой месяц на момент опроса;
- `liquid_savings` должен описывать доступные накопления пользователя на момент опроса;
- если у пользователя несколько выплат в одном месяце, сервер позже суммирует все значения `monthly_income` за период, а остальные поля берет из самой свежей intake submission.

## 9. Ответ n8n на запуск

### Ответ `202 Accepted`

```json
{
  "status": "accepted",
  "request_id": "99999999-9999-9999-9999-999999999999",
  "workflow_run_id": "n8n-run-001"
}
```

Этот ответ должен формироваться orchestration workflow через `Respond to Webhook` и возвращаться сразу после успешной инициализации intake-сессии.

С точки зрения сервера запуск считается успешным только если одновременно выполнены все условия:

- `HTTP 202 Accepted`;
- `status = accepted`;
- `request_id` в ответе совпадает с отправленным значением;
- `workflow_run_id` присутствует и не пустой.

### Ответ `401 Unauthorized`

```json
{
  "error": {
    "code": "INVALID_TOKEN",
    "message": "Authorization shared secret is invalid"
  }
}
```

### Ответ `409 Conflict`

Используется если workflow с таким `request_id` уже запущен.

```json
{
  "error": {
    "code": "DUPLICATE_REQUEST",
    "message": "Workflow for this request_id already exists"
  }
}
```

## 10. Поведение n8n после принятия запроса

После успешного ответа `202 Accepted` ожидается, что `n8n`:

- orchestration workflow завершает HTTP-ответ серверу и не держит дальнейший диалог внутри этого же запроса;
- orchestration workflow создает или обновляет intake-сессию с сохранением `request_id` и `workflow_run_id`;
- conversational workflow общается с пользователем через Telegram;
- conversational workflow собирает значения по запрошенным полям;
- conversational workflow нормализует числа;
- conversational workflow при необходимости задает ограниченное число уточнений;
- conversational workflow отправляет итоговые данные на сервер;
- весь процесс использует `request_id` для трассировки цепочки `server -> n8n -> Telegram -> server`;
- если `n8n` возвращает другой `request_id` или не возвращает `workflow_run_id`,
  сервер считает запуск неуспешным и переводит `llm_collection_request` в `failed`.

Для MVP основной паттерн диалога фиксируется как:

- `Telegram Trigger + AI session state`

`Human review` или `send-and-wait` допускается только как вспомогательный механизм подтверждения, эскалации или ручного takeover.

## 11. Минимальный состав для MVP

Для первой версии достаточно, чтобы сервер передавал:

- кого опросить;
- куда писать в Telegram;
- по какому зарплатному событию запускается сбор;
- за какой месяц собирать данные;
- какие 4 поля нужно собрать;
- куда отправить результат обратно.

Этого уже достаточно для рабочей интеграции сервер -> `n8n` -> Telegram -> сервер.
