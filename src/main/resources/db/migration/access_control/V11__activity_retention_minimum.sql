-- At least 90 days of activity (P-026), as Thailand's Computer-Related Crime Act, section 26, has service providers
-- keep traffic data; an installation that kept less now keeps 90
update admin_settings set activity_retention_days = 90 where activity_retention_days < 90;
alter table admin_settings
    drop constraint ck_admin_settings_activity_retention_days,
    add constraint ck_admin_settings_activity_retention_days check (activity_retention_days between 90 and 3650);
