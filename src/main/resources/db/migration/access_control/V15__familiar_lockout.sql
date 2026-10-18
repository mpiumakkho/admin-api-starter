-- Wrong answers counted apart for where the person signed in from before and for anywhere else (P-035), each count
-- starting again once it was left alone for a while (access-control.lockout.reset-after)
alter table user_credentials
    add column last_failed_at          timestamptz,
    add column familiar_failed_attempts int not null default 0,
    add column familiar_locked_until    timestamptz,
    add column familiar_last_failed_at  timestamptz;
