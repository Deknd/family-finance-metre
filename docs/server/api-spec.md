# Спецификация API сервера

## 1. Назначение

Этот документ описывает фактическое внешнее API сервера для текущего MVP.

В рамках MVP сервер взаимодействует с двумя клиентами:

- физическое устройство на ESP32;
- `n8n`, которое после опроса пользователя отправляет на сервер собранные данные.

Отдельный контракт server-triggered запуска intake-агента вынесен в
[llm-agent-trigger-spec.md](../n8n/llm-agent-trigger-spec.md).

Подробная внутренняя схема `n8n`-агента описана в
[telegram-finance-intake-agent-spec.md](../n8n/telegram-finance-intake-agent-spec.md).

## 2. Роли API

Сервер в MVP решает две основные задачи:

- отдает устройству уже рассчитанный семейный dashboard;
- принимает новые финансовые данные, собранные через опрос пользователя.

Модель данных для MVP:

- входные данные всегда собираются отдельно по каждому члену семьи;
- каждый payload от `n8n` относится к одному конкретному человеку;
- семейные показатели для устройства сервер считает сам на основе данных всех членов семьи;
- один intake payload соответствует одному payroll-событию или одной конкретной выплате.

В этой спецификации не описываются:

- внутренняя БД;
- планировщик задач;
- внутренняя логика вычислений;
- внутренние сервисные методы.

## 3. Общие правила

### 3.1. Формат данных

- все запросы и ответы используют `application/json`;
- даты и время передаются в ISO 8601 с часовым поясом;
- денежные значения в текущем MVP передаются как целые числа в рублях.

### 3.2. Версионирование

Все публичные endpoint'ы текущей версии используют префикс:

- `/api/v1/...`

### 3.3. Аутентификация

Для MVP используются два отдельных заголовка:

- устройство передает `X-Device-Token`;
- `n8n` передает `X-API-Key`.

### 3.4. Идемпотентность intake

Для входящих callback-данных от `n8n` сервер использует:

- `external_submission_id`

Это позволяет не создавать дубликаты, если один и тот же callback будет отправлен повторно.

## 4. Endpoint для физического устройства

### 4.1. Получение данных для экрана

`GET /api/v1/device/dashboard`

#### Назначение

Физическое устройство вызывает endpoint по таймеру и получает уже готовые к отображению данные.

#### Кто вызывает

- ESP32

#### Заголовки

```http
X-Device-Token: <device-token>
```

#### Query-параметры

В текущей реализации query-параметры не используются.

Сервер определяет устройство и его семью по `X-Device-Token`.

#### Ответ `200 OK`

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
    "credit_load_percent": 27.00,
    "emergency_fund_months": 2.00
  },
  "display": {
    "currency": "RUB",
    "updated_at_label": "15.03 09:00"
  }
}
```

#### Поля ответа

- `generated_at` - время расчета актуального dashboard snapshot-а (`family_dashboard_snapshots.calculated_at`), приведенное к таймзоне семьи;
- `device_id` - идентификатор аутентифицированного устройства;
- `family_id` - идентификатор семьи устройства;
- `status` - машинное значение статуса: `normal`, `warning`, `risk`;
- `status_text` - короткий текст для UI;
- `status_reason` - короткая причина для второй строки на экране;
- `metrics.monthly_income` - суммарный доход семьи за месяц;
- `metrics.monthly_expenses` - актуальная оценка расходов семьи за месяц на текущий момент;
- `metrics.credit_load_percent` - кредитная нагрузка семьи в процентах;
- `metrics.emergency_fund_months` - подушка безопасности семьи в месяцах расходов;
- `display.currency` - валюта отображения;
- `display.updated_at_label` - готовая строка для рендера на экране в формате `dd.MM HH:mm`, сформированная из `generated_at` в таймзоне семьи.

#### Правило выбора данных

Сервер отдает snapshot за последний доступный расчетный период семьи,
то есть по максимальным `period_year` и `period_month`.

#### Обновление `last_seen_at`

После успешного ответа `200 OK` сервер обновляет `devices.last_seen_at`.

Если dashboard не найден или запрос завершился ошибкой, `last_seen_at` не обновляется.

#### MVP policy статуса

Для текущего MVP сервер определяет статус локально и детерминированно:

- `status_text`: `normal -> Норма`, `warning -> Внимание`, `risk -> Риск`;
- `risk`, если `monthly_expenses > 0` и `emergency_fund_months < 1.00`;
- `risk`, если `credit_load_percent >= 50.00`;
- `warning`, если `monthly_expenses > 0` и `emergency_fund_months < 3.00`, но не сработал `risk`;
- `warning`, если `credit_load_percent >= 30.00`, но не сработал `risk`;
- иначе `normal`;
- при одинаковой тяжести факторов `status_reason` выбирается по приоритету:
  сначала подушка, затем кредитная нагрузка.

Каталог причин для `status_reason`:

- `normal` -> `Показатели в пределах нормы`;
- `warning` по подушке -> `Подушка ниже комфортной зоны`;
- `warning` по кредитной нагрузке -> `Кредитная нагрузка выше комфортной`;
- `risk` по подушке -> `Подушка меньше одного месяца`;
- `risk` по кредитной нагрузке -> `Кредитная нагрузка в зоне риска`.

Примечание:

- при `monthly_expenses = 0` правило по подушке не применяется;
- позже источник policy и текста причины можно вынести в отдельный адаптер,
  но поля `status`, `status_text` и `status_reason` в API должны сохраниться.

#### Ответ `404 Not Found`

Используется, если для семьи аутентифицированного устройства еще нет рассчитанного dashboard snapshot.

```json
{
  "error": {
    "code": "DASHBOARD_NOT_READY",
    "message": "Dashboard data is not available yet"
  }
}
```

#### Ответ `401 Unauthorized`

```json
{
  "error": {
    "code": "INVALID_DEVICE_TOKEN",
    "message": "Device token is invalid"
  }
}
```

## 5. Endpoint приема данных от `n8n`

### 5.1. Прием результата опроса пользователя

`POST /api/v1/intake/user-finance-data`

#### Назначение

Endpoint принимает итоговые данные, которые LLM-агент собрал у пользователя через Telegram.

После приема сервер:

- валидирует структуру payload;
- валидирует ссылки `family_id` и `member_id`;
- при наличии `request_id` проверяет и связывает callback с `llm_collection_request`;
- сохраняет submission в `finance_submissions`;
- пересчитывает `member_finance_snapshots`;
- пересчитывает `family_dashboard_snapshots`;
- при валидной связке по `request_id` переводит `llm_collection_request` в статус `completed`.

#### Кто вызывает

- `n8n`

#### Заголовки

```http
X-API-Key: <n8n-api-key>
Content-Type: application/json
```

#### Тело запроса

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

#### Поля запроса

- `external_submission_id` - уникальный id отправки со стороны `n8n`;
- `request_id` - optional строковый correlation id исходного запуска опроса;
- `family_id` - идентификатор семьи в формате UUID;
- `member_id` - идентификатор члена семьи в формате UUID;
- `source` - источник данных, для MVP ожидается строго `telegram`;
- `collected_at` - когда опрос был завершен;
- `period.year` - год, к которому относятся данные;
- `period.month` - месяц, к которому относятся данные;
- `finance_input.monthly_income` - сумма дохода, относящаяся именно к текущей выплате или payroll-событию;
- `finance_input.monthly_expenses` - актуальная оценка всех расходов пользователя за весь месяц на текущий момент;
- `finance_input.monthly_credit_payments` - актуальная оценка всех платежей по кредитам за весь месяц на текущий момент;
- `finance_input.liquid_savings` - доступные накопления пользователя на момент опроса;
- `meta.telegram_chat_id` - чат пользователя в Telegram;
- `meta.confidence` - optional оценка уверенности: `low`, `medium`, `high`;
- `meta.notes` - optional комментарий.

#### Поведение `request_id`

- `request_id` в intake API трактуется как optional строковый correlation id;
- для server-triggered payroll-сценария сервер сам генерирует `request_id` как UUID-строку и ожидает, что `n8n` вернет это значение без изменений;
- на уровне intake DTO сервер не валидирует `request_id` как UUID отдельным правилом;
- если `request_id` отсутствует, равен `null` или пустой строке, payload обрабатывается как standalone intake без связи с `llm_collection_request`;
- если `request_id` передан, сервер требует, чтобы соответствующий `llm_collection_request` существовал и находился в статусе `accepted`;
- если `request_id` передан, но запись не найдена или не находится в статусе `accepted`, сервер возвращает `422 Unprocessable Entity` с `VALIDATION_ERROR`.

#### Семантика intake при нескольких выплатах в месяц

- один payload соответствует одному payroll-событию;
- если у человека в одном месяце две или три выплаты, `n8n` отправляет отдельный payload на каждую выплату;
- сервер суммирует `finance_input.monthly_income` по всем payload за один `member_id + period_year + period_month`;
- поля `finance_input.monthly_expenses`, `finance_input.monthly_credit_payments` и `finance_input.liquid_savings` в месячном snapshot берутся из самой свежей submission за период как текущая оценка на момент последнего опроса.

#### Ответ `202 Accepted`

```json
{
  "status": "accepted",
  "submission_id": "88888888-8888-8888-8888-888888888888",
  "family_id": "11111111-1111-1111-1111-111111111111",
  "member_id": "22222222-2222-2222-2222-222222222222",
  "recalculation_scheduled": true
}
```

Поля ответа:

- `status` - подтверждение, что payload принят;
- `submission_id` - идентификатор сохраненной записи в `finance_submissions`;
- `family_id` - идентификатор семьи из сохраненного submission;
- `member_id` - идентификатор члена семьи из сохраненного submission;
- `recalculation_scheduled` - совместимый флаг ответа; в текущей реализации всегда возвращается `true`, хотя сохранение submission и пересчет обоих snapshot-слоев уже выполнены синхронно до отправки ответа.

#### Ответ `401 Unauthorized`

```json
{
  "error": {
    "code": "INVALID_API_KEY",
    "message": "API key is invalid"
  }
}
```

#### Ответ `409 Conflict`

Используется, если `external_submission_id` уже был обработан.

```json
{
  "error": {
    "code": "DUPLICATE_SUBMISSION",
    "message": "Submission with this external_submission_id already exists"
  }
}
```

#### Ответ `422 Unprocessable Entity`

Используется, если request body не проходит структурную или доменную валидацию.

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed"
  },
  "details": [
    {
      "field": "finance_input.monthly_income",
      "message": "must be greater than or equal to 0"
    }
  ]
}
```

Этот же ответ используется, если:

- `family_id` не существует;
- `member_id` не существует;
- `member_id` не принадлежит указанной семье;
- `request_id` передан, но соответствующий `llm_collection_request` не найден;
- `request_id` передан, но соответствующий `llm_collection_request` не находится в статусе `accepted`.

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

Пример для `request_id`, который найден, но уже не находится в статусе `accepted`:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Request validation failed"
  },
  "details": [
    {
      "field": "request_id",
      "message": "llm collection request must be in accepted status"
    }
  ]
}
```

## 6. Минимальный набор endpoint'ов MVP

Для текущего MVP достаточно двух публичных endpoint'ов:

1. `GET /api/v1/device/dashboard`
2. `POST /api/v1/intake/user-finance-data`

Этого достаточно, чтобы:

- устройство регулярно получало свежие агрегированные данные;
- `n8n` могло отправлять результаты опроса;
- сервер мог пересчитывать состояние семьи после каждого нового ввода.
