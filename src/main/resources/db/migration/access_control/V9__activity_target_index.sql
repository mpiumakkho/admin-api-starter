-- A branch administrator reads the entries about their branch's people (P-024)
create index ix_audit_logs_target_id on audit_logs (target_id);
