-- Baseline Morocco New 2026-27: base siempre nueva
-- list_order es SIGNIFICATIVO: el motor reparte las franjas en ese orden (leer ORDER BY round_no, list_order).
CREATE TABLE fixture (
    id                     BIGINT IDENTITY(1,1)   PRIMARY KEY,
    competition_season_id  BIGINT      NOT NULL REFERENCES competition_season(id) ON DELETE CASCADE,
    round_no               INT         NOT NULL CHECK (round_no > 0),
    list_order             INT         NOT NULL,
    home                   VARCHAR(12) NOT NULL,
    away                   VARCHAR(12) NOT NULL,
    locked                 BIT     NOT NULL DEFAULT 0,
    play_date              DATE,
    play_hour              VARCHAR(5),
    CONSTRAINT ck_fixture_teams CHECK (home <> away),
    CONSTRAINT ck_fixture_hour CHECK (play_hour IS NULL OR play_date IS NOT NULL),
    CONSTRAINT uq_fixture UNIQUE (competition_season_id, round_no, home, away)
);
