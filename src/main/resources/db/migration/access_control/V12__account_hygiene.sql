-- Suspend accounts nobody used for this many days, every night (P-027); 0 leaves them
alter table admin_settings
    add column suspend_inactive_days int not null default 0,
    add constraint ck_admin_settings_suspend_inactive_days check (suspend_inactive_days between 0 and 3650);
