DECLARE
    column_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO column_count
      FROM user_tab_columns
     WHERE table_name = 'BPM_EVENT_INBOX'
       AND column_name = 'ACTOR_ENDPOINT';

    IF column_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE BPM_EVENT_INBOX ADD ACTOR_ENDPOINT VARCHAR2(255)';
    END IF;
END;
/
