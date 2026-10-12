-- The language of each person's emails (P-029): the one they last used the app in, or null until then, for the
-- settings' default_language
alter table users
    add column language text,
    add constraint ck_users_language check (language in ('th', 'en'));

alter table admin_settings
    add column default_language text not null default 'th',
    add constraint ck_admin_settings_default_language check (default_language in ('th', 'en'));
