-- Baseline Morocco New 2026-27: base siempre nueva
CREATE TABLE planning_job (
    id                     UUID         PRIMARY KEY,
    competition_season_id  BIGINT       NOT NULL REFERENCES competition_season(id) ON DELETE CASCADE,
    status                 VARCHAR(10)  NOT NULL CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED')),
    last_round_to_assign   INT          NOT NULL,
    cancel_requested       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    started_at             TIMESTAMPTZ,
    finished_at            TIMESTAMPTZ,
    error_message          TEXT,
    version_id             BIGINT       REFERENCES generation_version(id) ON DELETE SET NULL
);
