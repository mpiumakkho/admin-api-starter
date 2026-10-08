-- User management: who people are, what their roles allow, how they sign in, and what they did.
-- Flyway runs this in schema access_control. Business tables of the project go in schema app (db/migration/app),
-- and refer to users by id only, without foreign keys into this schema.

-- authorization ------------------------------------------------------------------------------------------------

-- Synced from the PermissionCatalog beans on every start; never written by the dashboard
create table permissions (
    id          uuid primary key,
    code        text    not null,
    resource    text    not null,
    action      text    not null,
    description text    not null default '',
    position    int     not null,
    deprecated  boolean not null default false,
    constraint ux_permissions_code unique (code)
);

create table roles (
    id          uuid primary key,
    -- Set for roles a RoleTemplate created, so code can find them; null for roles made on the dashboard
    code        text,
    name        text        not null,
    description text        not null default '',
    tone        text        not null,
    is_system   boolean     not null default false,
    created_at  timestamptz not null,
    updated_at  timestamptz not null,
    constraint ux_roles_code unique (code)
);

create table role_permissions (
    role_id         uuid not null,
    permission_code text not null,
    primary key (role_id, permission_code),
    constraint fk_role_permissions_role foreign key (role_id) references roles (id) on delete cascade,
    constraint fk_role_permissions_permission foreign key (permission_code) references permissions (code)
);

-- identity -----------------------------------------------------------------------------------------------------

create table users (
    id             uuid primary key,
    first_name     text        not null,
    last_name      text        not null,
    email          text        not null,
    phone          text        not null default '',
    department     text        not null,
    avatar         text        not null default '',
    role_id        uuid        not null,
    status         text        not null,
    created_at     timestamptz not null,
    updated_at     timestamptz not null,
    last_active_at timestamptz,
    -- A role that people hold cannot be deleted: RoleManagementService answers role_in_use
    constraint fk_users_role foreign key (role_id) references roles (id),
    constraint ck_users_status check (status in ('ACTIVE', 'INVITED', 'SUSPENDED'))
);
create unique index ux_users_email on users (lower(email));
create index ix_users_role_id on users (role_id);

create table user_credentials (
    user_id                   uuid primary key,
    password_hash             text,
    password_changed_at       timestamptz,
    failed_attempts           int not null default 0,
    locked_until              timestamptz,
    two_factor_secret         text,
    pending_two_factor_secret text,
    constraint fk_user_credentials_user foreign key (user_id) references users (id) on delete cascade
);

-- authentication -----------------------------------------------------------------------------------------------

create table user_sessions (
    id           uuid primary key,
    user_id      uuid        not null,
    created_at   timestamptz not null,
    last_seen_at timestamptz not null,
    expires_at   timestamptz not null,
    remember     boolean     not null,
    ip           text        not null,
    browser      text        not null,
    os           text        not null,
    ended_at     timestamptz,
    constraint fk_user_sessions_user foreign key (user_id) references users (id) on delete cascade
);
create index ix_user_sessions_user_id on user_sessions (user_id);

create table refresh_tokens (
    id          uuid primary key,
    session_id  uuid        not null,
    token_hash  text        not null,
    issued_at   timestamptz not null,
    expires_at  timestamptz not null,
    replaced_at timestamptz,
    constraint ux_refresh_tokens_token_hash unique (token_hash),
    constraint fk_refresh_tokens_session foreign key (session_id) references user_sessions (id) on delete cascade
);
create index ix_refresh_tokens_session_id on refresh_tokens (session_id);

create table action_tokens (
    id         uuid primary key,
    user_id    uuid        not null,
    type       text        not null,
    token_hash text        not null,
    expires_at timestamptz not null,
    used_at    timestamptz,
    created_at timestamptz not null,
    constraint ux_action_tokens_token_hash unique (token_hash),
    constraint fk_action_tokens_user foreign key (user_id) references users (id) on delete cascade,
    constraint ck_action_tokens_type check (type in ('INVITE', 'PASSWORD_RESET'))
);
create index ix_action_tokens_user_id on action_tokens (user_id);

-- audit --------------------------------------------------------------------------------------------------------

-- Append-only. No foreign keys: entries outlive the people and roles they name.
create table audit_logs (
    id           uuid primary key,
    occurred_at  timestamptz not null,
    actor_id     uuid        not null,
    action       text        not null,
    target_label text        not null,
    target_id    text,
    changes      jsonb,
    ip           text        not null,
    browser      text        not null,
    os           text        not null,
    request_id   text        not null
);
create index ix_audit_logs_occurred_at on audit_logs (occurred_at desc);

-- settings -----------------------------------------------------------------------------------------------------

create table admin_settings (
    id                      smallint primary key,
    -- The default role cannot be deleted: RoleManagementService answers default_role
    default_role_id         uuid        not null,
    allowed_domains         text[]      not null default '{}',
    invite_expiry_days      int         not null,
    session_timeout_minutes int         not null,
    updated_at              timestamptz not null,
    constraint ck_admin_settings_single_row check (id = 1),
    constraint fk_admin_settings_default_role foreign key (default_role_id) references roles (id)
);
