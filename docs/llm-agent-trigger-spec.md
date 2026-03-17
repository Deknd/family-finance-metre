# Спецификация запуска LLM-агента из сервера

## 1. Назначение

Этот документ описывает контракт, по которому сервер запускает LLM-агента в `n8n`.

Задача этого контракта:

- сообщить, кого нужно опросить;
- передать контекст семьи и пользователя;
- передать причину запуска;
- запустить workflow сбора данных через Telegram.

Этот документ не описывает обратную отправку результата на сервер. Она описана в [server-api-spec.md](/D:/IdeaProjects/finance-metr/docs/server-api-spec.md).

## 2. Когда сервер вызывает LLM-агента

Базовый сценарий MVP:

- сервер знает даты зарплаты членов семьи;
- на следующий день после зарплаты сервер инициирует опрос;
- сервер передает в `n8n`, какого пользователя нужно опросить;
- `n8n` запускает LLM-агента, который собирает данные в Telegram;
- после завершения опроса `n8n` отправляет результат обратно на сервер.

## 3. Формат интеграции

Для MVP удобнее всего использовать webhook в `n8n`.

Рекомендуемый контракт:

- сервер вызывает HTTP endpoint `n8n`;
- `n8n` отвечает, что задача принята;
- дальше работа идет асинхронно.

## 4. Endpoint webhook со стороны n8n

`POST /webhook/finance-intake-start`

Это логическое имя endpoint'а. Конкретный URL будет зависеть от конфигурации `n8n`.

Пример:

- `https://n8n.example.com/webhook/finance-intake-start`

## 5. Назначение endpoint'а

Endpoint запускает workflow сбора финансовых данных у конкретного члена семьи.

`n8n` после получения запроса должно:

- принять задачу;
- создать run workflow;
- начать общение с пользователем в Telegram;
- после завершения отправить собранные данные обратно на сервер.

## 6. Аутентификация

Для MVP достаточно одного из вариантов:

- `X-N8N-Webhook-Token`
- `Authorization: Bearer <token>`

Рекомендуется использовать `Authorization: Bearer`.

## 7. Тело запроса от сервера в n8n

```json
{
  "request_id": "req_2026_03_15_member_anna",
  "triggered_at": "2026-03-15T09:00:00+03:00",
  "reason": "day_after_salary",
  "family": {
    "id": "family_01",
    "name": "Ivanov family"
  },
  "member": {
    "id": "member_anna",
    "name": "Anna",
    "telegram_chat_id": "123456789"
  },
  "payroll_event": {
    "schedule_id": "sched_anna_02",
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

- `request_id` - уникальный id запуска со стороны сервера
- `triggered_at` - время запуска
- `reason` - причина запуска, например `day_after_salary`
- `family.id` - идентификатор семьи
- `family.name` - необязательное имя семьи
- `member.id` - идентификатор пользователя
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
  "request_id": "req_2026_03_15_member_anna",
  "workflow_run_id": "n8n_run_001"
}
```

### Ответ `401 Unauthorized`

```json
{
  "error": {
    "code": "INVALID_TOKEN",
    "message": "Authorization token is invalid"
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

- начинает опрос пользователя;
- собирает значения по запрошенным полям;
- нормализует числа;
- при необходимости задает ограниченное число уточнений;
- отправляет итоговые данные на сервер;
- использует `request_id` для трассировки всей цепочки.

## 11. Минимальный состав для MVP

Для первой версии достаточно, чтобы сервер передавал:

- кого опросить;
- куда писать в Telegram;
- по какому зарплатному событию запускается сбор;
- за какой месяц собирать данные;
- какие 4 поля нужно собрать;
- куда отправить результат обратно.

Этого уже достаточно для рабочей интеграции сервер -> `n8n` -> Telegram -> сервер.
