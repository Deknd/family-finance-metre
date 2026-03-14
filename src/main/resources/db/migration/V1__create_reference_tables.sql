create table families (
    id uuid primary key,
    name varchar(255) not null,
    timezone varchar(64) not null default 'Europe/Moscow',
    currency_code varchar(3) not null default 'RUB',
    status varchar(32) not null default 'active',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_families_status check (status in ('active', 'archived'))
);

create table family_members (
    id uuid primary key,
    family_id uuid not null,
    first_name varchar(255) not null,
    last_name varchar(255),
    display_name varchar(255),
    telegram_chat_id varchar(64),
    telegram_username varchar(255),
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint fk_family_members_family_id
        foreign key (family_id) references families (id)
);

create table devices (
    id uuid primary key,
    family_id uuid not null,
    name varchar(255) not null,
    device_token_hash varchar(255) not null,
    status varchar(32) not null default 'active',
    last_seen_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint chk_devices_status check (status in ('active', 'disabled')),
    constraint fk_devices_family_id
        foreign key (family_id) references families (id)
);

create index idx_family_members_family_id on family_members (family_id);

create unique index uq_family_members_telegram_chat_id_not_null
    on family_members (telegram_chat_id)
    where telegram_chat_id is not null;

create index idx_devices_family_id on devices (family_id);

create unique index uq_devices_device_token_hash on devices (device_token_hash);
