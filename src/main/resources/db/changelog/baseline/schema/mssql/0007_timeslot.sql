-- Baseline Morocco New 2026-27: base siempre nueva
CREATE TABLE timeslot (
    competition_season_id  BIGINT     NOT NULL REFERENCES competition_season(id) ON DELETE CASCADE,
    list_order             INT        NOT NULL,
    slot                   VARCHAR(5) NOT NULL CHECK (slot LIKE '[0-2][0-9]:[0-5][0-9]'),
    PRIMARY KEY (competition_season_id, list_order)
);
