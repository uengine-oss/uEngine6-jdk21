CREATE TABLE IF NOT EXISTS bpm_dim_date (
    date_key integer PRIMARY KEY,
    calendar_date date,
    year_number integer,
    quarter_number integer,
    month_number integer,
    day_number integer,
    week_of_year integer,
    day_of_week integer,
    weekend boolean
);

CREATE TABLE IF NOT EXISTS bpm_dim_process_def (
    process_key varchar(32) PRIMARY KEY,
    definition_id varchar(255),
    definition_version_id varchar(255),
    definition_name varchar(255),
    definition_path varchar(255)
);

CREATE TABLE IF NOT EXISTS bpm_dim_activity (
    activity_key varchar(32) PRIMARY KEY,
    process_key varchar(32),
    tracing_tag varchar(255),
    absolute_tracing_tag varchar(255),
    activity_name varchar(255),
    activity_type varchar(255),
    tool varchar(255)
);

CREATE TABLE IF NOT EXISTS bpm_dim_actor (
    actor_key varchar(32) PRIMARY KEY,
    endpoint varchar(255),
    resource_name varchar(255),
    group_code varchar(255),
    role_name varchar(255)
);

CREATE TABLE IF NOT EXISTS bpm_fact_proc_inst (
    process_instance_id bigint PRIMARY KEY,
    root_process_instance_id bigint,
    process_key varchar(32),
    start_date_key integer,
    end_date_key integer,
    started_at timestamp with time zone,
    finished_at timestamp with time zone,
    duration_seconds bigint,
    status varchar(255),
    deleted boolean,
    subprocess boolean,
    event_handler boolean,
    initiator varchar(255),
    initiator_group_code varchar(255),
    total_task_count integer,
    completed_task_count integer,
    active_task_count integer,
    cancelled_task_count integer,
    human_task_count integer,
    automated_task_count integer,
    rework_task_count integer,
    source_updated_at timestamp with time zone
);

CREATE TABLE IF NOT EXISTS bpm_fact_task (
    task_id bigint PRIMARY KEY,
    process_instance_id bigint,
    root_process_instance_id bigint,
    process_key varchar(32),
    activity_key varchar(32),
    actor_key varchar(32),
    start_date_key integer,
    end_date_key integer,
    started_at timestamp with time zone,
    finished_at timestamp with time zone,
    duration_seconds bigint,
    wait_from_previous_seconds bigint,
    lead_from_process_seconds bigint,
    status varchar(255),
    decision varchar(255),
    decision_reason text,
    priority integer,
    delegated boolean,
    human_task boolean,
    automated_task boolean,
    rework_task boolean,
    source_updated_at timestamp with time zone
);

CREATE TABLE IF NOT EXISTS bpm_kpi_target (
    target_id varchar(64) PRIMARY KEY,
    period_type varchar(32) NOT NULL,
    period_start date NOT NULL,
    period_end date NOT NULL,
    total_target integer NOT NULL,
    created_at timestamp with time zone DEFAULT current_timestamp,
    updated_at timestamp with time zone DEFAULT current_timestamp
);

CREATE TABLE IF NOT EXISTS bpm_kpi_process_state (
    process_key varchar(32) PRIMARY KEY,
    definition_path varchar(255) NOT NULL,
    process_name varchar(255) NOT NULL,
    domain_id varchar(64) NOT NULL,
    domain_name varchar(255) NOT NULL,
    lifecycle_stage varchar(32) NOT NULL,
    deployed_at timestamp with time zone,
    updated_at timestamp with time zone DEFAULT current_timestamp
);

CREATE INDEX IF NOT EXISTS idx_fact_proc_def ON bpm_fact_proc_inst(process_key);
CREATE INDEX IF NOT EXISTS idx_fact_proc_started ON bpm_fact_proc_inst(started_at);
CREATE INDEX IF NOT EXISTS idx_fact_task_inst ON bpm_fact_task(process_instance_id);
CREATE INDEX IF NOT EXISTS idx_fact_task_activity ON bpm_fact_task(activity_key);
CREATE INDEX IF NOT EXISTS idx_fact_task_actor ON bpm_fact_task(actor_key);
CREATE INDEX IF NOT EXISTS idx_fact_task_started ON bpm_fact_task(started_at);
CREATE INDEX IF NOT EXISTS idx_kpi_process_stage ON bpm_kpi_process_state(lifecycle_stage);
CREATE INDEX IF NOT EXISTS idx_kpi_process_domain ON bpm_kpi_process_state(domain_id);
CREATE INDEX IF NOT EXISTS idx_kpi_process_deployed ON bpm_kpi_process_state(deployed_at);
