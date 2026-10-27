-- Baseline Morocco New 2026-27: base siempre nueva
CREATE TABLE planning_job (
    id                     UNIQUEIDENTIFIER         PRIMARY KEY,
    competition_season_id  BIGINT       NOT NULL REFERENCES competition_season(id) ON DELETE CASCADE,
    status                 VARCHAR(10)  NOT NULL CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED','CANCELLED')),
    last_round_to_assign   INT          NOT NULL,
    cancel_requested       BIT      NOT NULL DEFAULT 0,
    created_at             DATETIMEOFFSET  NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    started_at             DATETIMEOFFSET,
    finished_at            DATETIMEOFFSET,
    error_message          NVARCHAR(MAX),
    version_id             BIGINT       REFERENCES generation_version(id) ON DELETE NO ACTION
);
