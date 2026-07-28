-- Review with DBA before applying. This file is not executed automatically.
create index if not exists idx_bpm_worklist_org_running_status_start
    on bpm_worklist (status, start_date, task_id);

create index if not exists idx_bpm_worklist_org_running_assign_group
    on bpm_worklist (assign_group);

create index if not exists idx_bpm_worklist_org_running_scope
    on bpm_worklist (scope);

create index if not exists idx_bpm_procinst_org_running_request_org
    on bpm_procinst (init_com_cd, started_date, inst_id);
