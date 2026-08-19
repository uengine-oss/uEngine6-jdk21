-- =====================================================================
-- BPM Notification 테이블 추가 (Oracle) - 2026-08-19
-- =====================================================================
-- 헤더 벨/배지 알림 저장소. 워크아이템이 담당자에게 배정될 때
-- BpmLifecycleService → WorkItemNotificationService 가 행을 만든다.
--
-- is_checked 는 OracleBooleanConverter(Boolean → NUMBER(1)) 매핑이다.
-- qa=validate / prod=none 프로파일에서는 이 스크립트가 반드시 선행되어야 한다.
-- =====================================================================

CREATE TABLE BPM_NOTIFICATION (
    ID            VARCHAR2(36)   NOT NULL,
    USER_ID       VARCHAR2(255),
    FROM_USER_ID  VARCHAR2(255),
    TITLE         VARCHAR2(1000),
    DESCRIPTION   VARCHAR2(2000),
    TYPE          VARCHAR2(50),
    URL           VARCHAR2(1000),
    IS_CHECKED    NUMBER(1)      DEFAULT 0,
    TIME_STAMP    TIMESTAMP,
    TASK_ID       NUMBER(19),
    INST_ID       NUMBER(19),
    TENANT_ID     VARCHAR2(255),
    CONSTRAINT BPM_NOTIFICATION_PK PRIMARY KEY (ID)
);

-- 벨 조회: 특정 사용자의 미확인 알림 최신순
CREATE INDEX IDX_BPM_NOTI_USER_UNREAD ON BPM_NOTIFICATION (USER_ID, IS_CHECKED, TIME_STAMP DESC);

-- 업무 종료/재배정 시 해당 업무의 알림 정리
CREATE INDEX IDX_BPM_NOTI_TASK ON BPM_NOTIFICATION (TASK_ID);

-- 읽음 처리(같은 url 일괄)
CREATE INDEX IDX_BPM_NOTI_USER_URL ON BPM_NOTIFICATION (USER_ID, URL);
