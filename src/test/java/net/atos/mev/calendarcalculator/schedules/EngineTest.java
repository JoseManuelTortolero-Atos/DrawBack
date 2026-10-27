package net.atos.mev.calendarcalculator.schedules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;

import net.atos.mev.calendarcalculator.setup.SeedFixture;
import net.atos.mev.calendarcalculator.setup.SetupToEngineMapper;

/**
 * Reglas nuevas del motor (cada una contra un caso de referencia sin la regla), espejo y cancelación.
 * Caso de referencia: F1 sin partidos colocados, jornadas 1–3 con fechas sábado/domingo.
 */
class EngineTest {

    private static final LocalDate SAT_17 = LocalDate.of(2026, 10, 17);
    private static final LocalDate SUN_18 = LocalDate.of(2026, 10, 18);
    private static final LocalDate SAT_24 = LocalDate.of(2026, 10, 24);

    private final SeedFixture seed = SeedFixture.load();
    private final SetupToEngineMapper mapper = new SetupToEngineMapper();

    private SeasonPlanningInput unplacedInput(String competitionId, int rounds, LocalDate firstSaturday) {
        SeasonPlanningInput base = mapper.toInput(seed.setup(competitionId), List.of(), null);
        List<SeasonPlanningInput.FixtureInput> fx = base.fixtures().stream()
                .map(f -> new SeasonPlanningInput.FixtureInput(f.round(), f.home(), f.away(), null, null, false)).toList();
        List<SeasonPlanningInput.RoundDates> rd = new ArrayList<>();
        LocalDate sat = firstSaturday;
        for (int r = 1; r <= rounds; r++) {
            rd.add(new SeasonPlanningInput.RoundDates(String.valueOf(r), List.of(sat, sat.plusDays(1))));
            sat = sat.plusWeeks(1);
        }
        return new SeasonPlanningInput(base.competitionCode(), base.competitionName(), base.seasonStart(), base.seasonEnd(), base.teams(), rd, fx, List.of());
    }

    private PlanningParameters.Builder f1Params() {
        PlanningParameters base = mapper.toParameters(seed.setup("CNPFF_D1"), 3);
        return PlanningParameters.builder().lastRoundToAssign(3).maxMatchesPerDay(6).groupsToRun(1)
                .maxDaysAdvanceMatch(base.maxDaysAdvanceMatch()).maxDaysPostponeMatch(base.maxDaysPostponeMatch())
                .timeslots(List.of("15:00", "17:00", "19:00")).groupToOptimizeTimes(1).teamsToOptimizeTimes(base.teamsToOptimizeTimes())
                .ramadan(base.ramadanStart(), base.ramadanEnd(), base.ramadanTimeslot()).teamsWithoutLight(List.of())
                .timeslotWithoutLight("15:00").restDays(base.restDays()).timeAssignsForwardLook(base.timeAssignsForwardLook())
                .groupsCanGoToMAJ(base.groupsCanGoToMAJ()).roundCalculationOrder(base.roundCalculationOrder())
                .startSecondLeg(base.startSecondLeg()).assignGroup(base.assignGroups().get(0), 2);
    }

    private List<SchMatch> run(PlanningParameters p) {
        SetupCalendarBuilder.Built b = SetupCalendarBuilder.build(unplacedInput("CNPFF_D1", 3, SAT_17));
        SchCalendar cal = new ScheduleMoroccoAlg(b.environment(), p).plan(b.calendar());
        List<SchMatch> all = new ArrayList<>();
        for (Matchday md : cal.getMatchdays()) {
            all.addAll(md.getMatches());
        }
        return all;
    }

    @Test
    void referenceWithoutNewRules() {
        List<SchMatch> ref = run(f1Params().build());
        assertEquals(18, ref.size());
        assertTrue(ref.stream().anyMatch(m -> m.getDate().equals(SAT_24)));
    }

    @Test
    void blackoutRemovesTheDay() {
        List<SchMatch> res = run(f1Params().blackout("FIFA window", SAT_24, SAT_24).build());
        assertEquals(18, res.size());
        assertTrue(res.stream().noneMatch(m -> m.getDate().equals(SAT_24)));
    }

    @Test
    void venueUnavailableKeepsTheTeamAwayFromHome() {
        String home = run(f1Params().build()).stream().filter(m -> m.getDate().equals(SAT_17)).findFirst().orElseThrow()
                .getTheMatch().getTeam1().getCode();
        List<SchMatch> res = run(f1Params().venueUnavailable(home, SAT_17, SAT_17).build());
        assertTrue(res.stream().noneMatch(m -> m.getDate().equals(SAT_17) && m.getTheMatch().getTeam1().getCode().equals(home)));
    }

    @Test
    void fixedHomeDateMovesTheMatch() {
        String home = run(f1Params().build()).stream().filter(m -> m.getDate().equals(SUN_18)).findFirst().orElseThrow()
                .getTheMatch().getTeam1().getCode();
        List<SchMatch> res = run(f1Params().fixedHomeDate(home, SAT_17).build());
        assertTrue(res.stream().anyMatch(m -> m.getDate().equals(SAT_17) && m.getTheMatch().getTeam1().getCode().equals(home)));
    }

    @Test
    void lockedFixtureKeepsItsOfficialDateAndHour() {
        SeasonPlanningInput base = unplacedInput("CNPFF_D1", 3, SAT_17);
        List<SeasonPlanningInput.FixtureInput> fixtures = new ArrayList<>(base.fixtures());
        int fixtureIndex = fixtures.indexOf(fixtures.stream().filter(f -> f.round() == 1).findFirst().orElseThrow());
        SeasonPlanningInput.FixtureInput official = fixtures.get(fixtureIndex);
        fixtures.set(fixtureIndex, new SeasonPlanningInput.FixtureInput(
                official.round(), official.home(), official.away(), SAT_17, "19:00", true));
        SeasonPlanningInput input = new SeasonPlanningInput(base.competitionCode(), base.competitionName(), base.seasonStart(),
                base.seasonEnd(), base.teams(), base.rounds(), fixtures, base.external());
        PlanningParameters parameters = f1Params().teamsWithoutLight(List.of(official.home()))
                .timeslotWithoutLight("15:00").build();

        SetupCalendarBuilder.Built built = SetupCalendarBuilder.build(input);
        SchCalendar result = new ScheduleMoroccoAlg(built.environment(), parameters).plan(built.calendar());
        SchMatch preserved = java.util.Arrays.stream(result.getMatchdays())
                .flatMap(day -> day.getMatches().stream())
                .filter(match -> match.getTheMatch().getTeam1().getCode().equals(official.home())
                        && match.getTheMatch().getTeam2().getCode().equals(official.away()))
                .findFirst().orElseThrow();

        assertEquals(SAT_17, preserved.getDate());
        assertEquals("19:00", preserved.getTime());
        assertTrue(preserved.isConfirmed());
    }

    @Test
    void incrementalPlanningOnlyAssignsRoundsInTheExplicitRangeAndIsIdempotent() {
        PlanningParameters parameters = f1Params().firstRoundToAssign(2).lastRoundToAssign(3).build();
        List<SchMatch> firstRun = run(parameters);
        List<SchMatch> secondRun = run(parameters);

        assertEquals(12, firstRun.size());
        assertEquals(java.util.Set.of("2", "3"), firstRun.stream().map(SchMatch::getCompetitionRound)
                .collect(java.util.stream.Collectors.toSet()));
        assertTrue(firstRun.stream().allMatch(match -> Integer.parseInt(match.getCompetitionRound()) >= 2
                && Integer.parseInt(match.getCompetitionRound()) <= 3));
        assertEquals(scheduleSignature(firstRun), scheduleSignature(secondRun));
    }

    @Test
    void incrementalSingleRoundLeavesEarlierAndLaterRoundsUnassigned() {
        List<SchMatch> matches = run(f1Params().firstRoundToAssign(2).lastRoundToAssign(2).build());

        assertEquals(6, matches.size());
        assertTrue(matches.stream().allMatch(match -> "2".equals(match.getCompetitionRound())));
    }

    private static List<String> scheduleSignature(List<SchMatch> matches) {
        return matches.stream().map(match -> match.getCompetitionRound() + "," + match.getDate() + ","
                + match.getTime() + "," + match.getTheMatch().getTeam1().getCode() + ","
                + match.getTheMatch().getTeam2().getCode()).sorted().toList();
    }

    @Test
    void secondLegMustBeAMirror() {
        SeasonPlanningInput in = unplacedInput("CNPFF_D1", 3, SAT_17);
        List<SeasonPlanningInput.FixtureInput> fx = new ArrayList<>(in.fixtures());
        int idx = 0;
        while (fx.get(idx).round() != 12) {
            idx++;
        }
        SeasonPlanningInput.FixtureInput f = fx.get(idx);
        fx.set(idx, new SeasonPlanningInput.FixtureInput(12, f.away(), f.home(), null, null, false));
        SeasonPlanningInput broken = new SeasonPlanningInput(in.competitionCode(), in.competitionName(), in.seasonStart(), in.seasonEnd(),
                in.teams(), in.rounds(), fx, in.external());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> SetupCalendarBuilder.build(broken));
        assertTrue(e.getMessage().contains("mirror"));
    }

    /** Hallazgo v0.4: D1 con 5 jornadas sin días fijados no termina (backtracking exponencial) → timeout y cancelación. */
    @Test
    void longRunCanBeCancelled() throws Exception {
        SetupCalendarBuilder.Built b = SetupCalendarBuilder.build(unplacedInput("BOTOLA_D1", 5, LocalDate.of(2026, 9, 26)));
        ScheduleMoroccoAlg alg = new ScheduleMoroccoAlg(b.environment(), mapper.toParameters(seed.setup("BOTOLA_D1"), 5));
        ExecutorService ex = Executors.newSingleThreadExecutor();
        try {
            Future<SchCalendar> f = ex.submit(() -> alg.plan(b.calendar()));
            // No depende de la velocidad de la máquina: cancela a los 100 ms.
            Thread.sleep(100);
            alg.cancel();
            try {
                f.get(10, TimeUnit.SECONDS);
                // Terminó antes de cancelar (máquina muy rápida): no hay nada que verificar.
                org.junit.jupiter.api.Assumptions.abort("plan finished before cancel()");
            } catch (ExecutionException ee) {
                assertInstanceOf(PlanningCancelledException.class, ee.getCause());
            }
        } finally {
            ex.shutdownNow();
        }
    }

    @Test
    void outsideSeasonDateIsRejected() {
        SeasonPlanningInput in = unplacedInput("CNPFF_D1", 1, LocalDate.of(2026, 9, 5));
        assertThrows(IllegalArgumentException.class, () -> SetupCalendarBuilder.build(in));
    }

}
