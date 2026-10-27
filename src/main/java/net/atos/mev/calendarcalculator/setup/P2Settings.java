package net.atos.mev.calendarcalculator.setup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.poi.ss.usermodel.IndexedColors;

/** Structural validation independent of fixture/date completeness. */
public final class P2Settings {
    private P2Settings() {
    }

    public static List<SetupDtos.ValidationIssue> validate(SetupDtos.SeasonSetup s) {
        List<SetupDtos.ValidationIssue> out = new ArrayList<>();
        var g = s.general();
        if (g != null && (g.roundsForwardLook() < 1 || g.timeAssignsForwardLook() < 1
                || g.maxDaysAdvanceMatch() < 0 || g.maxDaysPostponeMatch() < 0)) {
            error(out, "P2 horizons must be positive and advance/postpone limits non-negative.");
        }
        if (s.restDays() != null && s.restDays().entrySet().stream().anyMatch(x ->
                x.getKey() == null || !x.getKey().matches("[A-Z0-9_]{1,32}") || x.getValue() == null || x.getValue() < 0)) {
            error(out, "Rest days need a competition code and a non-negative integer.");
        }
        if (s.notHomeSameDay() != null) {
            Set<String> pairs = new HashSet<>();
            for (var pair : s.notHomeSameDay()) {
                if (pair == null || pair.size() != 2 || pair.stream().anyMatch(x -> x == null || x.isBlank())
                        || pair.get(0).equals(pair.get(1))) {
                    error(out, "P2 home pairs need two different non-empty team codes.");
                    continue;
                }
                String key = String.join(",", pair.stream().sorted().toList());
                if (!pairs.add(key)) error(out, "P2 home pairs must not contain duplicate team pairs.");
            }
        }
        var e = s.engine();
        if (e == null) return out;
        Set<String> teams = new HashSet<>();
        if (s.teams() != null) s.teams().forEach(t -> teams.add(t.code()));
        int total = Math.max(0, 2 * (teams.size() - 1));
        var groups = e.assignGroups();
        if (groups != null && !groups.isEmpty()) {
            if (e.groupsToRun() < 1 || e.groupsToRun() > groups.size()) {
                error(out, "Groups to run must be between 1 and the number of calculation groups.");
            }
            Set<String> assigned = new HashSet<>();
            for (int i = 0; i < groups.size(); i++) {
                var group = groups.get(i);
                if (group == null || group.groupNo() != i + 1 || group.roundsForwardLook() < 1) {
                    error(out, "Calculation groups need consecutive numbers from 1 and positive horizons.");
                    continue;
                }
                Set<String> members = new HashSet<>();
                for (String team : csv(group.teams())) {
                    if (!team.matches("[A-Z0-9]{1,32}") || !members.add(team)) {
                        error(out, "Calculation groups need valid team codes without duplicates within a group.");
                    }
                }
                if (members.isEmpty()) error(out, "Each calculation group needs at least one team.");
                if (i < e.groupsToRun()) assigned.addAll(members);
            }
            if (!assigned.containsAll(teams)) error(out, "The groups to run must cover every participating team.");
        }
        int groupCount = groups == null || groups.isEmpty() ? 1 : groups.size();
        if (e.groupsCanGoToMAJ() != null && !e.groupsCanGoToMAJ().isBlank() && !"0".equals(e.groupsCanGoToMAJ().trim())) {
            Set<String> seen = new HashSet<>();
            for (String flag : csv(e.groupsCanGoToMAJ())) {
                if (!flag.matches("[1-9][0-9]*") || !seen.add(flag)) {
                    error(out, "MAJ groups must be unique existing group numbers, or 0 for none.");
                } else {
                    try {
                        if (Integer.parseInt(flag) > groupCount) error(out, "MAJ groups must reference existing groups.");
                    } catch (NumberFormatException ex) {
                        error(out, "MAJ groups must reference existing groups.");
                    }
                }
            }
        }
        if (e.roundCalculationOrder() != null && !e.roundCalculationOrder().isBlank()) {
            List<String> order = csv(e.roundCalculationOrder());
            Set<String> expected = new HashSet<>();
            for (int r = 1; r <= total; r++) expected.add(String.valueOf(r));
            if (order.size() != total || !new HashSet<>(order).equals(expected)) {
                error(out, "Round calculation order must contain every round exactly once.");
            }
        }
        if (e.exportColours() != null) {
            Set<String> colours = new HashSet<>();
            for (IndexedColors colour : IndexedColors.values()) {
                if (colour != IndexedColors.AUTOMATIC) colours.add(colour.name());
            }
            e.exportColours().forEach((code, colour) -> {
                if (code == null || !code.matches("[A-Z0-9_]{1,32}") || !colours.contains(colour)) {
                    error(out, "Export colours need a competition code and a valid IndexedColors name.");
                }
            });
        }
        return out;
    }

    private static List<String> csv(String value) {
        return value == null || value.isBlank() ? List.of() : Arrays.stream(value.split(",", -1)).map(String::trim).toList();
    }

    private static void error(List<SetupDtos.ValidationIssue> out, String message) {
        out.add(SetupDtos.ValidationIssue.error("season", message));
    }

    public static Properties colours(SetupDtos.SeasonSetup setup) {
        Properties result = new Properties();
        if (setup.engine() != null && setup.engine().exportColours() != null) {
            setup.engine().exportColours().forEach((code, colour) ->
                    result.setProperty("ScheduleMoroccoAlg.colour." + code, colour));
        }
        return result;
    }
}
