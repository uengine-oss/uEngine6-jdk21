-- Loan BPM schema alignment: inbox identity/results, loan snapshot names, and delegation state.
-- Run once against PostgreSQL after the reviewed application code is deployed.

BEGIN;

CREATE SEQUENCE IF NOT EXISTS seq_bpm_event_inbox
    START WITH 1 INCREMENT BY 1 MINVALUE 1 NO CYCLE;

ALTER TABLE bpm_event_inbox
    ADD COLUMN IF NOT EXISTS prcr_rslt_code_nm VARCHAR(128),
    ADD COLUMN IF NOT EXISTS prcs_rslt_cntn TEXT;

ALTER SEQUENCE bpm_event_inbox_seq OWNED BY NONE;
ALTER SEQUENCE seq_bpm_event_inbox OWNED BY bpm_event_inbox.id;
ALTER TABLE bpm_event_inbox
    ALTER COLUMN id SET DEFAULT nextval('seq_bpm_event_inbox');

SELECT setval(
    'seq_bpm_event_inbox',
    GREATEST(COALESCE((SELECT MAX(id) FROM bpm_event_inbox), 1), 1),
    true
);

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'bpm_procinst' AND column_name = 'cust_no')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'bpm_procinst' AND column_name = 'cus_no') THEN
        ALTER TABLE bpm_procinst RENAME COLUMN cust_no TO cus_no;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'bpm_procinst' AND column_name = 'laon_hope_date')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'bpm_procinst' AND column_name = 'loan_hope_date') THEN
        ALTER TABLE bpm_procinst RENAME COLUMN laon_hope_date TO loan_hope_date;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'bpm_procinst' AND column_name = 'bsns_clsf')
       AND NOT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'bpm_procinst' AND column_name = 'bswr_clsf_code') THEN
        ALTER TABLE bpm_procinst RENAME COLUMN bsns_clsf TO bswr_clsf_code;
    END IF;
END $$;

UPDATE bpm_worklist
   SET delegated = FALSE
 WHERE delegated IS NULL;

ALTER TABLE bpm_worklist
    ALTER COLUMN delegated SET DEFAULT FALSE,
    ALTER COLUMN delegated SET NOT NULL;

COMMIT;
