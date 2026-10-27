-- Baseline Morocco New 2026-27: base siempre nueva
INSERT INTO assign_group (competition_season_id, group_no, teams, rounds_forward_look) VALUES ((SELECT id FROM competition_season WHERE competition_id = 'BOTOLA_D1' AND season_id = '2026-27'), 1, 'CODM,DHJ,FAR,FUS,HUSA,IRT,MAS,WST,RCA,RCAZ,RSB,UTS,WAC,MAT,KACM,USAT', 3);
INSERT INTO assign_group (competition_season_id, group_no, teams, rounds_forward_look) VALUES ((SELECT id FROM competition_season WHERE competition_id = 'BOTOLA_D1' AND season_id = '2026-27'), 2, 'CAK,CJBG,JSM,KAC,MCO,OCS,USYM,SM,USMO,WAF,JSS,SCCM,IZK,OCK,OD,USB', 4);
