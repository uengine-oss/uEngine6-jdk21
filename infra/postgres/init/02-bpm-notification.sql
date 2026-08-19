-- =====================================================================
-- BPM Notification 테이블 초기화 (PostgreSQL)
-- =====================================================================
-- Postgres 컨테이너 첫 기동 시 /docker-entrypoint-initdb.d 에 마운트되어
-- 자동 실행됨. 이미 만들어진 DB 에는 영향 없음.
--
-- 헤더 벨/배지 알림 저장소. 워크아이템이 담당자에게 배정될 때
-- BpmLifecycleService → WorkItemNotificationService 가 행을 만든다.
-- postgres 프로파일은 ddl-auto=update 라 없어도 자동 생성되지만,
-- qa(validate) / prod(none) 를 위해 명시 DDL 을 둔다.
-- =====================================================================

CREATE TABLE IF NOT EXISTS bpm_notification (
    id            VARCHAR(36)  NOT NULL,
    user_id       VARCHAR(255),
    from_user_id  VARCHAR(255),
    title         VARCHAR(1000),
    description   VARCHAR(2000),
    type          VARCHAR(50),
    url           VARCHAR(1000),
    is_checked    INTEGER      DEFAULT 0,
    time_stamp    TIMESTAMP,
    task_id       BIGINT,
    inst_id       BIGINT,
    tenant_id     VARCHAR(255),
    CONSTRAINT bpm_notification_pkey PRIMARY KEY (id)
);

-- 벨 조회: 특정 사용자의 미확인 알림 최신순
CREATE INDEX IF NOT EXISTS idx_bpm_notification_user_unread
    ON bpm_notification (user_id, is_checked, time_stamp DESC);

-- 업무 종료/재배정 시 해당 업무의 알림 정리
CREATE INDEX IF NOT EXISTS idx_bpm_notification_task
    ON bpm_notification (task_id);

-- 읽음 처리(같은 url 일괄)
CREATE INDEX IF NOT EXISTS idx_bpm_notification_user_url
    ON bpm_notification (user_id, url);
