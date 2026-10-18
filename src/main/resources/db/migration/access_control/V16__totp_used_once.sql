-- The 30-second step of the last code from the app that was accepted, so that code is not accepted again (P-036)
alter table user_credentials
    add column last_totp_step bigint;
