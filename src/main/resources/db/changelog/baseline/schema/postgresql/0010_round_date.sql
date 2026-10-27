-- Baseline Morocco New 2026-27: base siempre nueva
CREATE TABLE round_date (
    league_round_id  BIGINT NOT NULL REFERENCES league_round(id) ON DELETE CASCADE,
    play_date        DATE   NOT NULL,
    PRIMARY KEY (league_round_id, play_date)
);
