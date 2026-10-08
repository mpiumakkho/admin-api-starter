-- Passwords people used before, so the settings' passwordHistory can refuse them again (P-017). Only the Argon2id
-- hash, at most 24 per user (CredentialServiceImpl drops the oldest), deleted with the user.
create table previous_passwords (
    id            uuid primary key,
    user_id       uuid        not null,
    password_hash text        not null,
    -- When it stopped being the user's password
    created_at    timestamptz not null,
    constraint fk_previous_passwords_user foreign key (user_id) references users (id) on delete cascade
);
create index ix_previous_passwords_user_id on previous_passwords (user_id);
