-- Baseline Morocco New 2026-27: base siempre nueva
INSERT INTO assign_group (competition_season_id, group_no, teams, rounds_forward_look) VALUES ((SELECT id FROM competition_season WHERE competition_id = 'CNPFF_D1' AND season_id = '2026-27'), 1, 'ASFAR,FUS,WAC,RSB,UTS,AMLFF,SCC,HST,PFAM,ARAFF,USSB,HANFF', 3);
