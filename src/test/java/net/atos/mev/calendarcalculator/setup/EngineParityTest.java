package net.atos.mev.calendarcalculator.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.atos.mev.calendarcalculator.schedules.Matchday;
import net.atos.mev.calendarcalculator.schedules.SchCalendar;
import net.atos.mev.calendarcalculator.schedules.SchEnvironment;
import net.atos.mev.calendarcalculator.schedules.SchMatch;
import net.atos.mev.calendarcalculator.schedules.ScheduleMoroccoAlg;
import net.atos.mev.calendarcalculator.schedules.SetupCalendarBuilder;

/**
 * Paridad: D1, D2, F1 y F2 generados desde los datos del seed (= BD) dan EXACTAMENTE las mismas
 * fechas, horas y orden que los resultados legacy data/results/2026-27/*-v0.
 */
class EngineParityTest {

    private final SeedFixture seed = SeedFixture.load();
    private final SetupToEngineMapper mapper = new SetupToEngineMapper();

    @Test
    void botolaD1MatchesLegacyV0() {
        assertParity("BOTOLA_D1", null);
    }

    @Test
    void botolaD2WithApprovedD1MatchesLegacyV0() {
        assertParity("BOTOLA_D2", "BOTOLA_D1");
    }

    @Test
    void cnpffD1MatchesLegacyV0() {
        assertParity("CNPFF_D1", null);
    }

    @Test
    void cnpffD2WithApprovedF1MatchesLegacyV0() {
        assertParity("CNPFF_D2", "CNPFF_D1");
    }

    private void assertParity(String competitionId, String dependsOn) {
        SetupDtos.SeasonSetup setup = seed.setup(competitionId);
        List<SetupDtos.GeneratedMatch> dep = dependsOn == null ? List.of() : seed.approvedV0(dependsOn);
        SetupToEngineMapper.Planning planning = mapper.map(setup, dep, dependsOn == null ? null : seed.code(dependsOn), null);
        SetupCalendarBuilder.Built built = SetupCalendarBuilder.build(planning.input());
        SchCalendar result = new ScheduleMoroccoAlg(built.environment(), planning.parameters()).plan(built.calendar());

        List<String> got = new ArrayList<>();
        for (Matchday md : result.getMatchdays()) {
            for (SchMatch m : md.getMatches()) {
                got.add(m.getCompetitionRound() + "," + SchEnvironment.writeDate(m.getDate()) + "," + m.getTime() + ","
                        + m.getTheMatch().getTeam1().getCode() + "," + m.getTheMatch().getTeam2().getCode());
            }
        }
        List<String> expected = new ArrayList<>();
        for (SetupDtos.GeneratedMatch m : seed.approvedV0(competitionId)) {
            if (m.competition().equals(setup.code())) {
                expected.add(m.round() + "," + SchEnvironment.writeDate(m.date()) + "," + m.hour() + "," + m.home() + "," + m.away());
            }
        }
        assertEquals(expected, got, competitionId + " must match legacy v0 exactly (dates, hours and order)");
    }
}
