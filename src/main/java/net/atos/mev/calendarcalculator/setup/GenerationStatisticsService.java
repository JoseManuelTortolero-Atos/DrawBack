package net.atos.mev.calendarcalculator.setup;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.springframework.stereotype.Service;

import tools.jackson.databind.json.JsonMapper;

@Service
public class GenerationStatisticsService {
    public record Count(int home, int away, int total) {
        Count add(boolean isHome) {
            return new Count(home + (isHome ? 1 : 0), away + (isHome ? 0 : 1), total + 1);
        }
    }

    public record TeamStatistics(String code, String name, Count appearances, Map<String, Count> weekdays,
            Map<String, Count> timeslots, Count midweek, Count weekend) {
    }

    public record Statistics(String competitionId, String season, int versionNo, int matches, List<String> slots,
            List<TeamStatistics> teams) {
    }

    private static final Count ZERO = new Count(0, 0, 0);
    private final SeasonSetupRepository setups;
    private final GenerationRepository generations;
    private final JsonMapper json;

    public GenerationStatisticsService(SeasonSetupRepository setups, GenerationRepository generations, JsonMapper json) {
        this.setups = setups;
        this.generations = generations;
        this.json = json;
    }

    public Statistics statistics(String cid, String season, int versionNo) {
        long id = generations.versionId(setups.competitionSeasonId(cid, season), versionNo);
        String snapshot = generations.configSnapshot(id).orElse("");
        SetupDtos.SeasonSetup setup = snapshot.isBlank() ? null : json.readValue(snapshot, SetupDtos.SeasonSetup.class);
        String code = setup == null ? setups.load(cid, season).code() : setup.code();
        return calculate(cid, season, versionNo, code, setup == null ? List.of() : setup.teams(), generations.matches(id));
    }

    static Statistics calculate(String cid, String season, int versionNo, String code,
            List<SetupDtos.Team> teams, List<SetupDtos.GeneratedMatch> matches) {
        List<SetupDtos.GeneratedMatch> own = matches.stream().filter(m -> code.equals(m.competition())).toList();
        Map<String, String> names = new LinkedHashMap<>();
        teams.forEach(t -> names.put(t.code(), t.name()));
        TreeSet<String> slots = new TreeSet<>();
        for (var match : own) {
            if (match.home() == null || match.home().isBlank() || match.away() == null || match.away().isBlank()
                    || match.home().equals(match.away()) || match.date() == null) {
                throw new IllegalStateException("Invalid stored league match in version " + versionNo);
            }
            names.putIfAbsent(match.home(), match.home());
            names.putIfAbsent(match.away(), match.away());
            slots.add(slot(match));
        }
        List<TeamStatistics> result = new ArrayList<>();
        names.forEach((team, name) -> {
            Map<String, Count> days = new LinkedHashMap<>();
            for (var day : DayOfWeek.values()) days.put(day.name(), ZERO);
            Map<String, Count> hours = new LinkedHashMap<>();
            slots.forEach(hour -> hours.put(hour, ZERO));
            Count total = ZERO, midweek = ZERO, weekend = ZERO;
            for (var match : own) {
                if (!team.equals(match.home()) && !team.equals(match.away())) continue;
                boolean home = team.equals(match.home());
                DayOfWeek day = match.date().getDayOfWeek();
                total = total.add(home);
                days.put(day.name(), days.get(day.name()).add(home));
                hours.put(slot(match), hours.get(slot(match)).add(home));
                if (day.getValue() <= 5) midweek = midweek.add(home);
                else weekend = weekend.add(home);
            }
            result.add(new TeamStatistics(team, name, total, days, hours, midweek, weekend));
        });
        return new Statistics(cid, season, versionNo, own.size(), List.copyOf(slots), result);
    }

    private static String slot(SetupDtos.GeneratedMatch match) {
        return match.hour() == null || match.hour().isBlank() ? "UNASSIGNED" : match.hour();
    }

    public byte[] csv(Statistics statistics) {
        StringBuilder csv = new StringBuilder("\uFEFFsep=,\r\nTeam,Name,Dimension,Value,Home,Away,Total\r\n");
        for (var team : statistics.teams()) {
            row(csv, team, "TOTAL", "", team.appearances());
            row(csv, team, "MIDWEEK", "", team.midweek());
            row(csv, team, "WEEKEND", "", team.weekend());
            team.weekdays().forEach((day, count) -> row(csv, team, "WEEKDAY", day, count));
            team.timeslots().forEach((hour, count) -> row(csv, team, "TIMESLOT", hour, count));
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void row(StringBuilder csv, TeamStatistics team, String dimension, String value, Count count) {
        csv.append(cell(team.code())).append(',').append(cell(team.name())).append(',').append(dimension).append(',')
                .append(cell(value)).append(',').append(count.home()).append(',').append(count.away()).append(',')
                .append(count.total()).append("\r\n");
    }

    private static String cell(String value) {
        String text = value == null ? "" : value;
        if (text.stripLeading().matches("^[=+@\\-].*")) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
