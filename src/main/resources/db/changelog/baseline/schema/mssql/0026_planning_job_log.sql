-- Baseline Morocco New 2026-27: base siempre nueva
CREATE TABLE planning_job_log (
    id        BIGINT IDENTITY(1,1)    PRIMARY KEY,
    job_id    UNIQUEIDENTIFIER         NOT NULL REFERENCES planning_job(id) ON DELETE CASCADE,
    seq       INT          NOT NULL,
    at        DATETIMEOFFSET  NOT NULL DEFAULT SYSDATETIMEOFFSET(),
    level     VARCHAR(5)   NOT NULL CHECK (level IN ('INFO','WARN','ERROR')),
    message   NVARCHAR(MAX)         NOT NULL,
    CONSTRAINT uq_job_log UNIQUE (job_id, seq)
);
