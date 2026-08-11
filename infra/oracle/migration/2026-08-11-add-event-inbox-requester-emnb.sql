DECLARE
    column_count NUMBER;
BEGIN
    SELECT COUNT(*)
      INTO column_count
      FROM user_tab_columns
     WHERE table_name = 'BPM_EVENT_INBOX'
       AND column_name = 'REQUESTER_EMNB';

    IF column_count = 0 THEN
        EXECUTE IMMEDIATE 'ALTER TABLE BPM_EVENT_INBOX ADD REQUESTER_EMNB VARCHAR2(255)';
    END IF;
END;
/
