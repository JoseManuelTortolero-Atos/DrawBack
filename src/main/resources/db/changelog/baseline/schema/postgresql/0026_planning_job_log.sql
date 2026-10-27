-- Baseline Morocco New 2026-27: base siempre nueva
CREATE TABLE planning_job_log (
    id        BIGSERIAL    PRIMARY KEY,
    job_id    UUID         NOT NULL REFERENCES planning_job(id) ON DELETE CASCADE,
    seq       INT          NOT NULL,
    at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    level     VARCHAR(5)   NOT NULL CHECK (level IN ('INFO','WARN','ERROR')),
    message   TEXT         NOT NULL,
    CONSTRAINT uq_job_log UNIQUE (job_id, seq)
);
