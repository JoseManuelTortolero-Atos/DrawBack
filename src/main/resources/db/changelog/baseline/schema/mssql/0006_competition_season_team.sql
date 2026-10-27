-- Baseline Morocco New 2026-27: base siempre nueva
CREATE TABLE competition_season_team (
    competition_season_id  BIGINT      NOT NULL REFERENCES competition_season(id) ON DELETE CASCADE,
    team_code              VARCHAR(12) NOT NULL REFERENCES team(code),
    list_order             INT         NOT NULL,
    display_name           VARCHAR(160),
    no_light               BIT     NOT NULL DEFAULT 0,
    optimize_times         BIT     NOT NULL DEFAULT 1,
    PRIMARY KEY (competition_season_id, team_code),
    CONSTRAINT uq_cs_team_order UNIQUE (competition_season_id, list_order)
);
