-- Baseline Morocco New 2026-27: base siempre nueva
CREATE TABLE blackout (
    id                     BIGINT IDENTITY(1,1)   PRIMARY KEY,
    competition_season_id  BIGINT      NOT NULL REFERENCES competition_season(id) ON DELETE CASCADE,
    label                  VARCHAR(80) NOT NULL,
    date_from              DATE        NOT NULL,
    date_to                DATE        NOT NULL,
    CONSTRAINT ck_blackout CHECK (date_to >= date_from)
);
