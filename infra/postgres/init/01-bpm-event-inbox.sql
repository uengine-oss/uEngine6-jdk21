-- =====================================================================
-- BPM Event Inbox 테이블 초기화
-- =====================================================================
-- Postgres 컨테이너 첫 기동 시 /docker-entrypoint-initdb.d 에 마운트되어
-- 자동 실행됨. 이미 만들어진 DB 에는 영향 없음.
--
-- 폴링 모드 (uengine.messaging.mode=polling) 에서 외부 이벤트 인입 큐로 사용.
-- Hibernate ddl-auto=update 만으로도 기본 스키마는 생성되지만, raw SQL INSERT
-- 편의성을 위한 DEFAULT 절은 여기서 미리 적용한다.
-- =====================================================================

-- 시퀀스 (Hibernate AUTO 전략용, increment 50 = JPA 기본)
CREATE SEQUENCE IF NOT EXISTS bpm_event_inbox_seq
    START WITH 1
    INCREMENT BY 50
    MINVALUE 1
    MAXVALUE 9223372036854775807
    NO CYCLE
    CACHE 1;

-- 메인 테이블
CREATE TABLE IF NOT EXISTS bpm_event_inbox (
    id            BIGINT                      NOT NULL DEFAULT nextval('bpm_event_inbox_seq'),
    event_type    VARCHAR(128),                                                                        -- dispatcher 가 EventMapping 매칭에 사용
    payload       TEXT                        NOT NULL,                                                -- 이벤트 본문 JSON
    corr_key      VARCHAR(64)                          DEFAULT ('start_' || gen_random_uuid()::text), -- 비즈니스 식별자, 미지정 시 자동
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT now(),                                  -- 인입 시각
    processed_at  TIMESTAMP(6) WITH TIME ZONE,                                                         -- 처리 완료 시각 (NULL = 미처리)
    try_cnt       INTEGER                     NOT NULL DEFAULT 0,                                      -- 시도 횟수 (1=첫 시도)
    last_error    TEXT,                                                                                -- 실패 시 메시지 (NULL=정상, 값=dead-letter)

    CONSTRAINT bpm_event_inbox_pkey PRIMARY KEY (id),
    CONSTRAINT uk_inbox_corr_event  UNIQUE      (corr_key, event_type)
);

-- 미처리 row 빠른 조회용 (InboxPollJob 가 매초 사용)
CREATE INDEX IF NOT EXISTS idx_inbox_unprocessed
    ON bpm_event_inbox (processed_at);

-- 시퀀스를 테이블 컬럼에 종속 (테이블 삭제 시 시퀀스도 정리)
ALTER SEQUENCE bpm_event_inbox_seq OWNED BY bpm_event_inbox.id;

-- =====================================================================
-- 사용 예시
-- =====================================================================
-- (1) 단순 INSERT — DEFAULT 가 알아서 채움
--     INSERT INTO bpm_event_inbox (event_type, payload)
--     VALUES ('START_CREDIT_RATING', '{"자산": 5173, "신용도": 400}');
--
-- (2) 비즈니스 키 명시
--     INSERT INTO bpm_event_inbox (event_type, payload, corr_key)
--     VALUES ('START_CREDIT_RATING', '{...}', 'app-2026-001');
--
-- (3) 운영 모니터링
--     SELECT id, event_type, corr_key, try_cnt, processed_at, LEFT(last_error, 200) AS err
--       FROM bpm_event_inbox
--      ORDER BY id DESC LIMIT 20;
--
-- (4) dead-letter 검색
--     SELECT * FROM bpm_event_inbox
--      WHERE processed_at IS NOT NULL AND last_error IS NOT NULL
--      ORDER BY processed_at DESC;
--
-- (5) dead-letter 수동 재처리
--     UPDATE bpm_event_inbox
--        SET processed_at = NULL, last_error = NULL, try_cnt = 0
--      WHERE id = ?;
-- =====================================================================
