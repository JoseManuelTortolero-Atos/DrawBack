package net.atos.mev.calendarcalculator.schedules;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.Match;
import net.atos.mev.calendarcalculator.Team;
import net.atos.mev.calendarcalculator.Tournament;

/**
 * Construye {@link SchEnvironment} y {@link SchCalendar} desde un {@link SeasonPlanningInput}
 * sin leer ficheros. Reproduce la semántica de {@code SchCalendarExcelIO.loadFromExcel}:
 * partidos colocados (date), horas confirmadas (hour), fechas disponibles por jornada y
 * partidos de otras competiciones, en el mismo orden.
 */
public final class SetupCalendarBuilder {

    private SetupCalendarBuilder() {
    }

    public record Built(SchEnvironment environment, SchCalendar calendar) {
    }

    public static Built build(SeasonPlanningInput in) {
        return build(in, null);
    }

    public static Built build(SeasonPlanningInput in, Properties colours) {
        int n = in.teams().size();
        if (n < 2 || n % 2 != 0) {
            throw new IllegalArgumentException("The league needs an even number of teams (>= 2), found " + n);
        }
        if (in.seasonEnd().isBefore(in.seasonStart())) {
            throw new IllegalArgumentException("seasonEnd is before seasonStart");
        }
        List<String[]> teams = new ArrayList<>();
        Map<String, Integer> pos = new HashMap<>();
        for (SeasonPlanningInput.TeamRef t : in.teams()) {
            if (pos.put(t.code(), teams.size() + 1) != null) {
                throw new IllegalArgumentException("Duplicated team code " + t.code());
            }
            teams.add(new String[] { t.code(), t.name() == null ? t.code() : t.name() });
        }
        Environment env = Environment.inMemory(in.competitionName() == null ? in.competitionCode() : in.competitionName(),
                in.competitionCode(), teams);
        Tournament tour = buildTournament(env, pos, in.fixtures());
        SchEnvironment schEnv = SchEnvironment.inMemory(in.seasonStart(), in.seasonEnd(), env, tour, colours);
        SchCalendar calendar = new SchCalendar(schEnv);

        for (SeasonPlanningInput.FixtureInput f : in.fixtures()) {
            if (f.date() == null) {
                continue; // la fecha la decide el motor
            }
            Match m = tour.findMatch(f.round() - 1, f.home(), f.away());
            if (m == null) {
                throw new IllegalArgumentException("Fixture not in draw: round=" + f.round() + " " + f.home() + "-" + f.away());
            }
            SchMatch sm = new SchMatch(m);
            boolean confirmed = f.hour() != null && !f.hour().isBlank();
            sm.setCompetitionRound(String.valueOf(f.round()));
            sm.setConfirmed(confirmed);
            sm.setDate(f.date());
            sm.setTime(confirmed ? f.hour().trim() : ScheduleMoroccoAlg.EMPTYTIMESLOT);
            sm.setCompetition(in.competitionCode());
            calendar.getMatchdays()[day(calendar, f.date(), "fixture " + f.home() + "-" + f.away())].getMatches().add(sm);
        }
        for (SeasonPlanningInput.RoundDates r : in.rounds()) {
            for (LocalDate d : r.dates()) {
                Matchday md = calendar.getMatchdays()[day(calendar, d, "round " + r.label())];
                md.setCalendarRound(r.label());
                md.setAvailableForNewMatches(true);
            }
        }
        for (SeasonPlanningInput.ExternalInput e : in.external()) {
            OtherCompetitionDay ocd = new OtherCompetitionDay();
            ocd.setCompetition(e.competition());
            ocd.setCompetitionRound(e.round() == null ? "" : e.round());
            ocd.setDate(e.date());
            ocd.setHour(blankToNull(e.hour()));
            ocd.setTeamHome(blankToNull(e.home()));
            ocd.setTeamAway(blankToNull(e.away()));
            if (ocd.hasMatchDetails()) {
                ocd.getTeams().add(ocd.getTeamHome());
                ocd.getTeams().add(ocd.getTeamAway());
            } else if (ocd.getTeamHome() != null) {
                for (String t : ocd.getTeamHome().split(",")) {
                    String c = blankToNull(t);
                    if (c != null) {
                        ocd.getTeams().add(c);
                    }
                }
                if (ocd.getTeamAway() != null) {
                    ocd.getTeams().add(ocd.getTeamAway());
                }
            }
            calendar.getMatchdays()[day(calendar, e.date(), "external " + e.competition())].getOtherMatches().add(ocd);
        }
        schEnv.setDatabaseCalendar(calendar);
        return new Built(schEnv, calendar);
    }

    /**
     * 1ª vuelta = jornadas 1..n-1 en el orden recibido. La 2ª vuelta (n..2(n-1)) debe ser el
     * espejo exacto: el motor genera la vuelta invirtiendo local y visitante.
     */
    static Tournament buildTournament(Environment env, Map<String, Integer> pos, List<SeasonPlanningInput.FixtureInput> fixtures) {
        int firstLeg = env.nRounds;
        List<List<SeasonPlanningInput.FixtureInput>> byRound = new ArrayList<>();
        for (int r = 0; r < 2 * firstLeg; r++) {
            byRound.add(new ArrayList<>());
        }
        for (SeasonPlanningInput.FixtureInput f : fixtures) {
            if (f.round() < 1 || f.round() > 2 * firstLeg) {
                throw new IllegalArgumentException("Fixture round out of range 1.." + (2 * firstLeg) + ": " + f.round());
            }
            if (!pos.containsKey(f.home()) || !pos.containsKey(f.away())) {
                throw new IllegalArgumentException("Fixture with unknown team: " + f.home() + "-" + f.away());
            }
            byRound.get(f.round() - 1).add(f);
        }
        Tournament tour = new Tournament(env);
        for (int r = 0; r < firstLeg; r++) {
            List<SeasonPlanningInput.FixtureInput> round = byRound.get(r);
            if (round.size() != env.matchesPerRound) {
                throw new IllegalArgumentException("Round " + (r + 1) + " must have " + env.matchesPerRound + " fixtures, found " + round.size());
            }
            Set<String> seen = new HashSet<>();
            for (int m = 0; m < round.size(); m++) {
                SeasonPlanningInput.FixtureInput f = round.get(m);
                if (!seen.add(f.home()) || !seen.add(f.away())) {
                    throw new IllegalArgumentException("Team plays twice in round " + (r + 1));
                }
                Match match = new Match(pos.get(f.home()), pos.get(f.away()), env);
                match.setTeam1(team(env, f.home()));
                match.setTeam2(team(env, f.away()));
                tour.rounds[r].matches[m] = match;
            }
            Set<String> expectedMirror = new HashSet<>();
            round.forEach(f -> expectedMirror.add(f.away() + "," + f.home()));
            Set<String> secondLeg = new HashSet<>();
            byRound.get(r + firstLeg).forEach(f -> secondLeg.add(f.home() + "," + f.away()));
            if (!expectedMirror.equals(secondLeg)) {
                throw new IllegalArgumentException("Round " + (r + 1 + firstLeg) + " is not the mirror of round " + (r + 1)
                        + " (the engine requires the second leg to mirror the first)");
            }
        }
        for (int i = 0; i < env.nTeams; i++) {
            tour.mapPosToTeams.put(i + 1, env.teams[i]);
        }
        return tour;
    }

    private static Team team(Environment env, String code) {
        return env.searchTeamByCode(code);
    }

    private static int day(SchCalendar calendar, LocalDate date, String what) {
        if (date == null) {
            throw new IllegalArgumentException("Missing date in " + what);
        }
        int idx = (int) java.time.temporal.ChronoUnit.DAYS.between(calendar.getSchEnv().start, date);
        if (idx < 0 || idx >= calendar.getMatchdays().length) {
            throw new IllegalArgumentException("Date " + date + " of " + what + " is outside the season "
                    + calendar.getSchEnv().start + ".." + calendar.getSchEnv().end);
        }
        return idx;
    }

    private static String blankToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
