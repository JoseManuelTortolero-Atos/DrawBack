package net.atos.mev.calendarcalculator.setup;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import net.atos.mev.calendarcalculator.schedules.PlanningParameters;

/** Los parámetros sacados del seed (BD) coinciden con los .properties legacy. Excepción documentada: D2 (GAP-04). */
class PlanningParametersTest {

    private final SeedFixture seed = SeedFixture.load();
    private final SetupToEngineMapper mapper = new SetupToEngineMapper();

    @Test
    void botolaD1() throws IOException {
        compare("BOTOLA_D1", "schBotolaD1/SchMoroccoD1.properties", true);
    }

    @Test
    void cnpffD1() throws IOException {
        compare("CNPFF_D1", "schCNPFF1/MoroccoCNPFF1.properties", true);
    }

    @Test
    void cnpffD2() throws IOException {
        compare("CNPFF_D2", "schCNPFF2/MoroccoCNPFF2.properties", true);
    }

    /** GAP-04: el parser legacy ignoraba teamsNotHomeSameDay.8–.24 de D2; el seed carga las 17 parejas. */
    @Test
    void botolaD2LoadsTheSeventeenPairsTheLegacyParserIgnored() throws IOException {
        PlanningParameters legacy = compare("BOTOLA_D2", "schBotolaD2/SchMoroccoD2.properties", false);
        PlanningParameters db = mapper.toParameters(seed.setup("BOTOLA_D2"), 5);
        assertEquals(0, legacy.teamsNotHomeSameDay().size());
        assertEquals(17, db.teamsNotHomeSameDay().size());
        assertEquals(List.of("USMO", "MCO"), db.teamsNotHomeSameDay().get(0));
        assertEquals(List.of("WST", "USYM"), db.teamsNotHomeSameDay().get(16));
    }

    private PlanningParameters compare(String competitionId, String file, boolean samePairs) throws IOException {
        Properties p = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(file)) {
            p.load(in);
        }
        PlanningParameters legacy = PlanningParameters.fromProperties(p, seed.code(competitionId));
        PlanningParameters db = mapper.toParameters(seed.setup(competitionId), legacy.lastRoundToAssign());
        assertEquals(legacy.lastRoundToAssign(), db.lastRoundToAssign());
        assertEquals(legacy.maxMatchesPerDay(), db.maxMatchesPerDay());
        assertEquals(legacy.groupsToRun(), db.groupsToRun());
        assertEquals(legacy.maxDaysAdvanceMatch(), db.maxDaysAdvanceMatch());
        assertEquals(legacy.maxDaysPostponeMatch(), db.maxDaysPostponeMatch());
        assertEquals(legacy.timeslots(), db.timeslots());
        assertEquals(legacy.groupToOptimizeTimes(), db.groupToOptimizeTimes());
        assertEquals(sorted(legacy.teamsToOptimizeTimes()), sorted(db.teamsToOptimizeTimes()), "same set (engine uses it as a set)");
        assertEquals(legacy.ramadanTimeslot(), db.ramadanTimeslot());
        assertEquals(legacy.ramadanStart(), db.ramadanStart());
        assertEquals(legacy.ramadanEnd(), db.ramadanEnd());
        assertEquals(sorted(legacy.teamsWithoutLight()), sorted(db.teamsWithoutLight()));
        assertEquals(legacy.timeslotWithoutLight(), db.timeslotWithoutLight());
        assertEquals(legacy.restDays(), db.restDays());
        assertEquals(List.copyOf(legacy.restDays().keySet()), List.copyOf(db.restDays().keySet()), "same competition order");
        assertEquals(legacy.assignGroups(), db.assignGroups());
        assertEquals(legacy.roundsForwardLookPerGroup(), db.roundsForwardLookPerGroup());
        assertEquals(legacy.timeAssignsForwardLook(), db.timeAssignsForwardLook());
        assertEquals(legacy.groupsCanGoToMAJ(), db.groupsCanGoToMAJ());
        assertArrayEquals(legacy.roundCalculationOrder(), db.roundCalculationOrder());
        assertEquals(legacy.startSecondLeg(), db.startSecondLeg());
        assertTrue(db.blackouts().isEmpty() && db.venueUnavailable().isEmpty() && db.fixedHomeDates().isEmpty());
        if (samePairs) {
            assertEquals(legacy.teamsNotHomeSameDay(), db.teamsNotHomeSameDay());
        }
        return legacy;
    }

    private static List<String> sorted(List<String> l) {
        return l.stream().sorted().toList();
    }
}
