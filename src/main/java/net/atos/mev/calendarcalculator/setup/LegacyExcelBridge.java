package net.atos.mev.calendarcalculator.setup;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import net.atos.mev.calendarcalculator.schedules.LegacyResultFiles;
import net.atos.mev.calendarcalculator.schedules.Matchday;
import net.atos.mev.calendarcalculator.schedules.OtherCompetitionDay;
import net.atos.mev.calendarcalculator.schedules.SchCalendar;
import net.atos.mev.calendarcalculator.schedules.SchCalendarExcelIO;
import net.atos.mev.calendarcalculator.schedules.SchMatch;
import net.atos.mev.calendarcalculator.schedules.SetupCalendarBuilder;

/**
 * Puente OPCIONAL con el Excel antiguo (SCR-10). El motor no lo necesita.
 * <ul>
 *   <li>import = vista previa: devuelve el SeasonSetup con las fechas/horas del Excel aplicadas a los
 *       fixtures y los partidos de otras competiciones como externos. NO guarda (el front hace PUT).</li>
 *   <li>export = el calendario de la configuración actual en el formato del Excel legacy.</li>
 * </ul>
 */
@Component
public class LegacyExcelBridge {

    private final SetupToEngineMapper mapper;

    public LegacyExcelBridge(SetupToEngineMapper mapper) {
        this.mapper = mapper;
    }

    public SetupDtos.SeasonSetup importPreview(SetupDtos.SeasonSetup current, byte[] excel) {
        SetupCalendarBuilder.Built built = SetupCalendarBuilder.build(mapper.toInput(current, List.of(), null));
        SchCalendar cal = SchCalendarExcelIO.loadFromExcel(new ByteArrayInputStream(excel), built.environment());

        Map<String, SchMatch> placed = new HashMap<>();
        Map<String, List<LocalDate>> roundDates = new HashMap<>();
        List<SetupDtos.ExternalMatch> external = new ArrayList<>();
        for (Matchday md : cal.getMatchdays()) {
            for (SchMatch m : md.getMatches()) {
                placed.put(m.getCompetitionRound() + ":" + m.getTheMatch().getTeam1().getCode() + "," + m.getTheMatch().getTeam2().getCode(), m);
            }
            if (md.getMatches().isEmpty() && md.isAvailableForNewMatches() && md.getCalendarRound() != null) {
                roundDates.computeIfAbsent(md.getCalendarRound(), k -> new ArrayList<>()).add(md.getDate());
            }
            for (OtherCompetitionDay o : md.getOtherMatches()) {
                String home = o.hasMatchDetails() ? o.getTeamHome() : String.join(",", o.getTeams());
                external.add(new SetupDtos.ExternalMatch(null, o.getCompetition(), o.getCompetitionRound(), o.getDate(), o.getHour(), home,
                        o.hasMatchDetails() ? o.getTeamAway() : null));
            }
        }
        // fixtures: mismo sorteo; los colocados en el Excel van primero en su jornada y en el orden del Excel
        List<SetupDtos.Fixture> fixtures = new ArrayList<>();
        Map<Integer, List<SetupDtos.Fixture>> byRound = new HashMap<>();
        current.fixtures().forEach(f -> byRound.computeIfAbsent(f.round(), k -> new ArrayList<>()).add(f));
        for (int r = 1; r <= byRound.size(); r++) {
            List<SetupDtos.Fixture> round = byRound.getOrDefault(r, List.of());
            for (Matchday md : cal.getMatchdays()) {
                for (SchMatch m : md.getMatches()) {
                    if (m.getCompetitionRound().equals(String.valueOf(r))) {
                        boolean confirmed = m.isConfirmed();
                        fixtures.add(new SetupDtos.Fixture(null, r, m.getTheMatch().getTeam1().getCode(), m.getTheMatch().getTeam2().getCode(), true,
                                m.getDate(), confirmed ? m.getTime() : null));
                    }
                }
            }
            for (SetupDtos.Fixture f : round) {
                if (!placed.containsKey(r + ":" + f.home() + "," + f.away())) {
                    fixtures.add(new SetupDtos.Fixture(f.id(), r, f.home(), f.away(), false, null, null));
                }
            }
        }
        List<SetupDtos.Round> rounds = new ArrayList<>(current.rounds());
        roundDates.forEach((label, dates) -> {
            Integer n = label.matches("[0-9]+") ? Integer.valueOf(label) : null;
            rounds.removeIf(x -> n != null ? n.equals(x.n()) : label.equals(x.label()));
            rounds.add(new SetupDtos.Round(n, n == null ? label : null, null, dates));
        });
        rounds.sort((a, b) -> a.dates().get(0).compareTo(b.dates().get(0)));
        return new SetupDtos.SeasonSetup(current.competitionId(), current.code(), current.season(), current.general(), current.timeslots(),
                current.ramadan(), current.noLightSlot(), current.teams(), rounds, fixtures, external, current.blackouts(), current.restDays(),
                current.notHomeSameDay(), current.derbies(), current.rules(), current.dependsOn(), current.includeDependency(), current.engine(),
                current.rowVersion(), current.updatedAt());
    }

    public byte[] export(SetupDtos.SeasonSetup setup, List<SetupDtos.GeneratedMatch> dependency, String dependencyCode) {
        SetupCalendarBuilder.Built built = SetupCalendarBuilder.build(mapper.toInput(setup, dependency, dependencyCode), P2Settings.colours(setup));
        return LegacyResultFiles.fullCalendarXlsx(built.calendar(), built.environment());
    }
}
