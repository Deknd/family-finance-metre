create table member_payroll_schedules (
    id uuid primary key,
    member_id uuid not null,
    label varchar(255),
    schedule_type varchar(32) not null,
    day_of_month smallint,
    trigger_delay_days smallint not null default 1,
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint fk_member_payroll_schedules_member_id
        foreign key (member_id) references family_members (id),
    constraint chk_member_payroll_schedules_schedule_type
        check (schedule_type in ('fixed_day_of_month', 'last_day_of_month')),
    constraint chk_member_payroll_schedules_day_of_month
        check (
            (schedule_type = 'fixed_day_of_month' and day_of_month is not null and day_of_month between 1 and 31)
            or (schedule_type = 'last_day_of_month' and day_of_month is null)
        ),
    constraint chk_member_payroll_schedules_trigger_delay_days
        check (trigger_delay_days between 0 and 7)
);

create index idx_member_payroll_schedules_member_id
    on member_payroll_schedules (member_id);

create unique index uq_member_payroll_schedules_member_schedule_type_day_of_month
    on member_payroll_schedules (member_id, schedule_type, coalesce(day_of_month, 0));
