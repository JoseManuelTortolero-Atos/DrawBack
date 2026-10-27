-- Baseline Morocco New 2026-27: base siempre nueva
CREATE TABLE generated_match (
    id                BIGINT IDENTITY(1,1)   PRIMARY KEY,
    version_id        BIGINT      NOT NULL REFERENCES generation_version(id) ON DELETE CASCADE,
    list_order        INT         NOT NULL,
    competition_code  VARCHAR(12) NOT NULL,
    round_label       VARCHAR(20),
    play_date         DATE        NOT NULL,
    play_hour         VARCHAR(5),
    home              VARCHAR(200),
    away              VARCHAR(12)
);
