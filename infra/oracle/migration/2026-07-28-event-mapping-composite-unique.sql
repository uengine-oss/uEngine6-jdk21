-- Preserve multiple BPM event mappings that share one event name.
-- Run as the BPM application schema owner before deploying the new application.
-- Consolidated migration: remove the legacy event-name UNIQUE and ensure the
-- composite target UNIQUE in one execution. Safe to rerun after either step.
-- Mapping rows, primary key and BPM_EVENT_INBOX are preserved.
-- Execute this entire file in script mode, including both slash terminators.

DECLARE
    constraint_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO constraint_count
      FROM user_constraints
     WHERE table_name = 'BPM_EVENT_MAPPING'
       AND constraint_name = 'UK_EVENT_MAPPING_EVENT_NAME';

    IF constraint_count > 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE BPM_EVENT_MAPPING DROP CONSTRAINT UK_EVENT_MAPPING_EVENT_NAME';
    END IF;
END;
/

DECLARE
    constraint_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO constraint_count
      FROM user_constraints
     WHERE table_name = 'BPM_EVENT_MAPPING'
       AND constraint_name = 'UK_EVENT_MAPPING_TARGET';

    IF constraint_count = 0 THEN
        EXECUTE IMMEDIATE
            'ALTER TABLE BPM_EVENT_MAPPING ADD CONSTRAINT UK_EVENT_MAPPING_TARGET ' ||
            'UNIQUE (EVENT_NAME, DEFINITION_ID, TRACING_TAG, IS_START_EVENT)';
    END IF;
END;
/
