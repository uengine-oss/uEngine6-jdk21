-- Apply once when deploying the resolutionContext-only assignment contract.
-- PostgreSQL drops indexes that depend on the column automatically.
alter table bpm_worklist drop column if exists assign_group;
