-- Baseline Morocco New 2026-27: base siempre nueva
CREATE TABLE constraint_rule (
    id                     BIGSERIAL   PRIMARY KEY,
    competition_season_id  BIGINT      NOT NULL REFERENCES competition_season(id) ON DELETE CASCADE,
    list_order             INT         NOT NULL,
    rule_type              VARCHAR(30) NOT NULL CHECK (rule_type IN ('MatchNotInRound','SeparateMatches','VenueUnavailable','FixedHomeDate','LEGACY_CHECK')),
    team_a                 VARCHAR(12),
    team_b                 VARCHAR(12),
    team                   VARCHAR(12),
    teams                  TEXT,
    rounds                 TEXT,
    min_rounds             INT,
    date_from              DATE,
    date_to                DATE,
    fixed_date             DATE,
    definition             TEXT,
    note                   TEXT
);
