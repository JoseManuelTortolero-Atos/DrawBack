-- Baseline Morocco New 2026-27: base siempre nueva
-- Un único job activo (QUEUED/RUNNING) por competición y temporada.
CREATE UNIQUE INDEX uq_planning_job_active ON planning_job (competition_season_id) WHERE status IN ('QUEUED','RUNNING');
