ALTER TABLE bpm_event_inbox
    ADD COLUMN IF NOT EXISTS requester_emnb VARCHAR(255);
