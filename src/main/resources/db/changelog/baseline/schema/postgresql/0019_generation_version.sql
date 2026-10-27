-- Baseline Morocco New 2026-27: base siempre nueva
CREATE TABLE generation_version (
    id                     BIGSERIAL    PRIMARY KEY,
    competition_season_id  BIGINT       NOT NULL REFERENCES competition_season(id) ON DELETE CASCADE,
    version_no             INT          NOT NULL,
    status                 VARCHAR(10)  NOT NULL CHECK (status IN ('DRAFT','APPROVED','ARCHIVED')),
    last_round_to_assign   INT          NOT NULL,
    source                 VARCHAR(15)  NOT NULL CHECK (source IN ('ENGINE','LEGACY_IMPORT')),
    job_id                 UUID,
    config_snapshot        JSONB,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    approved_at            TIMESTAMPTZ,
    approved_by            VARCHAR(80),
    CONSTRAINT uq_version UNIQUE (competition_season_id, version_no)
);
