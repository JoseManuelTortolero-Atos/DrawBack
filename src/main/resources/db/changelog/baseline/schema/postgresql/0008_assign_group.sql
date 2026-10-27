-- Baseline Morocco New 2026-27: base siempre nueva
-- Grupos de asignación del motor (ScheduleMoroccoAlg.assignGroups.N / roundsForwardLook.N)
CREATE TABLE assign_group (
    competition_season_id  BIGINT      NOT NULL REFERENCES competition_season(id) ON DELETE CASCADE,
    group_no               INT         NOT NULL,
    teams                  TEXT        NOT NULL,
    rounds_forward_look    INT         NOT NULL,
    PRIMARY KEY (competition_season_id, group_no)
);
