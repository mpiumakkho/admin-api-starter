-- When each session last proved who is signed in (P-023): its sign-in, or a password confirmed since. Refreshing does
-- not count. Sessions from before count from their sign-in.
alter table user_sessions add column verified_at timestamptz;
update user_sessions set verified_at = created_at;
alter table user_sessions alter column verified_at set not null;

-- How long that lets someone make the risky changes before the password is asked again; 0 never asks
alter table admin_settings
    add column reauth_minutes int not null default 10,
    add constraint ck_admin_settings_reauth_minutes check (reauth_minutes between 0 and 1440);
