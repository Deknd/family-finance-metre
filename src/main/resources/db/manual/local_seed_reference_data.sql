-- Manual local seed for a demo family.
-- This script is intentionally not a Flyway migration and should be executed manually.

insert into families (
    id,
    name,
    timezone,
    currency_code,
    status,
    created_at,
    updated_at
)
values (
    '11111111-1111-1111-1111-111111111111',
    'Demo family',
    'Europe/Moscow',
    'RUB',
    'active',
    '2026-03-15T09:00:00+03:00',
    '2026-03-15T09:00:00+03:00'
)
on conflict (id) do update
set name = excluded.name,
    timezone = excluded.timezone,
    currency_code = excluded.currency_code,
    status = excluded.status,
    updated_at = excluded.updated_at;

insert into family_members (
    id,
    family_id,
    first_name,
    last_name,
    display_name,
    telegram_chat_id,
    telegram_username,
    is_active,
    created_at,
    updated_at
)
values
(
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    'Anna',
    'Ivanova',
    'Anna',
    'demo-family-anna-chat',
    'anna_demo',
    true,
    '2026-03-15T09:05:00+03:00',
    '2026-03-15T09:05:00+03:00'
),
(
    '33333333-3333-3333-3333-333333333333',
    '11111111-1111-1111-1111-111111111111',
    'Ivan',
    'Ivanov',
    'Ivan',
    'demo-family-ivan-chat',
    'ivan_demo',
    true,
    '2026-03-15T09:06:00+03:00',
    '2026-03-15T09:06:00+03:00'
)
on conflict (id) do update
set family_id = excluded.family_id,
    first_name = excluded.first_name,
    last_name = excluded.last_name,
    display_name = excluded.display_name,
    telegram_chat_id = excluded.telegram_chat_id,
    telegram_username = excluded.telegram_username,
    is_active = excluded.is_active,
    updated_at = excluded.updated_at;

insert into devices (
    id,
    family_id,
    name,
    device_token_hash,
    status,
    last_seen_at,
    created_at,
    updated_at
)
values (
    '44444444-4444-4444-4444-444444444444',
    '11111111-1111-1111-1111-111111111111',
    'Hall display',
    'e9bdbf88b2ec36a3e0bd9d60f2cd413a1631d5dd2f6053d342dc27d24fa4a447',
    'active',
    null,
    '2026-03-15T09:10:00+03:00',
    '2026-03-15T09:10:00+03:00'
)
on conflict (id) do update
set family_id = excluded.family_id,
    name = excluded.name,
    device_token_hash = excluded.device_token_hash,
    status = excluded.status,
    last_seen_at = excluded.last_seen_at,
    updated_at = excluded.updated_at;

insert into member_payroll_schedules (
    id,
    member_id,
    label,
    schedule_type,
    day_of_month,
    trigger_delay_days,
    is_active,
    created_at,
    updated_at
)
values
(
    '55555555-5555-5555-5555-555555555551',
    '22222222-2222-2222-2222-222222222222',
    'Anna salary on 16th',
    'fixed_day_of_month',
    16,
    1,
    true,
    '2026-03-15T09:15:00+03:00',
    '2026-03-15T09:15:00+03:00'
),
(
    '55555555-5555-5555-5555-555555555552',
    '22222222-2222-2222-2222-222222222222',
    'Anna salary on last day',
    'last_day_of_month',
    null,
    1,
    true,
    '2026-03-15T09:16:00+03:00',
    '2026-03-15T09:16:00+03:00'
),
(
    '55555555-5555-5555-5555-555555555553',
    '33333333-3333-3333-3333-333333333333',
    'Ivan salary on 5th',
    'fixed_day_of_month',
    5,
    1,
    true,
    '2026-03-15T09:17:00+03:00',
    '2026-03-15T09:17:00+03:00'
),
(
    '55555555-5555-5555-5555-555555555554',
    '33333333-3333-3333-3333-333333333333',
    'Ivan salary on 25th',
    'fixed_day_of_month',
    25,
    1,
    true,
    '2026-03-15T09:18:00+03:00',
    '2026-03-15T09:18:00+03:00'
)
on conflict (id) do update
set member_id = excluded.member_id,
    label = excluded.label,
    schedule_type = excluded.schedule_type,
    day_of_month = excluded.day_of_month,
    trigger_delay_days = excluded.trigger_delay_days,
    is_active = excluded.is_active,
    updated_at = excluded.updated_at;
