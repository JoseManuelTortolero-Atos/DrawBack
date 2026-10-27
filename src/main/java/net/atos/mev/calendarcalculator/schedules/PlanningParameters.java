package net.atos.mev.calendarcalculator.schedules;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Parámetros del motor de planificación (P2) en memoria. Sustituye a las claves
 * {@code ScheduleMoroccoAlg.*} de los ficheros sch*.properties (v0.4).
 *
 * <p>Java puro: sin Spring ni JPA. Se construye desde la BD ({@code SetupToEngineMapper})
 * o, solo como puente legacy, desde un {@link Properties} con {@link #fromProperties}.
 */
public final class PlanningParameters {

    /** Periodo sin partidos de liga [from, to] (regla nueva). */
    public record Blackout(String label, LocalDate from, LocalDate to) {
        public boolean contains(LocalDate d) {
            return !d.isBefore(from) && !d.isAfter(to);
        }
    }

    /** El equipo no puede jugar en casa en [from, to] (regla nueva). */
    public record VenueUnavailable(String team, LocalDate from, LocalDate to) {
        public boolean contains(LocalDate d) {
            return !d.isBefore(from) && !d.isAfter(to);
        }
    }

    /** El equipo debe jugar en casa en la fecha indicada (regla nueva). */
    public record FixedHomeDate(String team, LocalDate date) {
    }

    int lastRoundToAssign;
    int firstRoundToAssign = 1;
    int maxMatchesPerDay;
    int groupsToRun = 1;
    int maxDaysAdvanceMatch;
    int maxDaysPostponeMatch;
    String[] timeslots = new String[0];
    int groupToOptimizeTimes = 1;
    String[] teamsToOptimizeTimes = new String[0];
    String ramadanTimeslot;
    LocalDate ramadanStart;
    LocalDate ramadanEnd;
    String[] teamsWithoutLight = new String[] { "" };
    String timeslotWithoutLight;
    /** Orden = orden de {@code ScheduleMoroccoAlg.competitions}. */
    Map<String, Integer> restDays = new LinkedHashMap<>();
    List<String[]> teamsNotHomeSameDay = new ArrayList<>();
    List<String[]> assignGroups = new ArrayList<>();
    List<Integer> roundsForwardLookPerGroup = new ArrayList<>();
    int timeAssignsForwardLook;
    String[] groupsCanGoToMAJ = new String[] { "1" };
    int[] roundCalculationOrder = new int[0];
    LocalDate startSecondLeg;
    boolean failOnNoFeasibleAssignment;
    List<Blackout> blackouts = new ArrayList<>();
    List<VenueUnavailable> venueUnavailable = new ArrayList<>();
    List<FixedHomeDate> fixedHomeDates = new ArrayList<>();

    /** Nombres de ficheros de salida legacy (solo los usa {@link LegacyResultFiles}). */
    String outputDatesCSV = "outSample.csv";
    String outputDatesExcel = "fullCalendar.xlsx";
    String outputTeamCSV = "summaryPerTeam.csv";
    String outputTeamExcel = "summaryPerTeam.xlsx";
    String outputStatisticsCSV = "statisticsDaysAtHome.csv";
    String outputTimeStatisticsCSV = "statisticsTime.csv";

    private PlanningParameters() {
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Puente legacy: lee exactamente las mismas claves y con la misma semántica que el
     * constructor original de {@code ScheduleMoroccoAlg}, incluido el comportamiento de
     * {@code teamsNotHomeSameDay.N} (se lee desde .1 y se para en el primer hueco: en
     * SchMoroccoD2.properties empieza en .8 y por eso el legacy ignoraba sus parejas, GAP-04).
     */
    public static PlanningParameters fromProperties(Properties prop, String competitionCode) {
        PlanningParameters p = new PlanningParameters();
        p.outputDatesCSV = prop.getProperty("ScheduleMoroccoAlg.outputDatesCSV", p.outputDatesCSV);
        p.outputDatesExcel = prop.getProperty("ScheduleMoroccoAlg.outputDatesExcel", p.outputDatesExcel);
        p.outputTeamCSV = prop.getProperty("ScheduleMoroccoAlg.outputTeamCSV", p.outputTeamCSV);
        p.outputTeamExcel = prop.getProperty("ScheduleMoroccoAlg.outputTeamExcel", p.outputTeamExcel);
        p.outputStatisticsCSV = prop.getProperty("ScheduleMoroccoAlg.outputStatisticsCSV", p.outputStatisticsCSV);
        p.outputTimeStatisticsCSV = prop.getProperty("ScheduleMoroccoAlg.outputTimeStatisticsCSV", p.outputTimeStatisticsCSV);
        p.failOnNoFeasibleAssignment = Boolean.parseBoolean(prop.getProperty("ScheduleMoroccoAlg.failOnNoFeasibleAssignment", "false"));
        p.lastRoundToAssign = Integer.valueOf(req(prop, "ScheduleMoroccoAlg.lastRoundToAssign").trim());
        p.maxMatchesPerDay = Integer.valueOf(req(prop, "ScheduleMoroccoAlg.maxMatchesPerDay").trim());
        p.groupsToRun = Integer.valueOf(req(prop, "ScheduleMoroccoAlg.groupsToRun").trim());
        p.maxDaysAdvanceMatch = Integer.valueOf(req(prop, "ScheduleMoroccoAlg.maxDaysAdvanceMatch").trim());
        p.maxDaysPostponeMatch = Integer.valueOf(req(prop, "ScheduleMoroccoAlg.maxDaysPostponeMatch").trim());
        p.timeslots = req(prop, "ScheduleMoroccoAlg.timeslots").split(",");
        p.groupToOptimizeTimes = Integer.valueOf(req(prop, "ScheduleMoroccoAlg.groupToOptimizeTimes").trim());
        p.teamsToOptimizeTimes = req(prop, "ScheduleMoroccoAlg.teamsToOptimizeTimes").split(",");
        p.ramadanTimeslot = prop.getProperty("ScheduleMoroccoAlg.ramadan.timeslots");
        String ramadanStart = prop.getProperty("ScheduleMoroccoAlg.ramadan.start");
        String ramadanEnd = prop.getProperty("ScheduleMoroccoAlg.ramadan.end");
        if ((ramadanStart == null) != (ramadanEnd == null)) {
            throw new IllegalArgumentException("Ramadan start and end must both be present or absent");
        }
        p.ramadanStart = ramadanStart == null ? null : SchEnvironment.readDate(ramadanStart);
        p.ramadanEnd = ramadanEnd == null ? null : SchEnvironment.readDate(ramadanEnd);
        p.teamsWithoutLight = req(prop, "ScheduleMoroccoAlg.teamsWithoutLight").split(",");
        p.timeslotWithoutLight = prop.getProperty("ScheduleMoroccoAlg.timeslotWithoutLight");
        for (String comp : req(prop, "ScheduleMoroccoAlg.competitions").split(",")) {
            if (comp.isBlank()) continue;
            p.restDays.put(comp, Integer.valueOf(req(prop, "ScheduleMoroccoAlg.restDays." + comp).trim()));
        }
        String pair = prop.getProperty("ScheduleMoroccoAlg.teamsNotHomeSameDay.1");
        for (int i = 2; pair != null; i++) {
            p.teamsNotHomeSameDay.add(pair.split(","));
            pair = prop.getProperty("ScheduleMoroccoAlg.teamsNotHomeSameDay." + i);
        }
        String group = prop.getProperty("ScheduleMoroccoAlg.assignGroups.1");
        for (int i = 2; group != null; i++) {
            p.assignGroups.add(group.split(","));
            p.roundsForwardLookPerGroup.add(Integer.valueOf(req(prop, "ScheduleMoroccoAlg.roundsForwardLook." + (i - 1)).trim()));
            group = prop.getProperty("ScheduleMoroccoAlg.assignGroups." + i);
        }
        p.timeAssignsForwardLook = Integer.valueOf(req(prop, "ScheduleMoroccoAlg.timeAssignsForwardLook").trim());
        p.groupsCanGoToMAJ = req(prop, "ScheduleMoroccoAlg.groupsCanGoToMAJ").split(",");
        String[] order = req(prop, "ScheduleMoroccoAlg.roundCalculationOrder").split(",");
        p.roundCalculationOrder = new int[order.length];
        for (int i = 0; i < order.length; i++) {
            p.roundCalculationOrder[i] = Integer.valueOf(order[i].trim());
        }
        String secondLeg = prop.getProperty("ScheduleMoroccoAlg.startSecondLeg");
        p.startSecondLeg = secondLeg == null || secondLeg.isBlank() ? null : SchEnvironment.readDate(secondLeg.trim());
        return p;
    }

    private static String req(Properties prop, String key) {
        String v = prop.getProperty(key);
        if (v == null) {
            throw new IllegalArgumentException("Missing planning parameter: " + key);
        }
        return v;
    }

    /** Copia con otra última jornada (el job la recibe por petición). */
    public PlanningParameters withLastRoundToAssign(int lastRound) {
        PlanningParameters c = copy();
        c.lastRoundToAssign = lastRound;
        return c;
    }

    private PlanningParameters copy() {
        PlanningParameters c = new PlanningParameters();
        c.lastRoundToAssign = lastRoundToAssign;
        c.firstRoundToAssign = firstRoundToAssign;
        c.maxMatchesPerDay = maxMatchesPerDay;
        c.groupsToRun = groupsToRun;
        c.maxDaysAdvanceMatch = maxDaysAdvanceMatch;
        c.maxDaysPostponeMatch = maxDaysPostponeMatch;
        c.timeslots = timeslots.clone();
        c.groupToOptimizeTimes = groupToOptimizeTimes;
        c.teamsToOptimizeTimes = teamsToOptimizeTimes.clone();
        c.ramadanTimeslot = ramadanTimeslot;
        c.ramadanStart = ramadanStart;
        c.ramadanEnd = ramadanEnd;
        c.teamsWithoutLight = teamsWithoutLight.clone();
        c.timeslotWithoutLight = timeslotWithoutLight;
        c.restDays = new LinkedHashMap<>(restDays);
        c.teamsNotHomeSameDay = new ArrayList<>(teamsNotHomeSameDay);
        c.assignGroups = new ArrayList<>();
        for (String[] g : assignGroups) {
            c.assignGroups.add(g.clone());
        }
        c.roundsForwardLookPerGroup = new ArrayList<>(roundsForwardLookPerGroup);
        c.timeAssignsForwardLook = timeAssignsForwardLook;
        c.groupsCanGoToMAJ = groupsCanGoToMAJ.clone();
        c.roundCalculationOrder = roundCalculationOrder.clone();
        c.startSecondLeg = startSecondLeg;
        c.failOnNoFeasibleAssignment = failOnNoFeasibleAssignment;
        c.blackouts = new ArrayList<>(blackouts);
        c.venueUnavailable = new ArrayList<>(venueUnavailable);
        c.fixedHomeDates = new ArrayList<>(fixedHomeDates);
        c.outputDatesCSV = outputDatesCSV;
        c.outputDatesExcel = outputDatesExcel;
        c.outputTeamCSV = outputTeamCSV;
        c.outputTeamExcel = outputTeamExcel;
        c.outputStatisticsCSV = outputStatisticsCSV;
        c.outputTimeStatisticsCSV = outputTimeStatisticsCSV;
        return c;
    }

    // ---- getters (lectura para tests, mapper y LegacyResultFiles) ----
    public int lastRoundToAssign() { return lastRoundToAssign; }
    public int firstRoundToAssign() { return firstRoundToAssign; }
    public int maxMatchesPerDay() { return maxMatchesPerDay; }
    public int groupsToRun() { return groupsToRun; }
    public int maxDaysAdvanceMatch() { return maxDaysAdvanceMatch; }
    public int maxDaysPostponeMatch() { return maxDaysPostponeMatch; }
    public List<String> timeslots() { return List.of(timeslots); }
    public int groupToOptimizeTimes() { return groupToOptimizeTimes; }
    public List<String> teamsToOptimizeTimes() { return List.of(teamsToOptimizeTimes); }
    public String ramadanTimeslot() { return ramadanTimeslot; }
    public LocalDate ramadanStart() { return ramadanStart; }
    public LocalDate ramadanEnd() { return ramadanEnd; }
    public List<String> teamsWithoutLight() { return List.of(teamsWithoutLight); }
    public String timeslotWithoutLight() { return timeslotWithoutLight; }
    public Map<String, Integer> restDays() { return Collections.unmodifiableMap(restDays); }
    public List<List<String>> teamsNotHomeSameDay() { return teamsNotHomeSameDay.stream().map(Arrays::asList).toList(); }
    public List<List<String>> assignGroups() { return assignGroups.stream().map(Arrays::asList).toList(); }
    public List<Integer> roundsForwardLookPerGroup() { return List.copyOf(roundsForwardLookPerGroup); }
    public int timeAssignsForwardLook() { return timeAssignsForwardLook; }
    public List<String> groupsCanGoToMAJ() { return List.of(groupsCanGoToMAJ); }
    public int[] roundCalculationOrder() { return roundCalculationOrder.clone(); }
    public LocalDate startSecondLeg() { return startSecondLeg; }
    public boolean failOnNoFeasibleAssignment() { return failOnNoFeasibleAssignment; }
    public List<Blackout> blackouts() { return List.copyOf(blackouts); }
    public List<VenueUnavailable> venueUnavailable() { return List.copyOf(venueUnavailable); }
    public List<FixedHomeDate> fixedHomeDates() { return List.copyOf(fixedHomeDates); }
    public String outputDatesCSV() { return outputDatesCSV; }
    public String outputDatesExcel() { return outputDatesExcel; }
    public String outputTeamCSV() { return outputTeamCSV; }
    public String outputTeamExcel() { return outputTeamExcel; }
    public String outputStatisticsCSV() { return outputStatisticsCSV; }
    public String outputTimeStatisticsCSV() { return outputTimeStatisticsCSV; }

    /** Constructor fluido usado por el mapper BD → motor y por los tests. */
    public static final class Builder {
        private final PlanningParameters p = new PlanningParameters();

        public Builder lastRoundToAssign(int v) { p.lastRoundToAssign = v; return this; }
        public Builder firstRoundToAssign(int v) { p.firstRoundToAssign = v; return this; }
        public Builder maxMatchesPerDay(int v) { p.maxMatchesPerDay = v; return this; }
        public Builder groupsToRun(int v) { p.groupsToRun = v; return this; }
        public Builder maxDaysAdvanceMatch(int v) { p.maxDaysAdvanceMatch = v; return this; }
        public Builder maxDaysPostponeMatch(int v) { p.maxDaysPostponeMatch = v; return this; }
        public Builder timeslots(List<String> v) { p.timeslots = v.toArray(String[]::new); return this; }
        public Builder groupToOptimizeTimes(int v) { p.groupToOptimizeTimes = v; return this; }
        public Builder teamsToOptimizeTimes(List<String> v) { p.teamsToOptimizeTimes = v.toArray(String[]::new); return this; }
        public Builder ramadan(LocalDate start, LocalDate end, String slot) { p.ramadanStart = start; p.ramadanEnd = end; p.ramadanTimeslot = slot; return this; }
        /** Vacío equivale al legacy {@code teamsWithoutLight=} (array con un elemento ""). */
        public Builder teamsWithoutLight(List<String> v) { p.teamsWithoutLight = v.isEmpty() ? new String[] { "" } : v.toArray(String[]::new); return this; }
        public Builder timeslotWithoutLight(String v) { p.timeslotWithoutLight = v; return this; }
        public Builder restDays(Map<String, Integer> v) { p.restDays = new LinkedHashMap<>(v); return this; }
        public Builder teamsNotHomeSameDay(List<List<String>> v) { p.teamsNotHomeSameDay = new ArrayList<>(); v.forEach(x -> p.teamsNotHomeSameDay.add(x.toArray(String[]::new))); return this; }
        public Builder assignGroup(List<String> teams, int roundsForwardLook) { p.assignGroups.add(teams.toArray(String[]::new)); p.roundsForwardLookPerGroup.add(roundsForwardLook); return this; }
        public Builder timeAssignsForwardLook(int v) { p.timeAssignsForwardLook = v; return this; }
        public Builder groupsCanGoToMAJ(List<String> v) { p.groupsCanGoToMAJ = v.toArray(String[]::new); return this; }
        public Builder roundCalculationOrder(int[] v) { p.roundCalculationOrder = v.clone(); return this; }
        public Builder startSecondLeg(LocalDate v) { p.startSecondLeg = v; return this; }
        public Builder failOnNoFeasibleAssignment(boolean v) { p.failOnNoFeasibleAssignment = v; return this; }
        public Builder blackout(String label, LocalDate from, LocalDate to) { p.blackouts.add(new Blackout(label, from, to)); return this; }
        public Builder venueUnavailable(String team, LocalDate from, LocalDate to) { p.venueUnavailable.add(new VenueUnavailable(team, from, to)); return this; }
        public Builder fixedHomeDate(String team, LocalDate date) { p.fixedHomeDates.add(new FixedHomeDate(team, date)); return this; }
        public Builder outputNames(String suffix) {
            p.outputDatesCSV = "outSample" + suffix + ".csv";
            p.outputDatesExcel = "fullCalendar" + suffix + ".xlsx";
            p.outputTeamCSV = "summaryPerTeam" + suffix + ".csv";
            p.outputTeamExcel = "summaryPerTeam" + suffix + ".xlsx";
            p.outputStatisticsCSV = "statisticsDaysAtHome" + suffix + ".csv";
            p.outputTimeStatisticsCSV = "statisticsTime" + suffix + ".csv";
            return this;
        }

        public PlanningParameters build() {
            if (p.assignGroups.isEmpty()) {
                throw new IllegalArgumentException("At least one assign group is required");
            }
            if (p.timeslots.length == 0) {
                throw new IllegalArgumentException("At least one timeslot is required");
            }
            return p.copy();
        }
    }
}
