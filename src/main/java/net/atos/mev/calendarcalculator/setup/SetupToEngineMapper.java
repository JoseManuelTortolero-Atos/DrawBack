package net.atos.mev.calendarcalculator.setup;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.atos.mev.calendarcalculator.schedules.PlanningParameters;
import net.atos.mev.calendarcalculator.schedules.SeasonPlanningInput;
import net.atos.mev.calendarcalculator.setup.SetupDtos.SeasonSetup;

/**
 * Traduce la configuración guardada en BD ({@link SeasonSetup}) a la entrada del motor.
 * Java puro (sin Spring) para poder probarlo sin contexto; se registra como bean en {@code SetupConfig}.
 */
public class SetupToEngineMapper {

    /** Resultado del mapeo: entrada y parámetros listos para {@code ScheduleMoroccoAlg}. */
    public record Planning(SeasonPlanningInput input, PlanningParameters parameters) {
    }

    /**
     * @param setup             configuración de la competición y temporada
     * @param dependencyMatches partidos de la versión APROBADA de la liga de la que se depende
     *                          (D2 → D1, F2 → F1); se ignoran si {@code includeDependency} es false
     * @param dependencyCode    código de esa liga (D1, F1); null si no hay dependencia
     * @param lastRoundToAssign última jornada pedida en el job (null = la guardada)
     */
    public Planning map(SeasonSetup setup, List<SetupDtos.GeneratedMatch> dependencyMatches, String dependencyCode, Integer lastRoundToAssign) {
        int last = lastRoundToAssign != null ? lastRoundToAssign : setup.general().lastRoundToAssign();
        return new Planning(toInput(setup, dependencyMatches, dependencyCode), toParameters(setup, 1, last));
    }

    public Planning map(SeasonSetup setup, List<SetupDtos.GeneratedMatch> dependencyMatches, String dependencyCode,
            int firstRoundToAssign, int lastRoundToAssign) {
        return new Planning(toInput(setup, dependencyMatches, dependencyCode),
                toParameters(setup, firstRoundToAssign, lastRoundToAssign));
    }

    public PlanningParameters toParameters(SeasonSetup s, int lastRoundToAssign) {
        return toParameters(s, 1, lastRoundToAssign);
    }

    public PlanningParameters toParameters(SeasonSetup s, int firstRoundToAssign, int lastRoundToAssign) {
        SetupDtos.General g = s.general();
        int totalRounds = 2 * (s.teams().size() - 1);
        PlanningParameters.Builder b = PlanningParameters.builder()
                .firstRoundToAssign(firstRoundToAssign)
                .lastRoundToAssign(lastRoundToAssign)
                .failOnNoFeasibleAssignment(true)
                .maxMatchesPerDay(g.maxMatchesPerDay())
                .maxDaysAdvanceMatch(g.maxDaysAdvanceMatch())
                .maxDaysPostponeMatch(g.maxDaysPostponeMatch())
                .timeslots(s.timeslots())
                .teamsToOptimizeTimes(s.teams().stream().filter(SetupDtos.Team::optimizeTimes).map(SetupDtos.Team::code).toList())
                .teamsWithoutLight(s.teams().stream().filter(SetupDtos.Team::noLight).map(SetupDtos.Team::code).toList())
                .timeslotWithoutLight(s.noLightSlot())
                .restDays(s.restDays() == null ? Map.of() : new LinkedHashMap<>(s.restDays()))
                .teamsNotHomeSameDay(s.notHomeSameDay() == null ? List.of() : s.notHomeSameDay())
                .timeAssignsForwardLook(g.timeAssignsForwardLook())
                .startSecondLeg(g.startSecondLeg());
        if (s.ramadan() != null) {
            b.ramadan(s.ramadan().start(), s.ramadan().end(), s.ramadan().slot());
        }
        SetupDtos.EngineSettings e = s.engine();
        if (e != null && e.assignGroups() != null && !e.assignGroups().isEmpty()) {
            b.groupsToRun(e.groupsToRun()).groupToOptimizeTimes(e.groupToOptimizeTimes());
            for (SetupDtos.AssignGroup ag : e.assignGroups()) {
                int look = ag.groupNo() == 1 ? g.roundsForwardLook() : ag.roundsForwardLook();
                b.assignGroup(java.util.Arrays.stream(ag.teams().split(",")).map(String::trim).toList(), look);
            }
            b.groupsCanGoToMAJ(java.util.Arrays.stream(majFlag(e.groupsCanGoToMAJ(), g.allowMAJ()).split(",")).map(String::trim).toList());
            b.roundCalculationOrder(parseOrder(e.roundCalculationOrder(), totalRounds));
        } else {
            b.groupsToRun(1).groupToOptimizeTimes(1)
                    .assignGroup(s.teams().stream().map(SetupDtos.Team::code).toList(), g.roundsForwardLook())
                    .groupsCanGoToMAJ(List.of(g.allowMAJ() ? "1" : "0"))
                    .roundCalculationOrder(parseOrder(null, totalRounds));
        }
        if (s.blackouts() != null) {
            s.blackouts().forEach(x -> b.blackout(x.label(), x.from(), x.to()));
        }
        if (s.rules() != null) {
            for (SetupDtos.Rule r : s.rules()) {
                if ("VenueUnavailable".equals(r.type()) && r.team() != null && r.from() != null) {
                    b.venueUnavailable(r.team(), r.from(), r.to() == null ? r.from() : r.to());
                } else if ("FixedHomeDate".equals(r.type()) && r.team() != null && r.date() != null) {
                    b.fixedHomeDate(r.team(), r.date());
                }
            }
        }
        return b.build();
    }

    /** allowMAJ manda: si se desactiva, ningún grupo puede ir a aplazados. */
    private static String majFlag(String stored, boolean allowMAJ) {
        if (!allowMAJ) {
            return "0";
        }
        return stored == null || stored.isBlank() ? "1" : stored;
    }

    static int[] parseOrder(String order, int totalRounds) {
        if (order == null || order.isBlank()) {
            int[] r = new int[totalRounds];
            for (int i = 0; i < totalRounds; i++) {
                r[i] = i + 1;
            }
            return r;
        }
        String[] p = order.split(",");
        int[] r = new int[p.length];
        for (int i = 0; i < p.length; i++) {
            r[i] = Integer.parseInt(p[i].trim());
        }
        return r;
    }

    public SeasonPlanningInput toInput(SeasonSetup s, List<SetupDtos.GeneratedMatch> dependencyMatches, String dependencyCode) {
        List<SeasonPlanningInput.TeamRef> teams = s.teams().stream().map(t -> new SeasonPlanningInput.TeamRef(t.code(), t.name())).toList();
        List<SeasonPlanningInput.RoundDates> rounds = new ArrayList<>();
        for (SetupDtos.Round r : s.rounds()) {
            if (r.dates() == null || r.dates().isEmpty()) {
                continue;
            }
            String label = r.n() != null ? String.valueOf(r.n()) : r.label();
            rounds.add(new SeasonPlanningInput.RoundDates(label, r.dates()));
        }
        List<SeasonPlanningInput.FixtureInput> fixtures = s.fixtures().stream()
                .map(f -> new SeasonPlanningInput.FixtureInput(f.round(), f.home(), f.away(), f.date(), blankToNull(f.hour()), f.locked()))
                .toList();
        List<SeasonPlanningInput.ExternalInput> external = new ArrayList<>();
        if (s.external() != null) {
            for (SetupDtos.ExternalMatch e : s.external()) {
                external.add(new SeasonPlanningInput.ExternalInput(e.competition(), e.round(), e.date(), e.hour(), e.home(), e.away()));
            }
        }
        if (s.includeDependency() && dependencyMatches != null && dependencyCode != null) {
            for (SetupDtos.GeneratedMatch m : dependencyMatches) {
                if (dependencyCode.equals(m.competition()) && m.home() != null) {
                    external.add(new SeasonPlanningInput.ExternalInput(m.competition(), m.round(), m.date(), m.hour(), m.home(), m.away()));
                }
            }
        }
        LocalDate start = s.general().seasonStart();
        LocalDate end = s.general().seasonEnd();
        return new SeasonPlanningInput(s.code(), s.competitionId(), start, end, teams, rounds, fixtures, external);
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
