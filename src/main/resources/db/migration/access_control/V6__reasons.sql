-- Why a change was made, as the person gave it (P-021): the activity log keeps it with the entry
alter table audit_logs add column reason varchar(500);

-- The changes that must have a reason; suspending or reactivating someone and deleting people on a new installation
alter table admin_settings add column reasons_required text[] not null default '{status,delete}';
