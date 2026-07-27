create index if not exists idx_bpm_procinst_corr_key_status
    on bpm_procinst (corr_key, status);

create index if not exists idx_bpm_worklist_root_status_end
    on bpm_worklist (root_inst_id, status, end_date);
