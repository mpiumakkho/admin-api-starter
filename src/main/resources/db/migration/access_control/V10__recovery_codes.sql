-- Recovery codes for two-step sign-in (P-025): ten per person, each used once, kept only as hashes
create table recovery_codes (
    id         uuid primary key,
    user_id    uuid        not null,
    code_hash  text        not null,
    created_at timestamptz not null,
    used_at    timestamptz,
    constraint fk_recovery_codes_user foreign key (user_id) references users (id) on delete cascade
);
create index ix_recovery_codes_user_id on recovery_codes (user_id);
