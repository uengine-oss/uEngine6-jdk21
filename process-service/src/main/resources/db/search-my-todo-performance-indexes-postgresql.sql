create index if not exists idx_bpm_worklist_process_instance
    on bpm_worklist (process_instance_inst_id);

create index if not exists idx_bpm_worklist_endpoint
    on bpm_worklist (endpoint);

create index if not exists idx_bpm_worklist_scope
    on bpm_worklist (scope);

create index if not exists idx_bpm_worklist_status_start
    on bpm_worklist (status, start_date, task_id);

create index if not exists idx_bpm_procinst_corr_key
    on bpm_procinst (corr_key);
