-- Baseline Morocco New 2026-27: base siempre nueva
CREATE TABLE rest_rule (
    competition_season_id  BIGINT      NOT NULL REFERENCES competition_season(id) ON DELETE CASCADE,
    list_order             INT         NOT NULL,
    competition_code       VARCHAR(12) NOT NULL,
    rest_days              INT         NOT NULL CHECK (rest_days >= 0),
    PRIMARY KEY (competition_season_id, competition_code)
);
