-- Baseline Morocco New 2026-27: base siempre nueva
CREATE TABLE league_round (
    id                     BIGSERIAL   PRIMARY KEY,
    competition_season_id  BIGINT      NOT NULL REFERENCES competition_season(id) ON DELETE CASCADE,
    list_order             INT         NOT NULL,
    round_no               INT,
    label                  VARCHAR(20),
    leg                    INT CHECK (leg IN (1, 2)),
    CONSTRAINT ck_round_kind CHECK (round_no IS NOT NULL OR label IS NOT NULL),
    CONSTRAINT uq_round_order UNIQUE (competition_season_id, list_order)
);
