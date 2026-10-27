package net.atos.mev.calendarcalculator.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import net.atos.mev.calendarcalculator.setup.SetupDtos.Fixture;
import net.atos.mev.calendarcalculator.setup.SetupDtos.FixtureLockRequest;
import net.atos.mev.calendarcalculator.setup.SetupDtos.MatchRescheduleRequest;
import net.atos.mev.calendarcalculator.setup.SetupDtos.MatchStatusRequest;

@SpringBootTest
class MatchStateDatabaseIntegrationTest {

    private static final String COMPETITION = "BOTOLA_D1";
    private static final String SEASON = "2026-27";
    private static final String TEST_AUTHORITY = "ROLE_TEST_SCHEDULE_ADMIN";

    @Autowired
    JdbcClient jdbc;
    @Autowired
    SeasonSetupRepository setups;
    @Autowired
    MatchStateService states;
    @Autowired
    GenerationRepository generations;

    @Test
    @Transactional
    void generatedVersionPromotesPendingMatchesAndPreservesDeliveredAndPlayedMatches() {
        var fixtures = setups.load(COMPETITION, SEASON).fixtures();
        Fixture pending = fixtures.get(0);
        Fixture delivered = fixtures.get(1);
        Fixture played = fixtures.get(2);
        long cs = seasonId();
        LocalDate pendingDate = freeDate(pending.home(), pending.away());
        LocalDate deliveredDate = freeDate(delivered.home(), delivered.away());
        LocalDate playedDate = freeDate(played.home(), played.away());
        setState(cs, pending, MatchStateService.PENDING, null, null);
        setState(cs, delivered, MatchStateService.DELIVERED, deliveredDate, "18:00");
        setState(cs, played, MatchStateService.PLAYED, playedDate, "20:00");

        String competitionCode = setups.competitionCode(COMPETITION);
        UUID jobId = UUID.randomUUID();
        var version = states.saveGenerated(cs, competitionCode, pending.round(), jobId, "{}",
                List.of(
                        generated(pending, pendingDate, "19:00"),
                        generated(delivered, deliveredDate, "18:00"),
                        generated(played, playedDate, "20:00")),
                Map.of());

        Map<String, String> persistedStatuses = generations.matches(version.id()).stream()
                .collect(java.util.stream.Collectors.toMap(match -> match.home() + "-" + match.away(),
                        SetupDtos.GeneratedMatch::status));
        assertEquals(MatchStateService.PLANNED, persistedStatuses.get(pending.home() + "-" + pending.away()));
        assertEquals(MatchStateService.DELIVERED, persistedStatuses.get(delivered.home() + "-" + delivered.away()));
        assertEquals(MatchStateService.PLAYED, persistedStatuses.get(played.home() + "-" + played.away()));
        assertEquals(deliveredDate, getStateDate(cs, delivered));
        assertEquals(playedDate, getStateDate(cs, played));
        assertEquals(1, jdbc.sql("""
                SELECT COUNT(*) FROM match_change_audit
                 WHERE competition_season_id = :cs AND round_no = :round AND home = :home AND away = :away
                   AND actor_type = 'SYSTEM' AND actor = :actor AND authorized_role = 'INTERNAL:PLANNING_JOB'
                   AND action = 'PLANNING_GENERATED' AND previous_status = 'PENDIENTE' AND new_status = 'PLANIFICADA'""")
                .param("cs", cs).param("round", pending.round()).param("home", pending.home()).param("away", pending.away())
                .param("actor", "planning-job:" + jobId)
                .query(Integer.class).single());
    }

    @Test
    @Transactional
    void generationRejectsChangesToProtectedMatches() {
        Fixture fixture = firstFixture();
        long cs = seasonId();
        LocalDate currentDate = freeDate(fixture.home(), fixture.away());
        LocalDate attemptedDate = currentDate.plusDays(1);
        setState(cs, fixture, MatchStateService.DELIVERED, currentDate, "18:00");
        int versionsBefore = generations.versions(cs).size();

        assertThrows(SetupExceptions.Conflict.class, () -> states.saveGenerated(cs, setups.competitionCode(COMPETITION), fixture.round(),
                UUID.randomUUID(), "{}", List.of(generated(fixture, attemptedDate, "19:00")), Map.of()));

        assertEquals(currentDate, getStateDate(cs, fixture));
        assertEquals(versionsBefore, generations.versions(cs).size());
    }

    @Test
    void failedGenerationDoesNotPersistEarlierMatchUpdatesOrAudit() {
        List<Fixture> fixtures = setups.load(COMPETITION, SEASON).fixtures();
        Fixture pending = fixtures.get(0);
        Fixture delivered = fixtures.get(1);
        long cs = seasonId();
        LocalDate pendingDate = freeDate(pending.home(), pending.away());
        LocalDate officialDate = freeDate(delivered.home(), delivered.away());
        setState(cs, pending, MatchStateService.PENDING, null, null);
        setState(cs, delivered, MatchStateService.DELIVERED, officialDate, "18:00");
        int auditsBefore = auditCount(cs, pending);
        int versionsBefore = generations.versions(cs).size();

        assertThrows(SetupExceptions.Conflict.class, () -> states.saveGenerated(cs, setups.competitionCode(COMPETITION),
                pending.round(), UUID.randomUUID(), "{}", List.of(
                        generated(pending, pendingDate, "19:00"),
                        generated(delivered, officialDate.plusDays(1), "20:00")), Map.of()));

        assertEquals(MatchStateService.PENDING, matchState(cs, pending).status());
        assertEquals(null, matchState(cs, pending).date());
        assertEquals(auditsBefore, auditCount(cs, pending));
        assertEquals(versionsBefore, generations.versions(cs).size());
    }

    @Test
    @Transactional
    void stateTransitionsPersistActorAuthorizationAndBeforeAfterValues() {
        Fixture fixture = firstFixture();
        long cs = seasonId();
        LocalDate date = freeDate(fixture.home(), fixture.away());
        setState(cs, fixture, MatchStateService.PLANNED, date, "19:00");

        var delivered = states.changeStatus(cs,
                new MatchStatusRequest(fixture.round(), fixture.home(), fixture.away(), MatchStateService.DELIVERED, "Official delivery"),
                "oidc-subject-17", TEST_AUTHORITY, "USER");
        var played = states.changeStatus(cs,
                new MatchStatusRequest(fixture.round(), fixture.home(), fixture.away(), MatchStateService.PLAYED, "Result confirmed"),
                "oidc-subject-17", TEST_AUTHORITY, "USER");

        assertEquals(MatchStateService.DELIVERED, delivered.status());
        assertEquals(MatchStateService.PLAYED, played.status());
        assertEquals(2, jdbc.sql("""
                SELECT COUNT(*) FROM match_change_audit
                 WHERE competition_season_id = :cs AND round_no = :r AND home = :home AND away = :away
                   AND actor = :actor AND actor_type = 'USER' AND authorized_role = :authorization""")
                .param("cs", cs).param("r", fixture.round()).param("home", fixture.home()).param("away", fixture.away())
                .param("actor", "oidc-subject-17").param("authorization", TEST_AUTHORITY)
                .query(Integer.class).single());
        assertTrue(jdbc.sql("""
                SELECT COUNT(*) FROM match_change_audit
                 WHERE competition_season_id = :cs AND round_no = :r AND home = :home AND away = :away
                   AND previous_values LIKE '%PLANIFICADA%' AND new_values LIKE '%ENTREGADA%'""")
                .param("cs", cs).param("r", fixture.round()).param("home", fixture.home()).param("away", fixture.away())
                .query(Integer.class).single() > 0);
    }

    @Test
    @Transactional
    void finalStatesAreOverlaidAsLockedFixturesForTheNextPlanningRun() {
        Fixture fixture = firstFixture();
        long cs = seasonId();
        LocalDate date = freeDate(fixture.home(), fixture.away());
        setState(cs, fixture, MatchStateService.DELIVERED, date, "20:00");

        Fixture protectedFixture = states.protectFinalizedMatches(setups.load(COMPETITION, SEASON)).fixtures().stream()
                .filter(f -> f.round() == fixture.round() && f.home().equals(fixture.home()) && f.away().equals(fixture.away()))
                .findFirst().orElseThrow();

        assertTrue(protectedFixture.locked());
        assertEquals(date, protectedFixture.date());
        assertEquals("20:00", protectedFixture.hour());
    }

    @Test
    @Transactional
    void lockingFixtureCreatesMissingRoundCalendarEntry() {
        Fixture fixture = firstFixture();
        LocalDate date = freeDate(fixture.home(), fixture.away());

        setups.lockFixture(COMPETITION, SEASON,
                new FixtureLockRequest(fixture.home(), fixture.away(), date, "19:00", true));

        Fixture saved = setups.load(COMPETITION, SEASON).fixtures().stream()
                .filter(f -> f.round() == fixture.round() && f.home().equals(fixture.home()) && f.away().equals(fixture.away()))
                .findFirst().orElseThrow();
        assertTrue(saved.locked());
        assertEquals(date, saved.date());
        assertEquals(1, jdbc.sql("""
                SELECT COUNT(*) FROM round_date rd JOIN league_round lr ON lr.id = rd.league_round_id
                 WHERE lr.competition_season_id = :cs AND lr.round_no = :round AND rd.play_date = :date""")
                .param("cs", seasonId()).param("round", fixture.round()).param("date", date).query(Integer.class).single());
    }

    @Test
    @Transactional
    void plannedMatchCanReturnToPendingOnlyThroughAuditedCancellation() {
        Fixture fixture = firstFixture();
        Fixture untouched = setups.load(COMPETITION, SEASON).fixtures().get(1);
        long cs = seasonId();
        LocalDate date = freeDate(fixture.home(), fixture.away());
        LocalDate untouchedDate = freeDate(untouched.home(), untouched.away());
        setState(cs, fixture, MatchStateService.PLANNED, date, "19:00");
        setState(cs, untouched, MatchStateService.PLANNED, untouchedDate, "18:00");
        jdbc.sql("""
                UPDATE fixture SET locked = :locked, play_date = :date, play_hour = '19:00'
                 WHERE competition_season_id = :cs AND round_no = :round AND home = :home AND away = :away""")
                .param("locked", true).param("date", date).param("cs", cs).param("round", fixture.round())
                .param("home", fixture.home()).param("away", fixture.away()).update();
        int auditsBefore = auditCount(cs, fixture);

        var pending = states.cancelPlanning(cs,
                new SetupDtos.MatchPlanCancellationRequest(fixture.round(), fixture.home(), fixture.away(), "Planning cancelled"),
                "human-subject-12", TEST_AUTHORITY, "USER");

        assertEquals(MatchStateService.PENDING, pending.status());
        assertEquals(null, pending.date());
        assertEquals(1, jdbc.sql("""
                SELECT COUNT(*) FROM fixture
                 WHERE competition_season_id = :cs AND round_no = :round AND home = :home AND away = :away
                   AND locked = :locked AND play_date IS NULL AND play_hour IS NULL""")
                .param("cs", cs).param("round", fixture.round()).param("home", fixture.home()).param("away", fixture.away())
                .param("locked", false)
                .query(Integer.class).single());
        assertEquals(untouchedDate, getStateDate(cs, untouched));
        assertEquals(auditsBefore + 1, auditCount(cs, fixture));
        assertEquals(1, jdbc.sql("""
                SELECT COUNT(*) FROM match_change_audit
                 WHERE competition_season_id = :cs AND round_no = :round AND home = :home AND away = :away
                   AND actor = 'human-subject-12' AND actor_type = 'USER'
                   AND authorized_role = :authority AND action = 'PLANNING_CANCELLED'
                   AND previous_status = 'PLANIFICADA' AND new_status = 'PENDIENTE'
                   AND reason = 'Planning cancelled'
                   AND previous_values LIKE '%"locked":true%'
                   AND new_values LIKE '%"locked":false%'""")
                .param("cs", cs).param("round", fixture.round()).param("home", fixture.home()).param("away", fixture.away())
                .param("authority", TEST_AUTHORITY).query(Integer.class).single());
    }

    @Test
    @Transactional
    void planCancellationRejectsDeliveredAndPlayedMatches() {
        Fixture fixture = firstFixture();
        long cs = seasonId();
        LocalDate date = freeDate(fixture.home(), fixture.away());
        for (String status : List.of(MatchStateService.DELIVERED, MatchStateService.PLAYED)) {
            setState(cs, fixture, status, date, "19:00");
            assertThrows(SetupExceptions.Conflict.class, () -> states.cancelPlanning(cs,
                    new SetupDtos.MatchPlanCancellationRequest(fixture.round(), fixture.home(), fixture.away(), "Invalid cancellation"),
                    "human-subject-12", TEST_AUTHORITY, "USER"));
            assertEquals(status, matchState(cs, fixture).status());
        }
    }

    @Test
    @Transactional
    void planCancellationRequiresActorAuthorityAndReason() {
        Fixture fixture = firstFixture();
        long cs = seasonId();
        setState(cs, fixture, MatchStateService.PLANNED, freeDate(fixture.home(), fixture.away()), "19:00");
        var request = new SetupDtos.MatchPlanCancellationRequest(fixture.round(), fixture.home(), fixture.away(), "Cancellation");
        assertThrows(SetupExceptions.Conflict.class, () -> states.cancelPlanning(cs, request, null, TEST_AUTHORITY, "USER"));
        assertThrows(SetupExceptions.Conflict.class, () -> states.cancelPlanning(cs, request, "subject", null, "USER"));
        assertThrows(SetupExceptions.Invalid.class, () -> states.cancelPlanning(cs,
                new SetupDtos.MatchPlanCancellationRequest(fixture.round(), fixture.home(), fixture.away(), " "),
                "subject", TEST_AUTHORITY, "USER"));
        assertEquals(MatchStateService.PLANNED, matchState(cs, fixture).status());
    }

    @Test
    @Transactional
    void serviceCancellationIsAuditedWithServiceIdentity() {
        Fixture fixture = firstFixture();
        long cs = seasonId();
        setState(cs, fixture, MatchStateService.PLANNED, freeDate(fixture.home(), fixture.away()), "19:00");

        states.cancelPlanning(cs,
                new SetupDtos.MatchPlanCancellationRequest(fixture.round(), fixture.home(), fixture.away(), "Automated reconciliation"),
                "oidc-service:calendar-planning-service", "ROLE_TEST_SERVICE", "SERVICE");

        assertEquals(1, jdbc.sql("""
                SELECT COUNT(*) FROM match_change_audit
                 WHERE competition_season_id = :cs AND round_no = :round AND home = :home AND away = :away
                   AND actor = 'oidc-service:calendar-planning-service' AND actor_type = 'SERVICE'
                   AND authorized_role = 'ROLE_TEST_SERVICE' AND action = 'PLANNING_CANCELLED'""")
                .param("cs", cs).param("round", fixture.round()).param("home", fixture.home()).param("away", fixture.away())
                .query(Integer.class).single());
    }

    @Test
    @Transactional
    void setupReplacementCannotSilentlyCancelOrMovePlannedMatches() {
        var setup = setups.load(COMPETITION, SEASON);
        Fixture moved = setup.fixtures().get(0);
        Fixture delivered = setup.fixtures().get(1);
        Fixture reset = setup.fixtures().get(2);
        long cs = seasonId();
        LocalDate start = setup.general().seasonStart();
        LocalDate movedDate = start.plusDays(2);
        LocalDate deliveredDate = start.plusDays(3);
        LocalDate originalMovedDate = start.plusDays(1);
        LocalDate resetDate = start.plusDays(4);
        setState(cs, moved, MatchStateService.PLANNED, originalMovedDate, "18:00");
        setState(cs, delivered, MatchStateService.DELIVERED, deliveredDate, "19:00");
        setState(cs, reset, MatchStateService.PLANNED, resetDate, "20:00");

        List<Fixture> fixtures = new ArrayList<>(setup.fixtures());
        fixtures.set(0, new Fixture(moved.id(), moved.round(), moved.home(), moved.away(), true, movedDate, "18:30"));
        fixtures.set(1, new Fixture(delivered.id(), delivered.round(), delivered.home(), delivered.away(), true, deliveredDate, "19:00"));
        fixtures.set(2, new Fixture(reset.id(), reset.round(), reset.home(), reset.away(), false, null, null));
        var replacement = new SetupDtos.SeasonSetup(setup.competitionId(), setup.code(), setup.season(), setup.general(),
                setup.timeslots(), setup.ramadan(), setup.noLightSlot(), setup.teams(), setup.rounds(), fixtures, setup.external(),
                setup.blackouts(), setup.restDays(), setup.notHomeSameDay(), setup.derbies(), setup.rules(), setup.dependsOn(),
                setup.includeDependency(), setup.engine(), setup.rowVersion(), setup.updatedAt());

        assertThrows(SetupExceptions.Conflict.class, () -> setups.replace(replacement));
        assertEquals(MatchStateService.PLANNED, matchState(cs, moved).status());
        assertEquals(originalMovedDate, matchState(cs, moved).date());
        assertEquals(MatchStateService.PLANNED, matchState(cs, reset).status());
        assertEquals(resetDate, matchState(cs, reset).date());
        assertEquals(MatchStateService.DELIVERED, matchState(cs, delivered).status());
    }

    @Test
    @Transactional
    void rescheduleRejectsD2HomePairOnTheSameDate() {
        String competition = "BOTOLA_D2";
        long cs = setups.competitionSeasonId(competition, SEASON);
        var setup = setups.load(competition, SEASON);
        Map<String, Object> pair = jdbc.sql("""
                SELECT team_a, team_b FROM team_pair_rule
                 WHERE competition_season_id = :cs AND kind = 'NOT_HOME_SAME_DAY'
                 ORDER BY list_order""")
                .param("cs", cs).query().listOfRows().get(0);
        String teamA = (String) pair.get("team_a");
        String teamB = (String) pair.get("team_b");
        Fixture moving = setup.fixtures().stream().filter(f -> teamA.equals(f.home()) && !teamB.equals(f.away()))
                .findFirst().orElseThrow();
        Fixture pairedHome = setup.fixtures().stream()
                .filter(f -> teamB.equals(f.home()) && !Set.of(moving.home(), moving.away()).contains(f.away()))
                .findFirst().orElseThrow();
        LocalDate targetDate = freeDateForTeams(cs, setup,
                List.of(moving.home(), moving.away(), pairedHome.home(), pairedHome.away()));
        LocalDate currentDate = targetDate.minusDays(1);
        jdbc.sql("""
                UPDATE competition_season
                   SET max_matches_per_day = 20, max_days_advance_match = 365, max_days_postpone_match = 365
                 WHERE id = :cs""")
                .param("cs", cs).update();
        setState(cs, moving, MatchStateService.DELIVERED, currentDate, "15:00");
        setState(cs, pairedHome, MatchStateService.PLANNED, targetDate, "17:00");
        int auditsBefore = auditCount(cs, moving);

        assertThrows(SetupExceptions.Conflict.class, () -> states.reschedule(cs,
                new MatchRescheduleRequest(moving.round(), moving.home(), moving.away(), targetDate, "19:00",
                        "Conflicting home pair", true),
                "oidc-subject-d2", TEST_AUTHORITY, "USER"));

        assertEquals(currentDate, getStateDate(cs, moving));
        assertEquals(auditsBefore, auditCount(cs, moving));
    }

    @Test
    @Transactional
    void rescheduleRejectsDailyCapacityOverflow() {
        var fixtures = setups.load(COMPETITION, SEASON).fixtures();
        Fixture moving = fixtures.get(0);
        Fixture existing = fixtures.stream()
                .filter(f -> !f.equals(moving)
                        && !Set.of(moving.home(), moving.away()).contains(f.home())
                        && !Set.of(moving.home(), moving.away()).contains(f.away()))
                .findFirst().orElseThrow();
        long cs = seasonId();
        LocalDate targetDate = freeDate(moving.home(), moving.away());
        LocalDate currentDate = targetDate.minusDays(1);
        jdbc.sql("""
                UPDATE competition_season
                   SET max_matches_per_day = 1, max_days_advance_match = 365, max_days_postpone_match = 365
                 WHERE id = :cs""")
                .param("cs", cs).update();
        setState(cs, moving, MatchStateService.DELIVERED, currentDate, "15:00");
        setState(cs, existing, MatchStateService.PLANNED, targetDate, "17:00");
        int auditsBefore = auditCount(cs, moving);

        assertThrows(SetupExceptions.Conflict.class, () -> states.reschedule(cs,
                new MatchRescheduleRequest(moving.round(), moving.home(), moving.away(), targetDate, "19:00",
                        "Daily limit check", true),
                "oidc-subject-capacity", TEST_AUTHORITY, "USER"));

        assertEquals(currentDate, getStateDate(cs, moving));
        assertEquals(auditsBefore, auditCount(cs, moving));
    }

    @Test
    @Transactional
    void rescheduleRejectsDateThatViolatesFixedHomeDate() {
        Fixture fixture = firstFixture();
        long cs = seasonId();
        LocalDate targetDate = freeDate(fixture.home(), fixture.away());
        LocalDate currentDate = targetDate.minusDays(1);
        jdbc.sql("""
                UPDATE competition_season
                   SET max_days_advance_match = 365, max_days_postpone_match = 365
                 WHERE id = :cs""")
                .param("cs", cs).update();
        jdbc.sql("""
                INSERT INTO constraint_rule (competition_season_id, list_order, rule_type, team, fixed_date)
                VALUES (:cs, 1000, 'FixedHomeDate', :home, :fixed)""")
                .param("cs", cs).param("home", fixture.home()).param("fixed", currentDate).update();
        setState(cs, fixture, MatchStateService.DELIVERED, currentDate, "15:00");
        int auditsBefore = auditCount(cs, fixture);

        assertThrows(SetupExceptions.Conflict.class, () -> states.reschedule(cs,
                new MatchRescheduleRequest(fixture.round(), fixture.home(), fixture.away(), targetDate, "19:00",
                        "Fixed date conflict", true),
                "oidc-subject-fixed-date", TEST_AUTHORITY, "USER"));

        assertEquals(currentDate, getStateDate(cs, fixture));
        assertEquals(auditsBefore, auditCount(cs, fixture));
    }

    @Test
    @Transactional
    void rescheduleConflictDoesNotChangeTheProtectedMatchOrCreateAudit() {
        var setup = setups.load(COMPETITION, SEASON);
        Fixture protectedFixture = setup.fixtures().get(0);
        Fixture sameTeam = setup.fixtures().stream()
                .filter(f -> f.round() != protectedFixture.round()
                        && (f.home().equals(protectedFixture.home()) || f.away().equals(protectedFixture.home())
                                || f.home().equals(protectedFixture.away()) || f.away().equals(protectedFixture.away())))
                .findFirst().orElseThrow();
        long cs = seasonId();
        LocalDate targetDate = freeDate(protectedFixture.home(), protectedFixture.away());
        LocalDate oldDate = targetDate.minusDays(1);
        jdbc.sql("UPDATE competition_season SET max_days_advance_match = 365, max_days_postpone_match = 365 WHERE id = :cs")
                .param("cs", cs).update();
        setState(cs, protectedFixture, MatchStateService.DELIVERED, oldDate, "15:00");
        setState(cs, sameTeam, MatchStateService.PLANNED, targetDate, "17:00");
        int auditsBefore = auditCount(cs, protectedFixture);

        assertThrows(SetupExceptions.Conflict.class, () -> states.reschedule(cs,
                new MatchRescheduleRequest(protectedFixture.round(), protectedFixture.home(), protectedFixture.away(),
                        targetDate, "19:00", "TV change", true),
                "oidc-subject-22", TEST_AUTHORITY, "USER"));

        assertEquals(oldDate, getStateDate(cs, protectedFixture));
        assertEquals(auditsBefore, auditCount(cs, protectedFixture));
    }

    @Test
    @Transactional
    void exceptionalDeliveredRescheduleIsAuditedAndPlayedMatchCannotMove() {
        Fixture fixture = firstFixture();
        long cs = seasonId();
        LocalDate targetDate = freeDate(fixture.home(), fixture.away());
        LocalDate oldDate = targetDate.minusDays(1);
        jdbc.sql("UPDATE competition_season SET max_days_advance_match = 365, max_days_postpone_match = 365 WHERE id = :cs")
                .param("cs", cs).update();
        setState(cs, fixture, MatchStateService.DELIVERED, oldDate, "15:00");

        var moved = states.reschedule(cs,
                new MatchRescheduleRequest(fixture.round(), fixture.home(), fixture.away(), targetDate, "19:00", "Broadcast request", true),
                "oidc-subject-31", TEST_AUTHORITY, "USER");

        assertEquals(MatchStateService.DELIVERED, moved.status());
        assertEquals(targetDate, moved.date());
        assertEquals(1, jdbc.sql("""
                SELECT COUNT(*) FROM match_change_audit
                 WHERE competition_season_id = :cs AND round_no = :r AND home = :home AND away = :away
                   AND action = 'APLAZADO' AND reason = :reason AND previous_status = 'ENTREGADA' AND new_status = 'ENTREGADA'""")
                .param("cs", cs).param("r", fixture.round()).param("home", fixture.home()).param("away", fixture.away())
                .param("reason", "Broadcast request").query(Integer.class).single());

        setState(cs, fixture, MatchStateService.PLAYED, targetDate, "19:00");
        assertThrows(SetupExceptions.Conflict.class, () -> states.reschedule(cs,
                new MatchRescheduleRequest(fixture.round(), fixture.home(), fixture.away(), oldDate, "18:00", "Historical correction", true),
                "oidc-subject-31", TEST_AUTHORITY, "USER"));
    }

    private Fixture firstFixture() {
        return setups.load(COMPETITION, SEASON).fixtures().get(0);
    }

    private SetupDtos.GeneratedMatch generated(Fixture fixture, LocalDate date, String hour) {
        return new SetupDtos.GeneratedMatch(setups.competitionCode(COMPETITION), String.valueOf(fixture.round()), date, hour, fixture.home(),
                fixture.away());
    }

    private long seasonId() {
        return jdbc.sql("SELECT id FROM competition_season WHERE competition_id = :c AND season_id = :s")
                .param("c", COMPETITION).param("s", SEASON).query(Long.class).single();
    }

    private void setState(long cs, Fixture fixture, String status, LocalDate date, String hour) {
        jdbc.sql("""
                UPDATE season_match_state SET status = :status, play_date = :date, play_hour = :hour
                 WHERE competition_season_id = :cs AND round_no = :round AND home = :home AND away = :away""")
                .param("status", status).param("date", date).param("hour", hour).param("cs", cs).param("round", fixture.round())
                .param("home", fixture.home()).param("away", fixture.away()).update();
    }

    private LocalDate freeDate(String home, String away) {
        var setup = setups.load(COMPETITION, SEASON);
        return freeDateForTeams(seasonId(), setup, List.of(home, away));
    }

    private LocalDate freeDateForTeams(long cs, SetupDtos.SeasonSetup setup, List<String> teams) {
        for (LocalDate candidate = setup.general().seasonStart().plusDays(1); !candidate.isAfter(setup.general().seasonEnd()); candidate = candidate.plusDays(1)) {
            int scheduled = 0;
            for (String team : teams) {
                scheduled += jdbc.sql("""
                    SELECT COUNT(*) FROM season_match_state
                     WHERE competition_season_id = :cs AND play_date = :date AND status <> 'PENDIENTE'
                       AND (home = :team OR away = :team)""")
                    .param("cs", cs).param("date", candidate).param("team", team)
                    .query(Integer.class).single();
                scheduled += jdbc.sql("""
                    SELECT COUNT(*) FROM external_commitment
                     WHERE competition_season_id = :cs AND play_date = :date
                       AND (home = :team OR away = :team)""")
                    .param("cs", cs).param("date", candidate).param("team", team)
                    .query(Integer.class).single();
            }
            int blackout = jdbc.sql("""
                    SELECT COUNT(*) FROM blackout
                     WHERE competition_season_id = :cs AND date_from <= :date AND date_to >= :date""")
                    .param("cs", cs).param("date", candidate).query(Integer.class).single();
            int venue = 0;
            for (Fixture fixture : setup.fixtures()) {
                if (!teams.contains(fixture.home())) continue;
                venue += jdbc.sql("""
                    SELECT COUNT(*) FROM constraint_rule
                     WHERE competition_season_id = :cs AND rule_type = 'VenueUnavailable' AND team = :home
                       AND date_from <= :date AND COALESCE(date_to, date_from) >= :date""")
                    .param("cs", cs).param("home", fixture.home()).param("date", candidate).query(Integer.class).single();
            }
            if (scheduled == 0 && blackout == 0 && venue == 0) return candidate;
        }
        throw new AssertionError("No conflict-free date found in the disposable test season");
    }

    private LocalDate getStateDate(long cs, Fixture fixture) {
        return jdbc.sql("""
                SELECT play_date FROM season_match_state
                 WHERE competition_season_id = :cs AND round_no = :round AND home = :home AND away = :away""")
                .param("cs", cs).param("round", fixture.round()).param("home", fixture.home()).param("away", fixture.away())
                .query(LocalDate.class).single();
    }

    private SetupDtos.MatchState matchState(long cs, Fixture fixture) {
        return states.list(cs).stream()
                .filter(state -> state.round() == fixture.round() && state.home().equals(fixture.home())
                        && state.away().equals(fixture.away()))
                .findFirst().orElseThrow();
    }

    private int auditCount(long cs, Fixture fixture) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM match_change_audit
                 WHERE competition_season_id = :cs AND round_no = :round AND home = :home AND away = :away""")
                .param("cs", cs).param("round", fixture.round()).param("home", fixture.home()).param("away", fixture.away())
                .query(Integer.class).single();
    }
}
