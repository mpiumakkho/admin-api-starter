-- The account rules of the settings page (API.md, "Settings"): roles that must use two-step sign-in (P-016), the
-- password rules (P-017) and how long the activity log is kept (P-018). Existing installations get the defaults of
-- DEFAULT_SETTINGS in admin-ng-starter's catalog.ts.

alter table admin_settings
    -- Role ids without duplicates; no foreign key (arrays cannot have one): deleting a role takes it off the list
    add column two_factor_role_ids      uuid[] not null default '{}',
    add column password_min_length      int    not null default 8,
    add column password_history         int    not null default 0,
    add column activity_retention_days  int    not null default 730,
    add constraint ck_admin_settings_password_min_length check (password_min_length between 8 and 64),
    add constraint ck_admin_settings_password_history check (password_history between 0 and 24),
    add constraint ck_admin_settings_activity_retention_days check (activity_retention_days between 30 and 3650);
