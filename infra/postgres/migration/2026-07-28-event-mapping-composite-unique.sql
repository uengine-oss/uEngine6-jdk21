-- Preserve multiple BPM event mappings that share one event name.
-- Apply before deploying code that queries mappings as a list.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conname = 'uk_event_mapping_event_name'
           AND conrelid = 'bpm_event_mapping'::regclass
    ) THEN
        ALTER TABLE bpm_event_mapping
            DROP CONSTRAINT uk_event_mapping_event_name;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conname = 'uk_event_mapping_target'
           AND conrelid = 'bpm_event_mapping'::regclass
    ) THEN
        ALTER TABLE bpm_event_mapping
            ADD CONSTRAINT uk_event_mapping_target
            UNIQUE (event_name, definition_id, tracing_tag, is_start_event);
    END IF;
END $$;
