-- Branches (sites, offices): people belong to one or none, and a role can be given for one branch only (P-020)
create table branches (
    id         uuid primary key,
    -- Kept upper-case by BranchManagementService; unique ignoring case
    code       varchar(10)  not null,
    name       varchar(100) not null,
    active     boolean      not null default true,
    created_at timestamptz  not null,
    updated_at timestamptz  not null
);
create unique index ux_branches_code on branches (upper(code));

-- The branch someone belongs to, or none. A branch anyone belongs to cannot be deleted (branch_in_use)
alter table users add column branch_id uuid;
alter table users add constraint fk_users_branch foreign key (branch_id) references branches (id);
create index ix_users_branch_id on users (branch_id);

-- Null: the whole organization; a branch limits the role to it. Deleting a branch takes its ended assignments with it
alter table role_assignments add column branch_id uuid;
alter table role_assignments
    add constraint fk_role_assignments_branch foreign key (branch_id) references branches (id) on delete cascade;
create index ix_role_assignments_branch_id on role_assignments (branch_id);

-- Branches are off on a new installation; turning them off hides them and keeps the data
alter table admin_settings add column branches_enabled boolean not null default false;
