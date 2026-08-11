ALTER TABLE bpm_event_inbox
    ADD COLUMN IF NOT EXISTS actor_endpoint VARCHAR(255);
