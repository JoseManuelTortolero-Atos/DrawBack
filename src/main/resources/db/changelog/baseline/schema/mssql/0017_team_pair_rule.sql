-- Baseline Morocco New 2026-27: base siempre nueva
CREATE TABLE team_pair_rule (
    id                     BIGINT IDENTITY(1,1)   PRIMARY KEY,
    competition_season_id  BIGINT      NOT NULL REFERENCES competition_season(id) ON DELETE CASCADE,
    kind                   VARCHAR(20) NOT NULL CHECK (kind IN ('NOT_HOME_SAME_DAY','DERBY')),
    list_order             INT         NOT NULL,
    team_a                 VARCHAR(12) NOT NULL,
    team_b                 VARCHAR(12) NOT NULL,
    CONSTRAINT ck_pair CHECK (team_a <> team_b)
);
