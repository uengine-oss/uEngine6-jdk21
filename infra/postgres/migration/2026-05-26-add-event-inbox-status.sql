-- Add explicit processing status to bpm_event_inbox.
-- PENDING: unprocessed or waiting for retry
-- SUCCESS: dispatched successfully
-- FAILED : max retry reached / dead-letter

ALTER TABLE bpm_event_inbox
    ADD COLUMN IF NOT EXISTS status VARCHAR(16) NOT NULL DEFAULT 'PENDING';

UPDATE bpm_event_inbox
   SET status = CASE
        WHEN processed_at IS NOT NULL AND last_error IS NULL THEN 'SUCCESS'
        WHEN processed_at IS NOT NULL AND last_error IS NOT NULL THEN 'FAILED'
        ELSE 'PENDING'
   END;
