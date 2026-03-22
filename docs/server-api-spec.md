# Спецификация API сервера

## 1. Назначение

Этот документ описывает внешнее API сервера для MVP.

В рамках MVP сервер взаимодействует с двумя клиентами:

- физическое устройство на ESP32;
- `n8n`, которое после опроса пользователя отправляет на сервер собранные данные.

Отдельный контракт запуска LLM-агента вынесен в другой документ.

Подробная внутренняя схема `n8n`-агента описана в [n8n-telegram-finance-intake-agent-spec.md](/D:/IdeaProjects/family-finance-metre/docs/n8n-telegram-finance-intake-agent-spec.md).

## 2. Роли API

Сервер в MVP должен решать 2 основные задачи:

- отдавать устройству готовые данные для отображения;
- принимать новые финансовые данные, собранные через опрос пользователя.

Модель данных для MVP:

- входные данные всегда собираются отдельно по каждому члену семьи;
- каждый payload от `n8n` относится к одному конкретному человеку;
- семейные показатели для устройства сервер считает сам на основе данных всех членов семьи.

В этой спецификации не описываются:

- внутренняя БД;
- планировщик задач;
- внутренняя логика вычислений;
- внутренние сервисные методы.

## 3. Общие правила

### 3.1. Формат данных

- все запросы и ответы: `application/json`
- даты и время: ISO 8601 с часовым поясом
- денежные значения: целые числа в минимальной денежной единице или в целых рублях

Для MVP проще использовать целые значения в рублях.

### 3.2. Версионирование

Все endpoint'ы первой версии:

- `/api/v1/...`

### 3.3. Аутентификация

Для MVP достаточно простой схемы:

- устройство передает `X-Device-Token`
- `n8n` передает `X-API-Key`

Позже это можно заменить на более строгую схему.

### 3.4. Идемпотентность

Для входящих данных от `n8n` желательно поддержать:

- `external_submission_id`

Это позволит не создавать дубликаты, если workflow будет переотправлен повторно.

## 4. Endpoint для физического устройства

## 4.1. Получение данных для экрана

`GET /api/v1/device/dashboard`

### Назначение

Физическое устройство вызывает endpoint по таймеру и получает уже готовые к отображению данные.

### Кто вызывает

- ESP32

### Заголовки

```http
X-Device-Token: <device-token>
```

### Query-параметры

Можно не использовать в первой версии.

Опционально позже:

- `device_id`
- `family_id`

Если устройство жестко привязано к одной семье, `device_id` можно получать из токена.

В текущей серверной реализации `device_id` и `family_id`
передаются строками с UUID-значениями.

### Ответ `200 OK`

```json
{
  "generated_at": "2026-03-15T09:00:00+03:00",
  "device_id": "44444444-4444-4444-4444-444444444444",
  "family_id": "11111111-1111-1111-1111-111111111111",
  "status": "warning",
  "status_text": "Внимание",
  "status_reason": "Подушка ниже комфортной зоны",
  "metrics": {
    "monthly_income": 210000,
    "monthly_expenses": 90000,
    "credit_load_percent": 27.0,
    "emergency_fund_months": 2.0
  },
  "display": {
    "currency": "RUB",
    "updated_at_label": "15.03 09:00"
  }
}
```

### Поля ответа

- `generated_at` - время расчета актуального dashboard snapshot-а
  (`family_dashboard_snapshots.calculated_at`) в таймзоне семьи
- `device_id` - идентификатор устройства
- `family_id` - идентификатор семьи
- `status` - машинное значение статуса: `normal`, `warning`, `risk`
- `status_text` - короткий текст для UI
- `status_reason` - короткая причина для второй строки на экране
- `metrics.monthly_income` - суммарный доход семьи за месяц
- `metrics.monthly_expenses` - актуальная оценка расходов семьи за месяц на текущий момент
- `metrics.credit_load_percent` - кредитная нагрузка в процентах
- `metrics.emergency_fund_months` - подушка в месяцах
- `display.currency` - валюта отображения
- `display.updated_at_label` - готовая строка для рендера на экране,
  сформированная из `generated_at` в таймзоне семьи

### Правило выбора данных

Сервер отдает snapshot за последний доступный расчетный период семьи,
то есть по максимальным `period_year` и `period_month`.

### MVP policy статуса

Для MVP сервер определяет статус локально и детерминированно:

- `status_text`: `normal -> Норма`, `warning -> Внимание`, `risk -> Риск`
- `risk`, если `credit_load_percent >= 50.00`
- `risk`, если `monthly_expenses > 0` и `emergency_fund_months < 1.00`
- `warning`, если `credit_load_percent >= 30.00`, но не сработал `risk`
- `warning`, если `monthly_expenses > 0` и `emergency_fund_months < 3.00`, но не сработал `risk`
- иначе `normal`
- при нескольких одновременно сработавших правилах `status_reason`
  выбирается по самому тяжелому фактору;
  при одинаковой тяжести сначала подушка, потом кредитная нагрузка

Каталог причин для `status_reason`:

- `normal` -> `Показатели в пределах нормы`
- `warning` по подушке -> `Подушка ниже комфортной зоны`
- `warning` по кредитной нагрузке -> `Кредитная нагрузка выше комфортной`
- `risk` по подушке -> `Подушка меньше одного месяца`
- `risk` по кредитной нагрузке -> `Кредитная нагрузка в зоне риска`

Примечание на будущее:

- в MVP эти правила и комментарии вычисляются на сервере;
- позже источник policy и текста причины можно вынести в `n8n` или LLM-агент,
  но поля `status`, `status_text` и `status_reason` в API должны сохраниться.

### Ответ `404 Not Found`

Используется если:

- устройство не привязано к семье;
- для семьи еще нет рассчитанных данных.

Пример:

```json
{
  "error": {
    "code": "DASHBOARD_NOT_READY",
    "message": "Dashboard data is not available yet"
  }
}
```

### Ответ `401 Unauthorized`

```json
{
  "error": {
    "code": "INVALID_DEVICE_TOKEN",
    "message": "Device token is invalid"
  }
}
```

## 5. Endpoint приема данных от n8n

## 5.1. Прием результата опроса пользователя

`POST /api/v1/intake/user-finance-data`

### Назначение

Endpoint принимает итоговые данные, которые LLM-агент собрал у пользователя через Telegram.

После приема сервер должен:

- провалидировать данные;
- сохранить сырые входные значения;
- пересчитать агрегаты семьи;
- обновить данные для устройства.

### Кто вызывает

- `n8n`

### Заголовки

```http
X-API-Key: <n8n-api-key>
Content-Type: application/json
```

### Тело запроса

```json
{
  "external_submission_id": "n8n-run-2026-03-15-001",
  "request_id": "req-2026-03-15-member-anna",
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

### Поля запроса

- `external_submission_id` - уникальный id отправки со стороны `n8n`
- `request_id` - необязательный correlation id исходного запуска опроса со стороны сервера
- `family_id` - идентификатор семьи в формате UUID
- `member_id` - идентификатор члена семьи, которого опрашивали, в формате UUID
- `source` - источник данных, для MVP ожидается `telegram`
- `collected_at` - когда опрос был завершен
- `period.year` - год, к которому относятся данные
- `period.month` - месяц, к которому относятся данные
- `finance_input.monthly_income` - сумма дохода, относящаяся именно к текущей выплате или payroll-событию
- `finance_input.monthly_expenses` - актуальная оценка всех расходов пользователя за весь месяц на текущий момент
- `finance_input.monthly_credit_payments` - актуальная оценка всех платежей по кредитам за весь месяц на текущий момент
- `finance_input.liquid_savings` - доступные накопления пользователя на момент опроса
- `meta.telegram_chat_id` - чат пользователя в Telegram
- `meta.confidence` - необязательная оценка уверенности: `low`, `medium`, `high`
- `meta.notes` - необязательный комментарий

Если `request_id` не передан, сервер все равно принимает payload и обрабатывает его как обычный intake без связки с конкретным `llm_collection_request`.

Если `request_id` передан, но такой `llm_collection_request` отсутствует, сервер возвращает `422 Unprocessable Entity` с `VALIDATION_ERROR`.

### Семантика intake при нескольких выплатах в месяц

- один payload соответствует одному payroll-событию;
- если у человека в одном месяце две или три выплаты, `n8n` отправляет отдельный payload на каждую выплату;
- сервер суммирует `finance_input.monthly_income` по всем payload за один `member_id + period_year + period_month`;
- поля `finance_input.monthly_expenses`, `finance_input.monthly_credit_payments` и `finance_input.liquid_savings` в месячном snapshot берутся из самой свежей submission за период как текущая оценка на момент последнего опроса.

### Ответ `202 Accepted`

```json
{
  "status": "accepted",
  "submission_id": "subm_001",
  "family_id": "11111111-1111-1111-1111-111111111111",
  "member_id": "22222222-2222-2222-2222-222222222222",
  "recalculation_scheduled": true
}
```

### Ответ `409 Conflict`

Используется если `external_submission_id` уже был обработан.

```json
{
  "error": {
    "code": "DUPLICATE_SUBMISSION",
    "message": "Submission with this external_submission_id already exists"
  }
}
```

### Ответ `422 Unprocessable Entity`

Используется если не хватает обязательных полей или числа невалидны.

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed"
  },
  "details": [
    {
      "field": "finance_input.monthly_income",
      "message": "Must be greater than or equal to 0"
    }
  ]
}
```

Также этот ответ используется, если:

- `family_id` не существует;
- `member_id` не существует;
- `request_id` передан, но соответствующий `llm_collection_request` не найден.

Пример для несуществующего `request_id`:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed"
  },
  "details": [
    {
      "field": "request_id",
      "message": "llm collection request does not exist"
    }
  ]
}
```

## 6. Минимальный набор endpoint'ов MVP

Для первой версии достаточно двух публичных endpoint'ов:

1. `GET /api/v1/device/dashboard`
2. `POST /api/v1/intake/user-finance-data`

Этого достаточно, чтобы:

- устройство регулярно получало свежие агрегированные данные;
- `n8n` могло отправлять результаты опроса;
- сервер мог пересчитывать состояние семьи после каждого нового ввода.

## 7. Открытые вопросы для следующей итерации

Эти вопросы пока не обязательно решать в API v1, но их стоит держать в уме:

- как именно сервер агрегирует персональные расходы и накопления в семейные показатели;
- допускаются ли общие семейные расходы, не привязанные к одному человеку;
- допускаются ли общие семейные накопления, не привязанные к одному человеку;
- нужен ли endpoint подтверждения успешного отображения на устройстве;
- нужно ли хранить историю снимков для экрана;
- нужен ли отдельный endpoint для ручного обновления данных.
