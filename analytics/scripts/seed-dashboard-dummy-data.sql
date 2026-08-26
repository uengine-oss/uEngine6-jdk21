\set ON_ERROR_STOP on

-- Heatmap cells do not need a separate surrogate ID. BPM_FACT_TASK.TASK_ID
-- identifies each event; ACTIVITY_KEY/ACTOR_KEY and STARTED_AT are the axes.

\if :{?schema}
\else
  \set schema public
\endif
\if :{?dummy_days}
\else
  \set dummy_days 90
\endif
\if :{?processes_per_day}
\else
  \set processes_per_day 20
\endif
\if :{?tasks_per_process}
\else
  \set tasks_per_process 6
\endif
\if :{?end_date}
\else
  SELECT CURRENT_DATE::text AS end_date \gset
\endif
\if :{?time_zone}
\else
  \set time_zone Asia/Seoul
\endif
\if :{?random_seed}
\else
  \set random_seed 0.4242
\endif
\if :{?cleanup}
\else
  \set cleanup false
\endif

SET search_path TO :"schema";

CREATE TABLE IF NOT EXISTS bpm_kpi_target (
    target_id varchar(64) PRIMARY KEY,
    period_type varchar(32) NOT NULL,
    period_start date NOT NULL,
    period_end date NOT NULL,
    total_target integer NOT NULL,
    created_at timestamp with time zone DEFAULT clock_timestamp(),
    updated_at timestamp with time zone DEFAULT clock_timestamp()
);

CREATE TABLE IF NOT EXISTS bpm_kpi_process_state (
    process_key varchar(32) PRIMARY KEY,
    definition_path varchar(255) NOT NULL,
    process_name varchar(255) NOT NULL,
    domain_id varchar(64) NOT NULL,
    domain_name varchar(255) NOT NULL,
    lifecycle_stage varchar(32) NOT NULL,
    deployed_at timestamp with time zone,
    updated_at timestamp with time zone DEFAULT clock_timestamp()
);

SELECT :dummy_days::integer BETWEEN 1 AND 3650
       AND :processes_per_day::integer BETWEEN 1 AND 10000
       AND :tasks_per_process::integer BETWEEN 1 AND 100
       AND :random_seed::double precision BETWEEN -1 AND 1 AS parameters_valid
\gset

\if :parameters_valid
\else
  \echo 'Invalid parameters: days=1..3650, processes/day=1..10000, tasks/process=1..100, seed=-1..1'
  \quit 2
\endif

SELECT EXISTS (
    SELECT 1 FROM pg_timezone_names WHERE name = :'time_zone'
) AS time_zone_valid
\gset

\if :time_zone_valid
\else
  \echo 'Invalid PostgreSQL time zone:' :time_zone
  \quit 2
\endif

\if :cleanup
BEGIN;
SELECT pg_advisory_xact_lock(hashtext('uengine.analytics.dummy-data'));

DELETE FROM bpm_fact_task
 WHERE left(process_key, 6) = 'dummy_';
DELETE FROM bpm_fact_proc_inst
 WHERE left(process_key, 6) = 'dummy_';
DELETE FROM bpm_dim_activity
 WHERE left(process_key, 6) = 'dummy_';
DELETE FROM bpm_dim_process_def
 WHERE left(process_key, 6) = 'dummy_';
DELETE FROM bpm_dim_actor
 WHERE left(actor_key, 12) = 'dummy_actor_';
DELETE FROM bpm_kpi_process_state
 WHERE left(process_key, 6) = 'dummy_';
DELETE FROM bpm_kpi_target
 WHERE left(target_id, 6) = 'dummy_';

COMMIT;
\echo 'Analytics dummy data removed.'
\else
BEGIN;
SELECT pg_advisory_xact_lock(hashtext('uengine.analytics.dummy-data'));
SELECT setseed(:random_seed::double precision);

CREATE TEMP TABLE _dummy_process_catalog (
    process_no integer PRIMARY KEY,
    process_key varchar(32) NOT NULL,
    definition_id varchar(255) NOT NULL,
    definition_name varchar(255) NOT NULL,
    domain_id varchar(64) NOT NULL,
    domain_name varchar(255) NOT NULL,
    lifecycle_stage varchar(32) NOT NULL
) ON COMMIT DROP;

INSERT INTO _dummy_process_catalog(
    process_no, process_key, definition_id, definition_name,
    domain_id, domain_name, lifecycle_stage
)
VALUES
    (1, 'dummy_card_issue', 'CorporateCardIssue/CorporateCardIssue',
        'Corporate Card Issue', 'cards', '카드', 'published'),
    (2, 'dummy_corp_loan', 'Integrated_CorporateLoan/Integrated_CorporateLoan',
        'Integrated Corporate Loan', 'lending', '여신', 'review'),
    (3, 'dummy_deposit_notice', 'DepositBalanceNotice/DepositBalanceNotice',
        'Deposit Balance Notice', 'deposits', '수신', 'published'),
    (4, 'dummy_export_purchase', 'ExportBillPurchase/ExportBillPurchase_Level2',
        'Export Bill Purchase Level 2', 'trade', '외환', 'published'),
    (5, 'dummy_new_deposit', 'NewDepositAccount/NewDepositAccount',
        'New Deposit Account', 'deposits', '수신', 'draft'),
    (6, 'dummy_home_mortgage', 'HomeMortgageLoan/HomeMortgageLoan',
        'Home Mortgage Loan', 'lending', '여신', 'review'),
    (7, 'dummy_credit_review', 'callActivity/CreditReview_Sub',
        'Credit Review Sub Process', 'lending', '여신', 'published'),
    (8, 'dummy_export_settle', 'callActivity/ExportBillSettlement_Sub',
        'Export Bill Settlement Sub Process', 'trade', '외환', 'review'),
    (9, 'dummy_export_settle_l2', 'callActivity/ExportBillSettlement_Level2_Sub',
        'Export Bill Settlement Level 2 Sub Process', 'trade', '외환', 'draft');

INSERT INTO bpm_dim_process_def(
    process_key, definition_id, definition_version_id, definition_name, definition_path
)
SELECT process_key, definition_id, definition_id || '.v1', definition_name,
       definition_id || '.bpmn'
  FROM _dummy_process_catalog
ON CONFLICT (process_key) DO UPDATE
SET definition_id = EXCLUDED.definition_id,
    definition_version_id = EXCLUDED.definition_version_id,
    definition_name = EXCLUDED.definition_name,
    definition_path = EXCLUDED.definition_path;

INSERT INTO bpm_kpi_target(
    target_id, period_type, period_start, period_end, total_target, updated_at
)
VALUES (
    'dummy_current',
    'yearly',
    date_trunc('year', :'end_date'::date)::date,
    (date_trunc('year', :'end_date'::date) + interval '1 year - 1 day')::date,
    12,
    clock_timestamp()
)
ON CONFLICT (target_id) DO UPDATE
SET period_type = EXCLUDED.period_type,
    period_start = EXCLUDED.period_start,
    period_end = EXCLUDED.period_end,
    total_target = EXCLUDED.total_target,
    updated_at = EXCLUDED.updated_at;

INSERT INTO bpm_kpi_process_state(
    process_key, definition_path, process_name, domain_id, domain_name,
    lifecycle_stage, deployed_at, updated_at
)
SELECT process_key,
       definition_id,
       definition_name,
       domain_id,
       domain_name,
       lifecycle_stage,
       CASE WHEN lifecycle_stage = 'published'
            THEN :'end_date'::date
                 - ((process_no * 11) % 56) * interval '1 day'
            ELSE NULL
       END,
       clock_timestamp()
  FROM _dummy_process_catalog
ON CONFLICT (process_key) DO UPDATE
SET definition_path = EXCLUDED.definition_path,
    process_name = EXCLUDED.process_name,
    domain_id = EXCLUDED.domain_id,
    domain_name = EXCLUDED.domain_name,
    lifecycle_stage = EXCLUDED.lifecycle_stage,
    deployed_at = EXCLUDED.deployed_at,
    updated_at = EXCLUDED.updated_at;

INSERT INTO bpm_dim_actor(actor_key, endpoint, resource_name, group_code, role_name)
SELECT 'dummy_actor_' || lpad(actor_no::text, 2, '0'),
       'dummy.user' || lpad(actor_no::text, 2, '0'),
       'Dummy User ' || lpad(actor_no::text, 2, '0'),
       (ARRAY['영업점', '기업금융', '여신심사', '수신업무', '외환업무', '리스크관리'])[
           ((actor_no - 1) % 6) + 1
       ],
       (ARRAY['REQUESTER', 'APPROVER', 'REVIEWER'])[((actor_no - 1) % 3) + 1]
  FROM generate_series(1, 12) AS actor_no
ON CONFLICT (actor_key) DO UPDATE
SET endpoint = EXCLUDED.endpoint,
    resource_name = EXCLUDED.resource_name,
    group_code = EXCLUDED.group_code,
    role_name = EXCLUDED.role_name;

INSERT INTO bpm_dim_activity(
    activity_key, process_key, tracing_tag, absolute_tracing_tag,
    activity_name, activity_type, tool
)
SELECT 'dummy_p' || catalog.process_no || '_a' || step_no,
       catalog.process_key,
       step_no::text,
       catalog.process_no || '.' || step_no,
       (ARRAY['Submit', 'Validate', 'Review', 'Approve', 'Notify', 'Archive'])[
           ((step_no - 1) % 6) + 1
       ] || ' - ' || catalog.definition_name,
       CASE WHEN step_no % 3 = 0 THEN 'ServiceTask' ELSE 'HumanActivity' END,
       CASE WHEN step_no % 3 = 0 THEN 'dummy-automation' ELSE NULL END
  FROM _dummy_process_catalog AS catalog
 CROSS JOIN generate_series(1, :tasks_per_process::integer) AS step_no
ON CONFLICT (activity_key) DO UPDATE
SET process_key = EXCLUDED.process_key,
    tracing_tag = EXCLUDED.tracing_tag,
    absolute_tracing_tag = EXCLUDED.absolute_tracing_tag,
    activity_name = EXCLUDED.activity_name,
    activity_type = EXCLUDED.activity_type,
    tool = EXCLUDED.tool;

INSERT INTO bpm_dim_date(
    date_key, calendar_date, year_number, quarter_number, month_number,
    day_number, week_of_year, day_of_week, weekend
)
SELECT to_char(calendar_date, 'YYYYMMDD')::integer,
       calendar_date,
       extract(year FROM calendar_date)::integer,
       extract(quarter FROM calendar_date)::integer,
       extract(month FROM calendar_date)::integer,
       extract(day FROM calendar_date)::integer,
       to_char(calendar_date, 'IW')::integer,
       extract(isodow FROM calendar_date)::integer,
       extract(isodow FROM calendar_date) IN (6, 7)
  FROM generate_series(
           :'end_date'::date - (:dummy_days::integer - 1),
           :'end_date'::date + 7,
           interval '1 day'
       ) AS dates(calendar_date)
ON CONFLICT (date_key) DO UPDATE
SET calendar_date = EXCLUDED.calendar_date,
    year_number = EXCLUDED.year_number,
    quarter_number = EXCLUDED.quarter_number,
    month_number = EXCLUDED.month_number,
    day_number = EXCLUDED.day_number,
    week_of_year = EXCLUDED.week_of_year,
    day_of_week = EXCLUDED.day_of_week,
    weekend = EXCLUDED.weekend;

CREATE TEMP TABLE _dummy_processes ON COMMIT DROP AS
WITH id_limit AS (
    SELECT least(coalesce(min(process_instance_id), 0), 0) AS min_id
      FROM bpm_fact_proc_inst
), generated AS (
    SELECT dates.calendar_date::date AS calendar_date,
           slots.slot_no,
           catalog.process_no,
           catalog.process_key,
           random() AS status_roll,
           random() AS hour_roll,
           random() AS minute_roll,
           (1800 + floor(random() * 257400))::bigint AS planned_duration_seconds
      FROM generate_series(
               :'end_date'::date - (:dummy_days::integer - 1),
               :'end_date'::date,
               interval '1 day'
           ) AS dates(calendar_date)
     CROSS JOIN generate_series(1, :processes_per_day::integer) AS slots(slot_no)
      JOIN _dummy_process_catalog AS catalog
        ON catalog.process_no = (
            (extract(doy FROM dates.calendar_date)::integer + slots.slot_no - 2)
                % (SELECT count(*) FROM _dummy_process_catalog)
        ) + 1
), classified AS (
    SELECT generated.*,
           CASE
               WHEN status_roll < 0.70 THEN 'COMPLETED'
               WHEN status_roll < 0.84 THEN 'RUNNING'
               WHEN status_roll < 0.94 THEN 'NEW'
               ELSE 'CANCELLED'
           END AS status,
           (calendar_date::timestamp
               + make_interval(
                   hours => 7 + floor(hour_roll * 13)::integer,
                   mins => floor(minute_roll * 60)::integer
                 )) AT TIME ZONE :'time_zone' AS started_at
      FROM generated
)
SELECT id_limit.min_id
           - row_number() OVER (ORDER BY classified.calendar_date, classified.slot_no) AS process_instance_id,
       classified.*
  FROM classified
 CROSS JOIN id_limit;

CREATE TEMP TABLE _dummy_tasks ON COMMIT DROP AS
WITH id_limit AS (
    SELECT least(coalesce(min(task_id), 0), 0) AS min_id
      FROM bpm_fact_task
), generated AS (
    SELECT process.*,
           steps.step_no,
           process.started_at
               + ((process.planned_duration_seconds * (steps.step_no - 1)
                    / :tasks_per_process::integer) * interval '1 second') AS task_started_at,
           (300 + floor(random() * 6900))::bigint AS task_duration_seconds,
           (floor(random() * 3600))::bigint AS wait_seconds,
           random() < 0.08 AS rework_task,
           steps.step_no % 3 <> 0 AS human_task,
           CASE
               WHEN process.status = 'COMPLETED' THEN 'COMPLETED'
               WHEN process.status = 'CANCELLED'
                   THEN CASE WHEN steps.step_no = :tasks_per_process::integer
                             THEN 'CANCELLED' ELSE 'COMPLETED' END
               WHEN process.status = 'RUNNING'
                   THEN CASE WHEN steps.step_no < :tasks_per_process::integer
                             THEN 'COMPLETED' ELSE 'RUNNING' END
               ELSE CASE WHEN steps.step_no = 1 THEN 'NEW' ELSE 'READY' END
           END AS task_status
      FROM _dummy_processes AS process
     CROSS JOIN generate_series(1, :tasks_per_process::integer) AS steps(step_no)
), identified AS (
    SELECT id_limit.min_id
               - row_number() OVER (ORDER BY generated.process_instance_id DESC, generated.step_no) AS task_id,
           generated.*
      FROM generated
     CROSS JOIN id_limit
)
SELECT identified.*,
       CASE WHEN task_status IN ('COMPLETED', 'CANCELLED')
            THEN task_started_at + task_duration_seconds * interval '1 second'
            ELSE NULL::timestamp with time zone
       END AS task_finished_at
  FROM identified;

INSERT INTO bpm_fact_task(
    task_id, process_instance_id, root_process_instance_id, process_key,
    activity_key, actor_key, start_date_key, end_date_key, started_at,
    finished_at, duration_seconds, wait_from_previous_seconds,
    lead_from_process_seconds, status, decision, decision_reason, priority,
    delegated, human_task, automated_task, rework_task, source_updated_at
)
SELECT task_id,
       process_instance_id,
       process_instance_id,
       process_key,
       'dummy_p' || process_no || '_a' || step_no,
       CASE WHEN human_task
            THEN 'dummy_actor_' || lpad((((slot_no + step_no - 2) % 12) + 1)::text, 2, '0')
            ELSE NULL
       END,
       to_char(task_started_at AT TIME ZONE :'time_zone', 'YYYYMMDD')::integer,
       CASE WHEN task_finished_at IS NULL THEN NULL
            ELSE to_char(task_finished_at AT TIME ZONE :'time_zone', 'YYYYMMDD')::integer
       END,
       task_started_at,
       task_finished_at,
       CASE WHEN task_finished_at IS NULL THEN NULL ELSE task_duration_seconds END,
       wait_seconds,
       CASE WHEN task_finished_at IS NULL THEN NULL
            ELSE extract(epoch FROM task_finished_at - started_at)::bigint
       END,
       task_status,
       CASE WHEN rework_task THEN 'RETURN'
            WHEN task_status = 'COMPLETED' THEN 'APPROVE'
            ELSE NULL
       END,
       CASE WHEN rework_task THEN 'Dummy rework case' ELSE NULL END,
       ((slot_no + step_no - 2) % 5) + 1,
       random() < 0.04,
       human_task,
       NOT human_task,
       rework_task,
       clock_timestamp()
  FROM _dummy_tasks;

INSERT INTO bpm_fact_proc_inst(
    process_instance_id, root_process_instance_id, process_key, start_date_key,
    end_date_key, started_at, finished_at, duration_seconds, status, deleted,
    subprocess, event_handler, initiator, initiator_group_code,
    total_task_count, completed_task_count, active_task_count,
    cancelled_task_count, human_task_count, automated_task_count,
    rework_task_count, source_updated_at
)
SELECT process.process_instance_id,
       process.process_instance_id,
       process.process_key,
       to_char(process.started_at AT TIME ZONE :'time_zone', 'YYYYMMDD')::integer,
       CASE WHEN process.status IN ('COMPLETED', 'CANCELLED')
            THEN to_char(
                (process.started_at + process.planned_duration_seconds * interval '1 second')
                    AT TIME ZONE :'time_zone',
                'YYYYMMDD'
            )::integer
            ELSE NULL
       END,
       process.started_at,
       CASE WHEN process.status IN ('COMPLETED', 'CANCELLED')
            THEN process.started_at + process.planned_duration_seconds * interval '1 second'
            ELSE NULL
       END,
       CASE WHEN process.status IN ('COMPLETED', 'CANCELLED')
            THEN process.planned_duration_seconds ELSE NULL
       END,
       process.status,
       false,
       false,
       false,
       'dummy.user' || lpad((((process.slot_no - 1) % 12) + 1)::text, 2, '0'),
       (ARRAY['영업점', '기업금융', '여신심사', '수신업무', '외환업무', '리스크관리'])[
           ((process.slot_no - 1) % 6) + 1
       ],
       count(task.*)::integer,
       count(*) FILTER (WHERE task.task_status = 'COMPLETED')::integer,
       count(*) FILTER (WHERE task.task_status IN ('NEW', 'READY', 'RUNNING'))::integer,
       count(*) FILTER (WHERE task.task_status = 'CANCELLED')::integer,
       count(*) FILTER (WHERE task.human_task)::integer,
       count(*) FILTER (WHERE NOT task.human_task)::integer,
       count(*) FILTER (WHERE task.rework_task)::integer,
       clock_timestamp()
  FROM _dummy_processes AS process
  JOIN _dummy_tasks AS task USING (process_instance_id)
 GROUP BY process.process_instance_id, process.process_key, process.started_at,
          process.planned_duration_seconds, process.status, process.slot_no;

SELECT count(*) AS inserted_processes,
       min(calendar_date) AS from_date,
       max(calendar_date) AS to_date
  FROM _dummy_processes;
SELECT count(*) AS inserted_tasks,
       count(*) FILTER (WHERE human_task) AS human_tasks,
       count(*) FILTER (WHERE rework_task) AS rework_tasks
  FROM _dummy_tasks;

COMMIT;
\echo 'Analytics dummy data committed. Run the script again to accumulate another batch.'
\endif
