-- Roles nobody may hold at the same time (P-022): NIST RBAC's static separation of duty
create table role_conflicts (
    id          uuid primary key,
    name        varchar(100) not null,
    description varchar(500) not null default '',
    created_at  timestamptz  not null,
    updated_at  timestamptz  not null
);

-- A deleted role comes off the rules; RoleConflictService drops a rule left with fewer than two
create table role_conflict_roles (
    conflict_id uuid not null,
    role_id     uuid not null,
    primary key (conflict_id, role_id),
    constraint fk_role_conflict_roles_conflict foreign key (conflict_id) references role_conflicts (id) on delete cascade,
    constraint fk_role_conflict_roles_role foreign key (role_id) references roles (id) on delete cascade
);
create index ix_role_conflict_roles_role_id on role_conflict_roles (role_id);
