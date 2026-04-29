-- V1: core law / law_version / article schema plus embedding sidecar and
-- sync audit log. Persistent key per §R6 of the analysis note is
-- (ls_id, jo, hang, ho, mok) at the article level; lsi_seq is a version
-- attribute, not an identity.

-- ---------- law ----------
CREATE TABLE law (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    ls_id       varchar(10) NOT NULL UNIQUE,          -- 법령ID, zero-padded string (e.g. "001872")
    name_kr     text        NOT NULL,                 -- 법령명_한글
    short_name  text,                                 -- 법령약칭명 (may be null)
    created_at  timestamptz NOT NULL DEFAULT now()
);

COMMENT ON COLUMN law.ls_id IS '법제처 영속 식별자. 개정되어도 동일. 예: 001872 (근로기준법).';
COMMENT ON COLUMN law.short_name IS '법령약칭명. 국민편의상 부여되는 약칭 (예: 기간제법). 없을 수 있음.';

-- ---------- law_version ----------
-- Tracks every version we have ever seen per law. MVP 는 is_current=true 행만
-- 주로 사용하지만 Phase 2 에서 시점 스냅샷을 다룰 때 여기서 확장된다.
CREATE TABLE law_version (
    id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    law_id             uuid        NOT NULL REFERENCES law(id) ON DELETE CASCADE,
    lsi_seq            varchar(20) NOT NULL,          -- 법령일련번호 / MST
    promulgation_date  date        NOT NULL,          -- 공포일자
    promulgation_no    varchar(20),                   -- 공포번호
    effective_date     date        NOT NULL,          -- 시행일자
    revision_type      text,                          -- 제개정구분 (제정/일부개정/전부개정/폐지/타법개정)
    is_current         boolean     NOT NULL DEFAULT false,
    raw_xml_gcs_uri    text,                          -- gs://laborcase-raw/law/{ls_id}/{lsi_seq}.xml
    fetched_at         timestamptz NOT NULL DEFAULT now(),
    UNIQUE (law_id, lsi_seq)
);

CREATE INDEX law_version_is_current_idx ON law_version (law_id) WHERE is_current;
CREATE INDEX law_version_effective_date_idx ON law_version (law_id, effective_date DESC);

COMMENT ON COLUMN law_version.lsi_seq IS 'MST / 법령일련번호. 개정마다 갱신.';
COMMENT ON COLUMN law_version.is_current IS '현재 시행 중인 버전. law 당 1개 true 보장은 트리거 없이 애플리케이션 책임.';

-- ---------- article ----------
-- 조문 평탄화. 조/항/호/목 의 4 레벨까지 한 행에 표현한다.
-- (조, null, null, null) = 조문 헤더, (조, 항, null, null) = 항 등.
-- 조문번호는 raw 숫자를 6자리 zero-pad 한 문자열(예: 제23조 → "000023").
-- URL API JO 파라미터 형식(예: "002300")은 클라이언트에서 변환한다.
CREATE TABLE article (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    law_version_id  uuid        NOT NULL REFERENCES law_version(id) ON DELETE CASCADE,
    jo              char(6)     NOT NULL,
    hang            char(6),
    ho              char(6),
    mok             varchar(4),
    title           text,                              -- 조문제목 (항/호/목은 보통 null)
    body            text        NOT NULL,              -- 조문내용 / 항내용 / 호내용 / 목내용
    effective_date  date,                              -- 조문시행일자 (조문마다 다를 수 있음)
    -- NULLS NOT DISTINCT treats NULL == NULL for the unique check, so the
    -- same (law_version, jo, hang) pair with NULL ho/mok cannot be inserted
    -- twice. Without this flag Postgres would allow the duplicate because
    -- the SQL standard says NULL != NULL.
    UNIQUE NULLS NOT DISTINCT (law_version_id, jo, hang, ho, mok)
);

CREATE INDEX article_law_version_jo_idx ON article (law_version_id, jo);

COMMENT ON TABLE article IS '조/항/호/목 단위 조문 텍스트. 하나의 law_version 에 수백 행.';
COMMENT ON COLUMN article.jo IS '조문번호 zero-pad 6자리. 제23조 → "000023".';

-- ---------- article_embedding ----------
-- pgvector 사이드카. 임베딩 모델 차원은 1536 (OpenAI text-embedding-3-small).
-- 다른 모델 사용 시 별도 테이블 또는 차원 변경 마이그레이션 필요.
CREATE TABLE article_embedding (
    article_id   uuid        PRIMARY KEY REFERENCES article(id) ON DELETE CASCADE,
    vector       vector(1536) NOT NULL,
    model        text         NOT NULL DEFAULT 'openai:text-embedding-3-small',
    embedded_at  timestamptz  NOT NULL DEFAULT now()
);

-- ivfflat 인덱스. lists 파라미터는 데이터 규모에 따라 후속 튜닝.
-- rows<10k 기준 lists=100 이 일반적 권장.
CREATE INDEX article_embedding_ivfflat_idx
    ON article_embedding
    USING ivfflat (vector vector_cosine_ops)
    WITH (lists = 100);

-- ---------- sync_log ----------
-- FullSyncJob / DeltaSyncJob 실행 기록. Task 11 관측성의 소스.
CREATE TABLE sync_log (
    id                 uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    job_name           text        NOT NULL,          -- "full-sync" | "delta-sync"
    started_at         timestamptz NOT NULL DEFAULT now(),
    finished_at        timestamptz,
    status             text        NOT NULL,          -- "RUNNING" | "SUCCESS" | "FAILED"
    error_message      text,
    versions_changed   int         NOT NULL DEFAULT 0
);

CREATE INDEX sync_log_started_at_idx ON sync_log (job_name, started_at DESC);
