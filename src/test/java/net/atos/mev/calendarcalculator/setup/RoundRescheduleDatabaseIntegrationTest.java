package net.atos.mev.calendarcalculator.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

import net.atos.mev.calendarcalculator.setup.SetupDtos.MatchRescheduleRequest;
import net.atos.mev.calendarcalculator.setup.SetupDtos.RoundRescheduleRequest;

/** Round-level reprogramming on a disposable 4-team, 6-round league (real PostgreSQL / SQL Server). */
@SpringBootTest
class RoundRescheduleDatabaseIntegrationTest {
    private static final String SEASON = "2096-97";
    private static final String AUTH = "ROLE_TEST_SCHEDULE_ADMIN";
    private static final String[][] PAIRS = {{"A", "B"}, {"C", "D"}, {"A", "C"}, {"D", "B"}, {"A", "D"}, {"B", "C"},
            {"B", "A"}, {"D", "C"}, {"C", "A"}, {"B", "D"}, {"D", "A"}, {"C", "B"}};

    @Autowired JdbcClient jdbc;
    @Autowired SeasonSetupRepository setups;
    @Autowired MatchStateService states;

    private String competitionId;
    private String code;
    private String[] team;
    private long cs;
    private boolean createdSeason;

    private static LocalDate roundDate(int round) {
        return round <= 3 ? LocalDate.of(2096, 9, 7).plusDays(14L * (round - 1)) : LocalDate.of(2097, 1, 10).plusDays(14L * (round - 4));
    }

    @BeforeEach
    void initialize() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        competitionId = "RR_" + suffix;
        code = "R" + suffix;
        team = new String[] {"A" + suffix, "B" + suffix, "C" + suffix, "D" + suffix};
        createdSeason = jdbc.sql("SELECT COUNT(*) FROM season WHERE id = :s").param("s", SEASON).query(Integer.class).single() == 0;
        if (createdSeason) {
            jdbc.sql("INSERT INTO season (id) VALUES (:s)").param("s", SEASON).update();
        }
        jdbc.sql("INSERT INTO competition (id, code, name, storage_dir, output_suffix, sort_order) VALUES (:c, :code, :c, :c, '', 9999)")
                .param("c", competitionId).param("code", code).update();
        jdbc.sql("""
                INSERT INTO competition_season (competition_id, season_id, season_start, season_end, start_second_leg,
                     max_matches_per_day, max_days_advance_match, max_days_postpone_match, round_calculation_order)
                VALUES (:c, :s, :start, :end, :second, 4, 30, 30, '')
                """).param("c", competitionId).param("s", SEASON).param("start", LocalDate.of(2096, 7, 1))
                .param("end", LocalDate.of(2097, 6, 30)).param("second", LocalDate.of(2097, 1, 10)).update();
        cs = setups.competitionSeasonId(competitionId, SEASON);
        var general = new SetupDtos.General(LocalDate.of(2096, 7, 1), LocalDate.of(2097, 6, 30),
                roundDate(1), LocalDate.of(2097, 1, 10), 4, 30, 30, false, 1, 1, 6, "sat-sun");
        var rounds = new java.util.ArrayList<SetupDtos.Round>();
        var fixtures = new java.util.ArrayList<SetupDtos.Fixture>();
        for (int r = 1; r <= 6; r++) {
            rounds.add(new SetupDtos.Round(r, null, r <= 3 ? 1 : 2, List.of(roundDate(r))));
            for (int m = 0; m < 2; m++) {
                String[] pair = PAIRS[(r - 1) * 2 + m];
                fixtures.add(new SetupDtos.Fixture(null, r, team[pair[0].charAt(0) - 'A'], team[pair[1].charAt(0) - 'A'], false, null, null));
            }
        }
        setups.replace(new SetupDtos.SeasonSetup(competitionId, code, SEASON, general, List.of("18:00"), null, null,
                java.util.Arrays.stream(team).map(t -> new SetupDtos.Team(t, t, null, false, false)).toList(),
                rounds, fixtures, List.of(), List.of(), Map.of("CP", 4), List.of(), List.of(), List.of(), null, false,
                new SetupDtos.EngineSettings(1, 1, "0", "", List.of(new SetupDtos.AssignGroup(1, String.join(",", team), 1)), Map.of()),
                0, null));
        for (int r = 1; r <= 6; r++) {
            jdbc.sql("""
                    UPDATE season_match_state SET status = 'PLANIFICADA', play_date = :d, play_hour = '18:00'
                     WHERE competition_season_id = :cs AND round_no = :r""").param("d", roundDate(r)).param("cs", cs).param("r", r).update();
        }
    }

    @AfterEach
    void cleanup() {
        if (cs == 0) {
            return;
        }
        jdbc.sql("DELETE FROM match_change_audit WHERE competition_season_id = :cs").param("cs", cs).update();
        jdbc.sql("DELETE FROM season_match_state WHERE competition_season_id = :cs").param("cs", cs).update();
        jdbc.sql("DELETE FROM round_date WHERE league_round_id IN (SELECT id FROM league_round WHERE competition_season_id = :cs)")
                .param("cs", cs).update();
        for (String table : List.of("fixture", "timeslot", "competition_season_team", "league_round", "external_commitment",
                "blackout", "rest_rule", "team_pair_rule", "constraint_rule", "assign_group", "season_export_colour")) {
            jdbc.sql("DELETE FROM " + table + " WHERE competition_season_id = :cs").param("cs", cs).update();
        }
        jdbc.sql("DELETE FROM competition_season WHERE id = :cs").param("cs", cs).update();
        jdbc.sql("DELETE FROM competition WHERE id = :c").param("c", competitionId).update();
        jdbc.sql("DELETE FROM team WHERE code IN (:codes)").param("codes", List.of(team)).update();
        if (createdSeason) {
            jdbc.sql("DELETE FROM season WHERE id = :s AND NOT EXISTS (SELECT 1 FROM competition_season WHERE season_id = :s)")
                    .param("s", SEASON).update();
        }
    }

    private RoundRescheduleRequest req(int round, LocalDate date, boolean following, boolean authorized, boolean dry) {
        return new RoundRescheduleRequest(round, date, following, authorized, "Federation request", true, dry);
    }

    private SetupDtos.RoundRescheduleResult move(RoundRescheduleRequest request) {
        return states.rescheduleRound(cs, request, "oidc-subject-r", AUTH, "USER");
    }

    private List<LocalDate> dates(int round) {
        return jdbc.sql("SELECT play_date FROM season_match_state WHERE competition_season_id = :cs AND round_no = :r ORDER BY home")
                .param("cs", cs).param("r", round).query(LocalDate.class).list();
    }

    private int audits() {
        return jdbc.sql("SELECT COUNT(*) FROM match_change_audit WHERE competition_season_id = :cs").param("cs", cs)
                .query(Integer.class).single();
    }

    private int officialDates(int round) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM round_date d JOIN league_round l ON l.id = d.league_round_id
                 WHERE l.competition_season_id = :cs AND l.round_no = :r""").param("cs", cs).param("r", round)
                .query(Integer.class).single();
    }

    @Test
    void movingOneRoundMovesOnlyThatRoundKeepsItsNumberAndTheOriginalDate() {
        LocalDate target = roundDate(2).plusDays(3);
        var result = move(req(2, target, false, false, false));

        assertTrue(result.applied());
        assertEquals(2, result.moves().size());
        assertEquals(List.of(target, target), dates(2));
        for (int r : new int[] {1, 3, 4, 5, 6}) {
            assertEquals(List.of(roundDate(r), roundDate(r)), dates(r), "round " + r + " must not move");
        }
        assertEquals(2, officialDates(2), "the original official date is kept and the new one added");
        assertEquals(2, audits());
        assertEquals(2, jdbc.sql("SELECT COUNT(*) FROM match_change_audit WHERE competition_season_id = :cs AND action = 'ROUND_APLAZADO'")
                .param("cs", cs).query(Integer.class).single());
    }

    @Test
    void advancingARoundIsAuditedAsAdelantado() {
        move(req(3, roundDate(3).minusDays(2), false, false, false));
        assertEquals(2, jdbc.sql("SELECT COUNT(*) FROM match_change_audit WHERE competition_season_id = :cs AND action = 'ROUND_ADELANTADO'")
                .param("cs", cs).query(Integer.class).single());
    }

    @Test
    void dryRunReportsTheMovesAndPersistsNothing() {
        var result = move(req(2, roundDate(2).plusDays(3), true, true, true));

        assertFalse(result.applied());
        assertTrue(result.dryRun());
        assertEquals(10, result.moves().size());
        assertTrue(result.conflicts().isEmpty());
        assertEquals(List.of(roundDate(2), roundDate(2)), dates(2));
        assertEquals(List.of(roundDate(6), roundDate(6)), dates(6));
        assertEquals(0, audits());
    }

    @Test
    void followingRoundsMoveOnlyWithExplicitAuthorizationAndNeverSilently() {
        assertThrows(SetupExceptions.Conflict.class, () -> move(req(3, roundDate(3).plusDays(3), true, false, false)));
        assertEquals(List.of(roundDate(3), roundDate(3)), dates(3));
        assertEquals(0, audits());

        var result = move(req(3, roundDate(3).plusDays(3), true, true, false));
        assertTrue(result.applied());
        assertEquals(8, result.moves().size());
        for (int r = 3; r <= 6; r++) {
            assertEquals(List.of(roundDate(r).plusDays(3), roundDate(r).plusDays(3)), dates(r), "round " + r);
        }
        assertEquals(List.of(roundDate(2), roundDate(2)), dates(2));
        assertEquals(8, audits());
    }

    @Test
    void deliveredOrPlayedMatchesBlockTheMoveInsteadOfBeingSkipped() {
        jdbc.sql("UPDATE season_match_state SET status = 'ENTREGADA' WHERE competition_season_id = :cs AND round_no = 4 AND home = :h")
                .param("cs", cs).param("h", team[1]).update();
        var single = move(req(4, roundDate(4).plusDays(3), false, false, false));
        assertFalse(single.applied());
        assertTrue(single.conflicts().stream().anyMatch(c -> c.contains("protected")), single.conflicts().toString());

        var cascade = move(req(3, roundDate(3).plusDays(3), true, true, false));
        assertFalse(cascade.applied());
        assertTrue(cascade.conflicts().stream().anyMatch(c -> c.startsWith("4 ") && c.contains("protected")));
        for (int r = 3; r <= 6; r++) {
            assertEquals(List.of(roundDate(r), roundDate(r)), dates(r));
        }
        assertEquals(0, audits());
    }

    @Test
    void pinnedMatchesBlockTheRoundMove() {
        states.reschedule(cs, new MatchRescheduleRequest(5, team[2], team[0], roundDate(5).plusDays(1), "18:00", "Pinned by request", true),
                "oidc-subject-r", AUTH, "USER");
        int before = audits();
        var result = move(req(5, roundDate(5).plusDays(7), false, false, false));
        assertFalse(result.applied());
        assertTrue(result.conflicts().stream().anyMatch(c -> c.contains("pinned")), result.conflicts().toString());
        assertEquals(before, audits());
    }

    @Test
    void conflictsAreDetectedBeforePersistingAndNothingIsSaved() {
        LocalDate target = roundDate(2).plusDays(3);
        jdbc.sql("INSERT INTO blackout (competition_season_id, label, date_from, date_to) VALUES (:cs, 'Mourning', :d, :d)")
                .param("cs", cs).param("d", target).update();
        var blackout = move(req(2, target, false, false, false));
        assertFalse(blackout.applied());
        assertEquals(2, blackout.conflicts().size());
        assertTrue(blackout.conflicts().get(0).contains("blackout"));

        var outsideLimit = move(req(2, roundDate(2).plusDays(31), false, false, false));
        assertFalse(outsideLimit.applied());
        assertTrue(outsideLimit.conflicts().stream().anyMatch(c -> c.contains("limit")), outsideLimit.conflicts().toString());

        // moving round 2 onto round 3's date overloads the daily capacity (max 4 -> allowed) but keeps teams apart? A-C,D-B vs A-D,B-C collide
        var teams = move(req(2, roundDate(3), false, false, false));
        assertFalse(teams.applied());
        assertTrue(teams.conflicts().stream().anyMatch(c -> c.contains("another fixture")), teams.conflicts().toString());

        assertEquals(List.of(roundDate(2), roundDate(2)), dates(2));
        assertEquals(0, audits());
    }

    @Test
    void requiresConfirmationActorAuthorityAndReason() {
        assertThrows(SetupExceptions.Invalid.class, () -> states.rescheduleRound(cs,
                new RoundRescheduleRequest(2, roundDate(2).plusDays(1), false, false, "reason", false, false), "a", AUTH, "USER"));
        assertThrows(SetupExceptions.Conflict.class, () -> states.rescheduleRound(cs,
                req(2, roundDate(2).plusDays(1), false, false, false), null, AUTH, "USER"));
        assertThrows(SetupExceptions.Conflict.class, () -> states.rescheduleRound(cs,
                req(2, roundDate(2).plusDays(1), false, false, false), "a", null, "USER"));
        assertThrows(SetupExceptions.Invalid.class, () -> states.rescheduleRound(cs,
                new RoundRescheduleRequest(2, roundDate(2).plusDays(1), false, false, " ", true, false), "a", AUTH, "USER"));
        assertThrows(SetupExceptions.Invalid.class, () -> move(req(2, roundDate(2), false, false, false)));
        assertEquals(0, audits());
    }

    @Test
    void individualMatchCanBeAdvancedOrPostponedKeepingItsRoundAndTheAuditAction() {
        states.reschedule(cs, new MatchRescheduleRequest(1, team[0], team[1], roundDate(1).plusDays(2), "20:00", "Postpone", true),
                "oidc-subject-r", AUTH, "USER");
        states.reschedule(cs, new MatchRescheduleRequest(1, team[2], team[3], roundDate(1).minusDays(2), "12:00", "Advance", true),
                "oidc-subject-r", AUTH, "USER");

        var all = states.list(cs).stream().filter(s -> s.round() == 1).toList();
        assertEquals(2, all.size());
        assertEquals(2, officialDates(1) - 1, "original official round date preserved, two new ones added");
        assertEquals(1, jdbc.sql("SELECT COUNT(*) FROM match_change_audit WHERE competition_season_id = :cs AND action = 'APLAZADO'")
                .param("cs", cs).query(Integer.class).single());
        assertEquals(1, jdbc.sql("SELECT COUNT(*) FROM match_change_audit WHERE competition_season_id = :cs AND action = 'ADELANTADO'")
                .param("cs", cs).query(Integer.class).single());
        assertEquals(List.of(roundDate(2), roundDate(2)), dates(2), "no silent shift of other rounds");
    }
}
