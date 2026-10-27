-- Baseline Morocco New 2026-27: base siempre nueva
-- Como mucho una versión APROBADA por competición y temporada.
CREATE UNIQUE INDEX uq_version_approved ON generation_version (competition_season_id) WHERE status = 'APPROVED';
