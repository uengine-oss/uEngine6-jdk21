-- =====================================================================
-- 이벤트 테이블 컬럼 정비 마이그레이션 (2026-05-21)
-- =====================================================================
-- 변경 내용:
--   1) bpm_event_inbox.event_type   → event_name 으로 정비
--   2) bpm_event_mapping.event_type → event_name 으로 정비
--   3) bpm_event_mapping 의 PK 를 event_type(업무값) → surrogate id 로 교체
--      - id BIGINT 컬럼 + seq_bpm_event_mapping 시퀀스 신규
--      - event_name 은 UNIQUE 업무 키로 유지
--
-- 적용 대상 : 이미 생성된 기존 DB.
-- 두 가지 상태를 모두 처리한다 (DO 블록으로 분기):
--   (A) event_type 만 존재          → 단순 컬럼명 변경
--   (B) event_type + event_name 공존 → Hibernate(ddl-auto)가 event_name 을
--       먼저 추가한 상태. 데이터 이관 후 event_type 제거.
-- 재실행해도 안전하도록 작성되어 있다.
-- ⚠ 실행 전 백업 권장.
-- =====================================================================

BEGIN;

-- ---------------------------------------------------------------------
-- 1. bpm_event_inbox : event_type → event_name
-- ---------------------------------------------------------------------
DO $$
DECLARE
    has_old boolean;
    has_new boolean;
BEGIN
    SELECT EXISTS(SELECT 1 FROM information_schema.columns
                  WHERE table_name = 'bpm_event_inbox' AND column_name = 'event_type') INTO has_old;
    SELECT EXISTS(SELECT 1 FROM information_schema.columns
                  WHERE table_name = 'bpm_event_inbox' AND column_name = 'event_name') INTO has_new;

    IF has_old AND NOT has_new THEN
        -- (A) 깨끗한 상태: 컬럼명만 변경. UNIQUE 제약 uk_inbox_corr_event 는 자동 추종.
        ALTER TABLE bpm_event_inbox RENAME COLUMN event_type TO event_name;

    ELSIF has_old AND has_new THEN
        -- (B) event_name 이 이미 추가된 상태: 데이터 이관 후 event_type 제거.
        UPDATE bpm_event_inbox SET event_name = event_type WHERE event_name IS NULL;
        ALTER TABLE bpm_event_inbox DROP CONSTRAINT IF EXISTS uk_inbox_corr_event;
        ALTER TABLE bpm_event_inbox DROP COLUMN event_type;
        ALTER TABLE bpm_event_inbox
            ADD CONSTRAINT uk_inbox_corr_event UNIQUE (corr_key, event_name);
    END IF;
    -- has_old = false 이면 이미 마이그레이션 완료 → 아무것도 안 함
END $$;

-- ---------------------------------------------------------------------
-- 2. bpm_event_mapping : event_type → event_name
-- ---------------------------------------------------------------------
DO $$
DECLARE
    has_old boolean;
    has_new boolean;
BEGIN
    SELECT EXISTS(SELECT 1 FROM information_schema.columns
                  WHERE table_name = 'bpm_event_mapping' AND column_name = 'event_type') INTO has_old;
    SELECT EXISTS(SELECT 1 FROM information_schema.columns
                  WHERE table_name = 'bpm_event_mapping' AND column_name = 'event_name') INTO has_new;

    IF has_old AND NOT has_new THEN
        ALTER TABLE bpm_event_mapping RENAME COLUMN event_type TO event_name;
    ELSIF has_old AND has_new THEN
        UPDATE bpm_event_mapping SET event_name = event_type WHERE event_name IS NULL;
        ALTER TABLE bpm_event_mapping DROP COLUMN event_type;
    END IF;
END $$;

-- ---------------------------------------------------------------------
-- 3. bpm_event_mapping : surrogate PK(id) 도입
-- ---------------------------------------------------------------------
-- 3-1) 기존 PK(event_type 기반) 제거
ALTER TABLE bpm_event_mapping DROP CONSTRAINT IF EXISTS bpm_event_mapping_pkey;

-- 3-2) surrogate 시퀀스 (엔티티 allocationSize=1 → INCREMENT BY 1)
CREATE SEQUENCE IF NOT EXISTS seq_bpm_event_mapping
    START WITH 1 INCREMENT BY 1 MINVALUE 1 NO CYCLE CACHE 1;

-- 3-3) id 컬럼 추가 후 기존 행 채우기
ALTER TABLE bpm_event_mapping ADD COLUMN IF NOT EXISTS id BIGINT;
UPDATE bpm_event_mapping SET id = nextval('seq_bpm_event_mapping') WHERE id IS NULL;
ALTER TABLE bpm_event_mapping ALTER COLUMN id SET NOT NULL;
ALTER TABLE bpm_event_mapping ALTER COLUMN id SET DEFAULT nextval('seq_bpm_event_mapping');

-- 3-4) 새 PK + event_name UNIQUE 업무 키 (재실행 안전)
ALTER TABLE bpm_event_mapping ADD CONSTRAINT bpm_event_mapping_pkey PRIMARY KEY (id);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uk_event_mapping_event_name') THEN
        ALTER TABLE bpm_event_mapping ADD CONSTRAINT uk_event_mapping_event_name UNIQUE (event_name);
    END IF;
END $$;

-- 3-5) 시퀀스를 컬럼에 종속 (테이블 삭제 시 시퀀스도 정리)
ALTER SEQUENCE seq_bpm_event_mapping OWNED BY bpm_event_mapping.id;

COMMIT;
