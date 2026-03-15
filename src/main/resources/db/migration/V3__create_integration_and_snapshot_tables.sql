create table llm_collection_requests (
    id uuid primary key,
    request_id varchar(255) not null,
    family_id uuid not null,
    member_id uuid not null,
    payroll_schedule_id uuid not null,
    period_year int not null,
    period_month smallint not null,
    reason varchar(64) not null,
    status varchar(32) not null,
    requested_fields jsonb not null,
    nominal_payroll_date date not null,
    effective_payroll_date date not null,
    scheduled_trigger_date date not null,
    triggered_at timestamptz not null,
    accepted_at timestamptz,
    completed_at timestamptz,
    workflow_run_id varchar(255),
    request_payload jsonb not null,
    response_payload jsonb,
    error_message text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint fk_llm_collection_requests_family_id
        foreign key (family_id) references families (id),
    constraint fk_llm_collection_requests_member_id
        foreign key (member_id) references family_members (id),
    constraint fk_llm_collection_requests_payroll_schedule_id
        foreign key (payroll_schedule_id) references member_payroll_schedules (id),
    constraint uq_llm_collection_requests_request_id unique (request_id),
    constraint chk_llm_collection_requests_period_month
        check (period_month between 1 and 12),
    constraint chk_llm_collection_requests_status
        check (status in ('pending', 'accepted', 'completed', 'failed', 'cancelled'))
);

create index idx_llm_collection_requests_member_period
    on llm_collection_requests (member_id, period_year, period_month);

create index idx_llm_requests_schedule_effective_date
    on llm_collection_requests (payroll_schedule_id, effective_payroll_date);

create index idx_llm_collection_requests_status
    on llm_collection_requests (status);

create unique index uq_llm_requests_schedule_effective_date
    on llm_collection_requests (payroll_schedule_id, effective_payroll_date);

create table finance_submissions (
    id uuid primary key,
    external_submission_id varchar(255) not null,
    request_id varchar(255),
    llm_collection_request_id uuid,
    family_id uuid not null,
    member_id uuid not null,
    source varchar(32) not null,
    period_year int not null,
    period_month smallint not null,
    collected_at timestamptz not null,
    monthly_income integer not null,
    monthly_expenses integer not null,
    monthly_credit_payments integer not null,
    liquid_savings integer not null,
    confidence varchar(16),
    notes text,
    raw_payload jsonb not null,
    created_at timestamptz not null default now(),
    constraint fk_finance_submissions_llm_collection_request_id
        foreign key (llm_collection_request_id) references llm_collection_requests (id),
    constraint fk_finance_submissions_family_id
        foreign key (family_id) references families (id),
    constraint fk_finance_submissions_member_id
        foreign key (member_id) references family_members (id),
    constraint uq_finance_submissions_external_submission_id unique (external_submission_id),
    constraint chk_finance_submissions_period_month
        check (period_month between 1 and 12),
    constraint chk_finance_submissions_source
        check (source in ('telegram')),
    constraint chk_finance_submissions_confidence
        check (confidence in ('low', 'medium', 'high') or confidence is null),
    constraint chk_finance_submissions_monthly_income
        check (monthly_income >= 0),
    constraint chk_finance_submissions_monthly_expenses
        check (monthly_expenses >= 0),
    constraint chk_finance_submissions_monthly_credit_payments
        check (monthly_credit_payments >= 0),
    constraint chk_finance_submissions_liquid_savings
        check (liquid_savings >= 0)
);

create index idx_finance_submissions_member_period_collected_at
    on finance_submissions (member_id, period_year, period_month, collected_at desc);

create index idx_finance_submissions_family_period
    on finance_submissions (family_id, period_year, period_month);

create index idx_finance_submissions_request_id
    on finance_submissions (request_id);

create table member_finance_snapshots (
    id uuid primary key,
    family_id uuid not null,
    member_id uuid not null,
    period_year int not null,
    period_month smallint not null,
    source_submission_id uuid not null,
    monthly_income integer not null,
    monthly_expenses integer not null,
    monthly_credit_payments integer not null,
    liquid_savings integer not null,
    collected_at timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint fk_member_finance_snapshots_family_id
        foreign key (family_id) references families (id),
    constraint fk_member_finance_snapshots_member_id
        foreign key (member_id) references family_members (id),
    constraint fk_member_finance_snapshots_source_submission_id
        foreign key (source_submission_id) references finance_submissions (id),
    constraint chk_member_finance_snapshots_period_month
        check (period_month between 1 and 12),
    constraint chk_member_finance_snapshots_monthly_income
        check (monthly_income >= 0),
    constraint chk_member_finance_snapshots_monthly_expenses
        check (monthly_expenses >= 0),
    constraint chk_member_finance_snapshots_monthly_credit_payments
        check (monthly_credit_payments >= 0),
    constraint chk_member_finance_snapshots_liquid_savings
        check (liquid_savings >= 0)
);

create unique index uq_member_finance_snapshots_member_period
    on member_finance_snapshots (member_id, period_year, period_month);

create table family_dashboard_snapshots (
    id uuid primary key,
    family_id uuid not null,
    period_year int not null,
    period_month smallint not null,
    status varchar(32) not null,
    status_text varchar(64) not null,
    status_reason varchar(255) not null,
    monthly_income integer not null,
    monthly_expenses integer not null,
    credit_load_percent numeric(5, 2) not null,
    emergency_fund_months numeric(8, 2) not null,
    member_count_used integer not null,
    calculated_at timestamptz not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint fk_family_dashboard_snapshots_family_id
        foreign key (family_id) references families (id),
    constraint chk_family_dashboard_snapshots_period_month
        check (period_month between 1 and 12),
    constraint chk_family_dashboard_snapshots_status
        check (status in ('normal', 'warning', 'risk')),
    constraint chk_family_dashboard_snapshots_member_count_used
        check (member_count_used >= 0)
);

create unique index uq_family_dashboard_snapshots_family_period
    on family_dashboard_snapshots (family_id, period_year, period_month);
