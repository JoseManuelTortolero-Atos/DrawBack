-- Baseline Morocco New 2026-27: base siempre nueva
INSERT INTO timeslot (competition_season_id, list_order, slot) VALUES ((SELECT id FROM competition_season WHERE competition_id = 'BOTOLA_D2' AND season_id = '2026-27'), 0, '20:00');
INSERT INTO timeslot (competition_season_id, list_order, slot) VALUES ((SELECT id FROM competition_season WHERE competition_id = 'BOTOLA_D2' AND season_id = '2026-27'), 1, '18:00');
INSERT INTO timeslot (competition_season_id, list_order, slot) VALUES ((SELECT id FROM competition_season WHERE competition_id = 'BOTOLA_D2' AND season_id = '2026-27'), 2, '16:00');
