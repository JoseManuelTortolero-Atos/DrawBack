package net.atos.mev.calendarcalculator.setup;

import java.time.LocalDate;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class RoundDateRepository {

    private final JdbcClient jdbc;

    RoundDateRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void include(long competitionSeasonId, int round, LocalDate date) {
        Long roundId = jdbc.sql("SELECT id FROM league_round WHERE competition_season_id = :cs AND round_no = :r")
                .param("cs", competitionSeasonId).param("r", round).query(Long.class).optional().orElse(null);
        if (roundId == null) {
            int order = jdbc.sql("SELECT COALESCE(MAX(list_order), -1) + 1 FROM league_round WHERE competition_season_id = :cs")
                    .param("cs", competitionSeasonId).query(Integer.class).single();
            int teamCount = jdbc.sql("SELECT COUNT(*) FROM competition_season_team WHERE competition_season_id = :cs")
                    .param("cs", competitionSeasonId).query(Integer.class).single();
            jdbc.sql("""
                    INSERT INTO league_round (competition_season_id, list_order, round_no, label, leg)
                    VALUES (:cs, :order, :round, :label, :leg)""")
                    .param("cs", competitionSeasonId).param("order", order).param("round", round).param("label", String.valueOf(round))
                    .param("leg", round <= teamCount - 1 ? 1 : 2).update();
            roundId = jdbc.sql("SELECT id FROM league_round WHERE competition_season_id = :cs AND round_no = :r")
                    .param("cs", competitionSeasonId).param("r", round).query(Long.class).single();
        }
        int exists = jdbc.sql("SELECT COUNT(*) FROM round_date WHERE league_round_id = :r AND play_date = :date")
                .param("r", roundId).param("date", date).query(Integer.class).single();
        if (exists == 0) {
            jdbc.sql("INSERT INTO round_date (league_round_id, play_date) VALUES (:r, :date)")
                    .param("r", roundId).param("date", date).update();
        }
    }
}
