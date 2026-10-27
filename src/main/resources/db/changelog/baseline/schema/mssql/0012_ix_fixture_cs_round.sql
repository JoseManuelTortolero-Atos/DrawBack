-- Baseline Morocco New 2026-27: base siempre nueva
CREATE INDEX ix_fixture_cs_round ON fixture (competition_season_id, round_no, list_order);
