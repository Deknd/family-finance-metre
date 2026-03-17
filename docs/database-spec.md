# Спецификация базы данных сервера

## 1. Назначение

Этот документ описывает структуру базы данных сервера для MVP.

База должна поддерживать 4 основных сценария:

- хранение семей, членов семьи и устройств;
- хранение данных, пришедших от `n8n` после опроса пользователя;
- хранение запусков LLM-опросов через `n8n`;
- хранение агрегированных семейных метрик для быстрого ответа устройству.

## 2. Ключевые принципы модели

- входные финансовые данные всегда привязаны к одному члену семьи;
- семейные показатели не вводятся руками, а вычисляются сервером;
- сырые входные данные и рассчитанные агрегаты хранятся отдельно;
- все интеграционные вызовы должны быть трассируемыми;
- для MVP лучше выбрать простую реляционную модель без избыточной нормализации.

## 3. Рекомендуемая СУБД

Для сервера я рекомендую `PostgreSQL`.

Причины:

- удобная реляционная модель;
- хорошие ограничения и индексы;
- удобно хранить интеграционные `jsonb` payload'ы;
- легко развивать проект после MVP.

## 4. Главные сущности

В MVP нужны такие сущности:

- `families`
- `family_members`
- `devices`
- `member_payroll_schedules`
- `llm_collection_requests`
- `finance_submissions`
- `member_finance_snapshots`
- `family_dashboard_snapshots`

## 5. Логика хранения данных

### 5.1. Сырые данные

Все, что пришло от `n8n`, нужно сохранять как отдельный факт приема.

Для этого используется:

- `finance_submissions`

Это важно для:

- аудита;
- повторного пересчета;
- поиска ошибок;
- защиты от дублей.

### 5.2. Нормализованные данные по человеку

После приема payload сервер должен собрать месячное состояние по конкретному человеку за конкретный месяц
на основе всех `finance_submissions`, пришедших за этот период.

Для этого используется:

- `member_finance_snapshots`

Это уже не сырое сообщение, а агрегированная запись, с которой удобно считать семейные показатели.

### 5.3. Агрегаты для устройства

Чтобы устройство быстро получало ответ, серверу лучше хранить готовый последний срез семьи.

Для этого используется:

- `family_dashboard_snapshots`

Именно из этой таблицы удобно отдавать данные в `GET /api/v1/device/dashboard`.

## 6. Структура таблиц

## 6.1. `families`

Хранит семью как верхнеуровневую сущность.

Поля:

- `id` `uuid` PK
- `name` `varchar(255)` not null
- `timezone` `varchar(64)` not null default `'Europe/Moscow'`
- `currency_code` `varchar(3)` not null default `'RUB'`
- `status` `varchar(32)` not null default `'active'`
- `created_at` `timestamptz` not null
- `updated_at` `timestamptz` not null

Ограничения:

- `status in ('active', 'archived')`

## 6.2. `family_members`

Хранит членов семьи.

Поля:

- `id` `uuid` PK
- `family_id` `uuid` not null FK -> `families.id`
- `first_name` `varchar(255)` not null
- `last_name` `varchar(255)` null
- `display_name` `varchar(255)` null
- `telegram_chat_id` `varchar(64)` null
- `telegram_username` `varchar(255)` null
- `is_active` `boolean` not null default `true`
- `created_at` `timestamptz` not null
- `updated_at` `timestamptz` not null

Индексы:

- index on `family_id`
- unique partial index on `telegram_chat_id` where `telegram_chat_id is not null`

## 6.3. `devices`

Хранит физические устройства, привязанные к семье.

Поля:

- `id` `uuid` PK
- `family_id` `uuid` not null FK -> `families.id`
- `name` `varchar(255)` not null
- `device_token_hash` `varchar(255)` not null
- `status` `varchar(32)` not null default `'active'`
- `last_seen_at` `timestamptz` null
- `created_at` `timestamptz` not null
- `updated_at` `timestamptz` not null

Ограничения:

- `status in ('active', 'disabled')`

Индексы:

- index on `family_id`
- unique index on `device_token_hash`

## 6.4. `member_payroll_schedules`

Хранит правила зарплатных выплат для конкретного члена семьи.

Один человек может иметь несколько выплат в месяц.

Примеры:

- 16 число и последний день месяца
- 5 и 25 число каждого месяца

Для MVP правило переноса едино для всех зарплатных дат:

- если выплата попала на выходной, она переносится на ближайший рабочий день.

Поля:

- `id` `uuid` PK
- `member_id` `uuid` not null FK -> `family_members.id`
- `label` `varchar(255)` null
- `schedule_type` `varchar(32)` not null
- `day_of_month` `smallint` null
- `trigger_delay_days` `smallint` not null default `1`
- `is_active` `boolean` not null default `true`
- `created_at` `timestamptz` not null
- `updated_at` `timestamptz` not null

Ограничения:

- `schedule_type in ('fixed_day_of_month', 'last_day_of_month')`
- если `schedule_type = 'fixed_day_of_month'`, то `day_of_month between 1 and 31`
- если `schedule_type = 'last_day_of_month'`, то `day_of_month is null`
- `trigger_delay_days between 0 and 7`

Индексы:

- index on `member_id`
- unique index on `(member_id, schedule_type, coalesce(day_of_month, 0))`

Примечание:

- для MVP под "рабочим днем" можно считать любой день, который не является субботой или воскресеньем;
- правило переноса фиксированное для всей системы: `nearest_business_day`;
- официальные праздничные календари можно добавить позже отдельным справочником.

## 6.5. `llm_collection_requests`

Хранит каждый запуск опроса, который сервер инициировал в `n8n`.

Поля:

- `id` `uuid` PK
- `request_id` `varchar(255)` not null unique
- `family_id` `uuid` not null FK -> `families.id`
- `member_id` `uuid` not null FK -> `family_members.id`
- `payroll_schedule_id` `uuid` not null FK -> `member_payroll_schedules.id`
- `period_year` `int` not null
- `period_month` `smallint` not null
- `reason` `varchar(64)` not null
- `status` `varchar(32)` not null
- `requested_fields` `jsonb` not null
- `nominal_payroll_date` `date` not null
- `effective_payroll_date` `date` not null
- `scheduled_trigger_date` `date` not null
- `triggered_at` `timestamptz` not null
- `accepted_at` `timestamptz` null
- `completed_at` `timestamptz` null
- `workflow_run_id` `varchar(255)` null
- `request_payload` `jsonb` not null
- `response_payload` `jsonb` null
- `error_message` `text` null
- `created_at` `timestamptz` not null
- `updated_at` `timestamptz` not null

Ограничения:

- `period_month between 1 and 12`
- `status in ('pending', 'accepted', 'completed', 'failed', 'cancelled')`

Индексы:

- index on `(member_id, period_year, period_month)`
- index on `(payroll_schedule_id, effective_payroll_date)`
- index on `status`
- unique index on `(payroll_schedule_id, effective_payroll_date)`

## 6.6. `finance_submissions`

Хранит каждый payload, который пришел от `n8n`.

Одна запись соответствует одному payroll-событию или одной конкретной выплате.

Поля:

- `id` `uuid` PK
- `external_submission_id` `varchar(255)` not null unique
- `request_id` `varchar(255)` null
- `llm_collection_request_id` `uuid` null FK -> `llm_collection_requests.id`
- `family_id` `uuid` not null FK -> `families.id`
- `member_id` `uuid` not null FK -> `family_members.id`
- `source` `varchar(32)` not null
- `period_year` `int` not null
- `period_month` `smallint` not null
- `collected_at` `timestamptz` not null
- `monthly_income` `integer` not null
- `monthly_expenses` `integer` not null
- `monthly_credit_payments` `integer` not null
- `liquid_savings` `integer` not null
- `confidence` `varchar(16)` null
- `notes` `text` null
- `raw_payload` `jsonb` not null
- `created_at` `timestamptz` not null

Ограничения:

- `period_month between 1 and 12`
- `source in ('telegram')`
- `confidence in ('low', 'medium', 'high')` or null
- все денежные поля `>= 0`

Индексы:

- index on `(member_id, period_year, period_month, collected_at desc)`
- index on `(family_id, period_year, period_month)`
- index on `request_id`

Примечание:

В этой таблице полезно хранить именно финальные нормализованные поля из payload и сам `raw_payload` целиком.
При этом `monthly_income` в этой таблице хранит сумму конкретной выплаты,
а не обязательно весь доход пользователя за месяц.

## 6.7. `member_finance_snapshots`

Хранит агрегированное финансовое состояние конкретного члена семьи за конкретный месяц.

Это производная таблица, которую сервер обновляет после приема `finance_submissions`.

Поля:

- `id` `uuid` PK
- `family_id` `uuid` not null FK -> `families.id`
- `member_id` `uuid` not null FK -> `family_members.id`
- `period_year` `int` not null
- `period_month` `smallint` not null
- `source_submission_id` `uuid` not null FK -> `finance_submissions.id`
- `monthly_income` `integer` not null
- `monthly_expenses` `integer` not null
- `monthly_credit_payments` `integer` not null
- `liquid_savings` `integer` not null
- `collected_at` `timestamptz` not null
- `created_at` `timestamptz` not null
- `updated_at` `timestamptz` not null

Ограничения:

- unique index on `(member_id, period_year, period_month)`
- `period_month between 1 and 12`
- все денежные поля `>= 0`

Смысл:

- одна запись = агрегированный месячный снимок по одному человеку за один месяц

Правила расчета:

- `monthly_income` = сумма всех `finance_submissions.monthly_income` по одному `member_id + period_year + period_month`
- `monthly_expenses` = значение из самой свежей `finance_submission` за период как текущая оценка расходов за месяц на момент опроса
- `monthly_credit_payments` = значение из самой свежей `finance_submission` за период как текущая оценка кредитных платежей за месяц на момент опроса
- `liquid_savings` = значение из самой свежей `finance_submission` за период как состояние накоплений на момент опроса
- `source_submission_id` = id самой свежей `finance_submission`, на основе которой зафиксированы неаддитивные поля
- `collected_at` = `collected_at` самой свежей `finance_submission`, использованной как текущая версия состояния

Это позволяет корректно обрабатывать две, три и более выплаты в одном месяце без потери общего дохода за период.

## 6.8. `family_dashboard_snapshots`

Хранит агрегированные значения семьи для экрана устройства.

Поля:

- `id` `uuid` PK
- `family_id` `uuid` not null FK -> `families.id`
- `period_year` `int` not null
- `period_month` `smallint` not null
- `status` `varchar(32)` not null
- `status_text` `varchar(64)` not null
- `status_reason` `varchar(255)` not null
- `monthly_income` `integer` not null
- `monthly_expenses` `integer` not null
- `credit_load_percent` `numeric(5,2)` not null
- `emergency_fund_months` `numeric(8,2)` not null
- `member_count_used` `integer` not null
- `calculated_at` `timestamptz` not null
- `created_at` `timestamptz` not null
- `updated_at` `timestamptz` not null

Ограничения:

- unique index on `(family_id, period_year, period_month)`
- `status in ('normal', 'warning', 'risk')`
- `member_count_used >= 0`

Смысл:

- одна запись = последний рассчитанный семейный экран за конкретный месяц

## 7. Связи между таблицами

Базовые связи:

- одна `families` -> много `family_members`
- одна `families` -> много `devices`
- один `family_members` -> много `member_payroll_schedules`
- один `family_members` -> много `llm_collection_requests`
- один `family_members` -> много `finance_submissions`
- один `family_members` -> много `member_finance_snapshots`
- одна `families` -> много `family_dashboard_snapshots`

## 8. Как работает поток данных

### 8.1. Запуск опроса

1. Планировщик на сервере определяет, что у члена семьи был день зарплаты.
2. Сервер рассчитывает фактическую дату выплаты по правилу из `member_payroll_schedules`.
3. Сервер рассчитывает дату запуска опроса как `effective_payroll_date + trigger_delay_days`.
4. Сервер создает запись в `llm_collection_requests`.
5. Сервер вызывает webhook в `n8n`.
6. После ответа `n8n` запись обновляется статусом `accepted`.

### 8.2. Прием результата опроса

1. `n8n` вызывает `POST /api/v1/intake/user-finance-data`.
2. Сервер сохраняет payload в `finance_submissions`.
3. Сервер пересчитывает запись в `member_finance_snapshots` по всем submission за месяц.
4. Сервер пересчитывает агрегат семьи и обновляет `family_dashboard_snapshots`.

### 8.3. Ответ устройству

1. Устройство вызывает `GET /api/v1/device/dashboard`.
2. Сервер находит устройство по токену.
3. Сервер находит семью устройства.
4. Сервер отдает последний актуальный `family_dashboard_snapshots`.

## 9. Индексы, которые важны в MVP

Критичные индексы:

- `devices(device_token_hash)`
- `finance_submissions(external_submission_id)`
- `finance_submissions(member_id, period_year, period_month, collected_at desc)`
- `member_finance_snapshots(member_id, period_year, period_month)`
- `family_dashboard_snapshots(family_id, period_year, period_month)`
- `llm_collection_requests(request_id)`

## 10. Что сознательно не добавляем в MVP

Чтобы не усложнять первую версию, в БД пока не нужны:

- отдельные таблицы транзакций;
- отдельные таблицы категорий расходов;
- кредиты поштучно;
- сложные роли пользователей;
- история рендеров экрана устройства;
- таблицы уведомлений и push-событий;
- отдельная таблица для аудита каждого шага LLM-диалога.

## 11. Рекомендация по реализации

Для MVP я рекомендую строить БД вокруг трех уровней:

- справочные сущности: `families`, `family_members`, `devices`, `member_payroll_schedules`
- интеграционные события: `llm_collection_requests`, `finance_submissions`
- расчетные слои: `member_finance_snapshots`, `family_dashboard_snapshots`

Это дает хороший баланс между:

- простотой разработки;
- трассируемостью;
- удобством пересчета;
- скоростью ответа устройству;
- возможностью спокойно расширять проект дальше.
