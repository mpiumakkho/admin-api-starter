-- Several roles per user, each given from a start until an end or for good (P-019, replacing users.role_id of P-003).
-- An assignment counts while starts_at <= now < ends_at; ended ones stay, to be renewed, until the activity log
-- forgets them. RoleAssignmentExpiryJob sets expiry_logged_at once it has logged the end.
create table role_assignments (
    id               uuid primary key,
    user_id          uuid         not null,
    role_id          uuid         not null,
    starts_at        timestamptz  not null,
    ends_at          timestamptz,
    reason           varchar(500) not null default '',
    assigned_by      uuid,
    assigned_at      timestamptz  not null,
    expiry_logged_at timestamptz,
    constraint fk_role_assignments_user foreign key (user_id) references users (id) on delete cascade,
    -- A role in use cannot be deleted (RoleManagementService answers role_in_use); its ended assignments go with it
    constraint fk_role_assignments_role foreign key (role_id) references roles (id) on delete cascade,
    constraint fk_role_assignments_assigned_by foreign key (assigned_by) references users (id) on delete set null,
    constraint ck_role_assignments_period check (ends_at is null or ends_at > starts_at)
);
create index ix_role_assignments_user_id on role_assignments (user_id);
create index ix_role_assignments_role_id on role_assignments (role_id);
-- What the expiry job looks for
create index ix_role_assignments_unlogged_end on role_assignments (ends_at) where ends_at is not null and expiry_logged_at is null;

-- Everyone keeps the role they had, for good, from when they joined
insert into role_assignments (id, user_id, role_id, starts_at, ends_at, reason, assigned_by, assigned_at)
select gen_random_uuid(), id, role_id, created_at, null, '', null, created_at from users;

alter table users drop constraint fk_users_role;
drop index ix_users_role_id;
alter table users drop column role_id;

-- Each role's own rules for giving it, as Microsoft Entra PIM's role settings: an end within so many days, a reason
alter table roles
    add column assignment_max_days        int,
    add column assignment_reason_required boolean not null default false,
    add constraint ck_roles_assignment_max_days check (assignment_max_days is null or assignment_max_days between 1 and 365);

-- What the server does by itself, such as ending an assignment on time, has no actor
alter table audit_logs alter column actor_id drop not null;
