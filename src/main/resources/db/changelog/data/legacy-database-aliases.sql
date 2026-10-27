INSERT INTO competition_alias (competition_id, alias_type, alias_value)
SELECT c.id, refs.alias_type, refs.alias_value
FROM (VALUES
    ('BOTOLA_D1', 'LEGACY_ENV', 'MoroccoDiv1.properties'),
    ('BOTOLA_D1', 'LEGACY_ENV_OLD', 'MoroccoDiv1_Old.properties'),
    ('BOTOLA_D1', 'LEGACY_SCHEDULE', 'schBotolaD1/SchMoroccoD1.properties'),
    ('BOTOLA_D1', 'LEGACY_SCHEDULE_SHORT', 'SchMoroccoD1.properties'),
    ('BOTOLA_D1', 'LEGACY_DRAW', 'AssignPositionsMorocco16.properties'),
    ('BOTOLA_D1', 'LEGACY_DRAW_OLD', 'AssignPositionsMoroccoBotola.properties'),
    ('BOTOLA_D1', 'LEGACY_XLSX', 'AssignmentsExcelMoroccoD1.properties'),
    ('BOTOLA_D2', 'LEGACY_ENV', 'MoroccoDiv2v1.properties'),
    ('BOTOLA_D2', 'LEGACY_SCHEDULE', 'schBotolaD2/SchMoroccoD2.properties'),
    ('BOTOLA_D2', 'LEGACY_SCHEDULE_SHORT', 'SchMoroccoD2.properties'),
    ('BOTOLA_D2', 'LEGACY_MASTER', 'SchEnvironment.properties'),
    ('BOTOLA_D2', 'LEGACY_XLSX', 'AssignmentsExcelMoroccoD2.properties'),
    ('CNPFF_D1', 'LEGACY_ENV', 'MoroccoFemDiv1.properties'),
    ('CNPFF_D1', 'LEGACY_SCHEDULE', 'schCNPFF1/MoroccoCNPFF1.properties'),
    ('CNPFF_D1', 'LEGACY_SCHEDULE_SHORT', 'MoroccoCNPFF1.properties'),
    ('CNPFF_D1', 'LEGACY_DRAW', 'AssignPositionsMoroccoCNPFF.properties'),
    ('CNPFF_D1', 'LEGACY_MASTER', 'AssignPositions.properties'),
    ('CNPFF_D1', 'LEGACY_XLSX', 'AssignmentsExcelMoroccoFemD1.properties'),
    ('CNPFF_D2', 'LEGACY_ENV', 'MoroccoFemDiv2.properties'),
    ('CNPFF_D2', 'LEGACY_SCHEDULE', 'schCNPFF2/MoroccoCNPFF2.properties'),
    ('CNPFF_D2', 'LEGACY_SCHEDULE_SHORT', 'MoroccoCNPFF2.properties'),
    ('CNPFF_D2', 'LEGACY_XLSX', 'AssignmentsExcelMoroccoFemD2.properties')
) AS refs(competition_id, alias_type, alias_value)
JOIN competition c ON c.id = refs.competition_id
WHERE NOT EXISTS (
    SELECT 1 FROM competition_alias existing
    WHERE existing.competition_id = c.id AND existing.alias_type = refs.alias_type
);
