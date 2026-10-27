-- Baseline Morocco New 2026-27: base siempre nueva
INSERT INTO generation_version (competition_season_id, version_no, status, last_round_to_assign, source, approved_at, approved_by) VALUES ((SELECT id FROM competition_season WHERE competition_id = 'BOTOLA_D1' AND season_id = '2026-27'), 0, 'APPROVED', 5, 'LEGACY_IMPORT', now(), 'seed');
