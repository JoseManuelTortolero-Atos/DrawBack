package net.atos.mev.calendarcalculator.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.atos.mev.calendarcalculator.schedules.PlanningParameters;
import net.atos.mev.calendarcalculator.schedules.SeasonPlanningInput;

class SetupToEngineMapperTest {

    private final SeedFixture seed = SeedFixture.load();
    private final SetupToEngineMapper mapper = new SetupToEngineMapper();

    /** D2 desde SeasonSetup + D1 v0 aprobada = las filas D1 que el legacy leía del Excel de D2. */
    @Test
    void botolaD2TakesTheApprovedD1MatchesAsExternalCommitments() {
        SeasonPlanningInput in = mapper.toInput(seed.setup("BOTOLA_D2"), seed.approvedV0("BOTOLA_D1"), "D1");
        List<String> got = new ArrayList<>();
        in.external().forEach(e -> got.add(e.competition() + "," + e.date() + "," + e.hour() + "," + e.home() + "," + e.away()));
        List<String> legacy = new ArrayList<>();
        // outDatesD2.csv (resultado legacy de D2) lista las filas D1 que leyó del Excel de entrada
        seed.approvedV0("BOTOLA_D2").stream().filter(m -> m.competition().equals("D1"))
                .forEach(m -> legacy.add(m.competition() + "," + m.date() + "," + m.hour() + "," + m.home() + "," + m.away()));
        assertEquals(40, got.size());
        assertEquals(legacy, got);
    }

    @Test
    void databasePlanningMustFailWhenNoCompleteAssignmentExists() {
        assertTrue(mapper.toParameters(seed.setup("BOTOLA_D1"), 5).failOnNoFeasibleAssignment());
    }

    @Test
    void withoutIncludeDependencyNoExternalMatchesAreAdded() {
        SetupDtos.SeasonSetup s = seed.setup("BOTOLA_D2");
        SetupDtos.SeasonSetup noDep = new SetupDtos.SeasonSetup(s.competitionId(), s.code(), s.season(), s.general(), s.timeslots(), s.ramadan(),
                s.noLightSlot(), s.teams(), s.rounds(), s.fixtures(), s.external(), s.blackouts(), s.restDays(), s.notHomeSameDay(), s.derbies(),
                s.rules(), s.dependsOn(), false, s.engine(), s.rowVersion(), s.updatedAt());
        assertTrue(mapper.toInput(noDep, seed.approvedV0("BOTOLA_D1"), "D1").external().isEmpty());
    }

    @Test
    void newRulesAreMappedToTheEngine() {
        SetupDtos.SeasonSetup s = seed.setup("CNPFF_D1");
        List<SetupDtos.Rule> rules = new ArrayList<>(s.rules());
        rules.add(new SetupDtos.Rule(null, "VenueUnavailable", null, null, "WAC", null, null, null, null,
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), null, null, null));
        rules.add(new SetupDtos.Rule(null, "FixedHomeDate", null, null, "SCC", null, null, null, null,
                null, null, LocalDate.of(2026, 10, 17), null, null));
        SetupDtos.SeasonSetup withRules = new SetupDtos.SeasonSetup(s.competitionId(), s.code(), s.season(), s.general(), s.timeslots(), s.ramadan(),
                s.noLightSlot(), s.teams(), s.rounds(), s.fixtures(), s.external(),
                List.of(new SetupDtos.Blackout(null, "FIFA", LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 17))), s.restDays(),
                s.notHomeSameDay(), s.derbies(), rules, s.dependsOn(), s.includeDependency(), s.engine(), s.rowVersion(), s.updatedAt());
        PlanningParameters p = mapper.toParameters(withRules, 3);
        assertEquals(3, p.lastRoundToAssign());
        assertEquals(1, p.blackouts().size());
        assertEquals("WAC", p.venueUnavailable().get(0).team());
        assertEquals(LocalDate.of(2026, 10, 17), p.fixedHomeDates().get(0).date());
    }

    @Test
    void allowMajFalseDisablesPostponedRoundsForEveryGroup() {
        SetupDtos.SeasonSetup s = seed.setup("BOTOLA_D1");
        SetupDtos.General g = s.general();
        SetupDtos.General noMaj = new SetupDtos.General(g.seasonStart(), g.seasonEnd(), g.firstRoundDate(), g.startSecondLeg(), g.maxMatchesPerDay(),
                g.maxDaysAdvanceMatch(), g.maxDaysPostponeMatch(), false, g.roundsForwardLook(), g.timeAssignsForwardLook(), g.lastRoundToAssign(),
                g.matchdayPattern());
        SetupDtos.SeasonSetup x = new SetupDtos.SeasonSetup(s.competitionId(), s.code(), s.season(), noMaj, s.timeslots(), s.ramadan(), s.noLightSlot(),
                s.teams(), s.rounds(), s.fixtures(), s.external(), s.blackouts(), s.restDays(), s.notHomeSameDay(), s.derbies(), s.rules(),
                s.dependsOn(), s.includeDependency(), s.engine(), s.rowVersion(), s.updatedAt());
        assertEquals(List.of("0"), mapper.toParameters(x, 5).groupsCanGoToMAJ());
    }

    @Test
    void unsupportedPlanningRuleIsReportedInsteadOfBeingIgnored() {
        SetupDtos.SeasonSetup s = seed.setup("CNPFF_D1");
        List<SetupDtos.Rule> rules = new ArrayList<>(s.rules());
        rules.add(new SetupDtos.Rule(null, "UnsupportedPlanningRule", null, null, null, null, null, null, null,
                null, null, null, null, null));
        SetupDtos.SeasonSetup withRule = new SetupDtos.SeasonSetup(s.competitionId(), s.code(), s.season(), s.general(), s.timeslots(),
                s.ramadan(), s.noLightSlot(), s.teams(), s.rounds(), s.fixtures(), s.external(), s.blackouts(), s.restDays(),
                s.notHomeSameDay(), s.derbies(), rules, s.dependsOn(), s.includeDependency(), s.engine(), s.rowVersion(), s.updatedAt());
        List<SetupDtos.RuleTypeDef> definitions = rules.stream().map(SetupDtos.Rule::type).distinct()
                .map(type -> new SetupDtos.RuleTypeDef(type, type, "shield", "PLANNING", type, "", "PLANNING", List.of(),
                        "SUPPORTED", "", "v0.4", true, true, List.of())).toList();

        assertTrue(new SetupValidator(ignored -> definitions).validate(withRule, 3, true).stream()
                .anyMatch(issue -> "error".equals(issue.level()) && issue.msg().contains("no P2 planning handler")));
    }

    @Test
    void incompletePlanningRuleBlocksGeneration() {
        SetupDtos.SeasonSetup s = seed.setup("CNPFF_D1");
        List<SetupDtos.Rule> rules = new ArrayList<>(s.rules());
        rules.add(new SetupDtos.Rule(null, "VenueUnavailable", null, null, null, null, null, null, null,
                null, null, null, null, null));
        SetupDtos.SeasonSetup withRule = new SetupDtos.SeasonSetup(s.competitionId(), s.code(), s.season(), s.general(), s.timeslots(),
                s.ramadan(), s.noLightSlot(), s.teams(), s.rounds(), s.fixtures(), s.external(), s.blackouts(), s.restDays(),
                s.notHomeSameDay(), s.derbies(), rules, s.dependsOn(), s.includeDependency(), s.engine(), s.rowVersion(), s.updatedAt());
        List<SetupDtos.RuleTypeDef> definitions = rules.stream().map(SetupDtos.Rule::type).distinct()
                .map(type -> new SetupDtos.RuleTypeDef(type, type, "shield", "PLANNING", type, "", "PLANNING", List.of(),
                        "SUPPORTED", "", "v0.4", true, true, List.of())).toList();

        assertTrue(new SetupValidator(ignored -> definitions).validate(withRule, 3, true).stream()
                .anyMatch(issue -> "error".equals(issue.level()) && issue.msg().contains("VenueUnavailable incomplete")));
    }
}
