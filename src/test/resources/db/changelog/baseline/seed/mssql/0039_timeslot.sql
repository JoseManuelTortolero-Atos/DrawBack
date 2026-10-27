-- Baseline Morocco New 2026-27: base siempre nueva
INSERT INTO timeslot (competition_season_id, list_order, slot) VALUES ((SELECT id FROM competition_season WHERE competition_id = 'CNPFF_D2' AND season_id = '2026-27'), 0, '15:00');
INSERT INTO timeslot (competition_season_id, list_order, slot) VALUES ((SELECT id FROM competition_season WHERE competition_id = 'CNPFF_D2' AND season_id = '2026-27'), 1, '15:00');
INSERT INTO timeslot (competition_season_id, list_order, slot) VALUES ((SELECT id FROM competition_season WHERE competition_id = 'CNPFF_D2' AND season_id = '2026-27'), 2, '15:00');
INSERT INTO timeslot (competition_season_id, list_order, slot) VALUES ((SELECT id FROM competition_season WHERE competition_id = 'CNPFF_D2' AND season_id = '2026-27'), 3, '15:00');
