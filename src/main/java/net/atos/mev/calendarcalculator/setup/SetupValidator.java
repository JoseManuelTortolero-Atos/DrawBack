package net.atos.mev.calendarcalculator.setup;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import net.atos.mev.calendarcalculator.setup.SetupDtos.SeasonSetup;
import net.atos.mev.calendarcalculator.setup.SetupDtos.ValidationIssue;

/**
 * Validaciones de SPECS §9 + CONTRACT §4 (espejo y lastRoundToAssign). Solo los "error"
 * bloquean la generación. Java puro; se registra como bean en {@code SetupConfig}.
 */
public class SetupValidator {

    private static final Pattern TEAM_CODE = Pattern.compile("[A-Z0-9]{2,8}");
    private static final Set<String> SUPPORTED_P2_RULES = Set.of("VenueUnavailable", "FixedHomeDate");
    private final Function<SeasonSetup, List<SetupDtos.RuleTypeDef>> ruleCatalog;

    public SetupValidator() {
        this(s -> List.of());
    }

    public SetupValidator(Function<SeasonSetup, List<SetupDtos.RuleTypeDef>> ruleCatalog) {
        this.ruleCatalog = ruleCatalog;
    }

    public List<ValidationIssue> validate(SeasonSetup s, Integer lastRoundToAssign, boolean dependencyApproved) {
        return validate(s, null, lastRoundToAssign, dependencyApproved);
    }

    public List<ValidationIssue> validate(SeasonSetup s, Integer firstRoundToAssign, Integer lastRoundToAssign,
            boolean dependencyApproved) {
        List<ValidationIssue> out = new ArrayList<>();
        out.addAll(P2Settings.validate(s));
        Map<String, SetupDtos.RuleTypeDef> catalog = new HashMap<>();
        for (SetupDtos.RuleTypeDef def : ruleCatalog.apply(s)) {
            catalog.put(def.type(), def);
        }
        SetupDtos.General g = s.general();
        if (g == null || g.seasonStart() == null || g.seasonEnd() == null || !g.seasonStart().isBefore(g.seasonEnd())) {
            out.add(ValidationIssue.error("season", "Season start must be before season end"));
        }
        if (s.timeslots() == null || s.timeslots().isEmpty()) {
            out.add(ValidationIssue.error("season", "At least one timeslot is required"));
        }
        if (s.ramadan() != null && s.ramadan().start() != null && s.ramadan().end() != null && s.ramadan().start().isAfter(s.ramadan().end())) {
            out.add(ValidationIssue.error("season", "Ramadan start is after Ramadan end"));
        }
        if (g != null && g.maxMatchesPerDay() <= 0) {
            out.add(ValidationIssue.error("season", "Max matches per day must be > 0"));
        }
        if (g != null && g.startSecondLeg() == null) {
            out.add(ValidationIssue.error("season", "Second leg start date is required"));
        }

        List<SetupDtos.Team> teams = s.teams() == null ? List.of() : s.teams();
        int n = teams.size();
        Set<String> codes = new HashSet<>();
        boolean dup = false;
        for (SetupDtos.Team t : teams) {
            dup |= !codes.add(t.code());
            if (t.code() == null || !TEAM_CODE.matcher(t.code()).matches()) {
                out.add(ValidationIssue.warn("teams", "Team codes should be 2-8 uppercase letters/digits: " + t.code()));
            }
        }
        if (n < 2) {
            out.add(ValidationIssue.error("teams", "At least 2 teams are required"));
        }
        if (n % 2 != 0) {
            out.add(ValidationIssue.error("teams", "Odd number of teams (" + n + "). Add a team or a BYE"));
        }
        if (dup) {
            out.add(ValidationIssue.error("teams", "Duplicated team codes"));
        }

        int total = Math.max(0, 2 * (n - 1));
        int last = lastRoundToAssign != null ? lastRoundToAssign : (g == null ? 0 : g.lastRoundToAssign());
        int requestedFirst = firstRoundToAssign == null ? 1 : firstRoundToAssign;
        if (last < 1 || last > total) {
            out.add(ValidationIssue.error("generate", "Last round to assign must be between 1 and " + total));
        }
        if (requestedFirst < 1 || requestedFirst > total || requestedFirst > last) {
            out.add(ValidationIssue.error("generate", "First round to assign must be between 1 and the last round to assign"));
        }
        Set<Integer> plannedRounds = new HashSet<>();
        if (!hasErrors(out)) {
            String order = s.engine() == null ? null : s.engine().roundCalculationOrder();
            int[] calculationOrder = SetupToEngineMapper.parseOrder(order, total);
            if (firstRoundToAssign != null) {
                for (int i = 0; i < Math.min(last, calculationOrder.length); i++) {
                    if (calculationOrder[i] != i + 1) {
                        out.add(ValidationIssue.error("generate",
                                "Incremental generation requires the configured round order to be sequential"));
                        break;
                    }
                }
            }
            for (int i = 0; i < Math.min(last, calculationOrder.length); i++) {
                int round = calculationOrder[i];
                if (firstRoundToAssign == null || round >= requestedFirst && round <= last) {
                    plannedRounds.add(round);
                }
            }
        } else {
            for (int r = 1; r <= Math.min(last, total); r++) plannedRounds.add(r);
        }

        // fixtures
        Map<Integer, List<SetupDtos.Fixture>> byRound = new HashMap<>();
        for (SetupDtos.Fixture f : s.fixtures() == null ? List.<SetupDtos.Fixture>of() : s.fixtures()) {
            byRound.computeIfAbsent(f.round(), k -> new ArrayList<>()).add(f);
        }
        for (int r = 1; r <= total; r++) {
            List<SetupDtos.Fixture> fx = byRound.getOrDefault(r, List.of());
            if (fx.size() != n / 2) {
                out.add(ValidationIssue.error("fixtures", "Round " + r + ": " + fx.size() + " matches (expected " + n / 2 + ")"));
            }
            Map<String, Integer> plays = new HashMap<>();
            for (SetupDtos.Fixture f : fx) {
                if (f.home() == null || f.away() == null || !codes.contains(f.home()) || !codes.contains(f.away())) {
                    out.add(ValidationIssue.error("fixtures", "Round " + r + ": unknown or empty team"));
                    continue;
                }
                if (f.home().equals(f.away())) {
                    out.add(ValidationIssue.error("fixtures", "Round " + r + ": team plays against itself"));
                }
                plays.merge(f.home(), 1, (left, right) -> Integer.valueOf(left.intValue() + right.intValue()));
                plays.merge(f.away(), 1, (left, right) -> Integer.valueOf(left.intValue() + right.intValue()));
            }
            for (Map.Entry<String, Integer> e : plays.entrySet()) {
                if (e.getValue() > 1) {
                    out.add(ValidationIssue.error("fixtures", "Round " + r + ": team " + e.getKey() + " plays " + e.getValue() + " times"));
                }
            }
        }
        // 2ª vuelta espejo de la 1ª (el motor lo exige)
        int firstLeg = n - 1;
        for (int r = 1; r <= firstLeg && n >= 2; r++) {
            Set<String> mirror = new HashSet<>();
            byRound.getOrDefault(r, List.of()).forEach(f -> mirror.add(f.away() + "," + f.home()));
            Set<String> second = new HashSet<>();
            byRound.getOrDefault(r + firstLeg, List.of()).forEach(f -> second.add(f.home() + "," + f.away()));
            if (!mirror.equals(second)) {
                out.add(ValidationIssue.error("fixtures", "Round " + (r + firstLeg) + " is not the mirror of round " + r + " (required by the engine)"));
            }
        }

        // matchdays
        Map<Integer, SetupDtos.Round> rounds = new HashMap<>();
        for (SetupDtos.Round r : s.rounds() == null ? List.<SetupDtos.Round>of() : s.rounds()) {
            if (r.n() != null) {
                rounds.put(r.n(), r);
            }
        }
        LocalDate prevLast = null;
        for (int r = 1; r <= total; r++) {
            SetupDtos.Round rd = rounds.get(r);
            List<SetupDtos.Fixture> fx = byRound.getOrDefault(r, List.of());
            boolean allPlaced = !fx.isEmpty() && fx.stream().allMatch(f -> f.date() != null);
            boolean hasDates = rd != null && rd.dates() != null && !rd.dates().isEmpty();
            if (!hasDates && !allPlaced) {
                if (plannedRounds.contains(r)) {
                    out.add(ValidationIssue.error("matchdays", "Round " + r + " has no date"));
                }
                continue;
            }
                List<LocalDate> dates = hasDates
                    ? (rd == null ? List.of() : rd.dates())
                        : fx.stream().map(fixture -> fixture.date()).filter(date -> date != null)
                            .sorted((left, right) -> left.compareTo(right)).toList();
            for (LocalDate d : dates) {
                if (g != null && g.seasonStart() != null && g.seasonEnd() != null && (d.isBefore(g.seasonStart()) || d.isAfter(g.seasonEnd()))) {
                    out.add(ValidationIssue.warn("matchdays", "Round " + r + ": date " + d + " outside the season"));
                    if (plannedRounds.contains(r)) {
                        out.add(ValidationIssue.error("matchdays", "Round " + r + ": date " + d + " outside the season (engine cannot place it)"));
                    }
                }
                for (SetupDtos.Blackout b : s.blackouts() == null ? List.<SetupDtos.Blackout>of() : s.blackouts()) {
                    if (!d.isBefore(b.from()) && !d.isAfter(b.to())) {
                        out.add(ValidationIssue.warn("matchdays", "Round " + r + ": date " + d + " inside blackout " + b.label()));
                    }
                }
            }
            LocalDate first = dates.stream().min((left, right) -> left.compareTo(right)).orElseThrow();
            if (r > firstLeg && g != null && g.startSecondLeg() != null && first.isBefore(g.startSecondLeg())) {
                out.add(ValidationIssue.warn("matchdays", "Round " + r + " (2nd leg) is before the second leg start"));
            }
            if (prevLast != null && !first.isAfter(prevLast)) {
                out.add(ValidationIssue.warn("matchdays", "Round " + r + " is not after round " + (r - 1)));
            }
            prevLast = dates.stream().max((left, right) -> left.compareTo(right)).orElseThrow();
            for (SetupDtos.Fixture f : fx) {
                if (f.locked() && f.date() != null && hasDates && rd != null && !rd.dates().contains(f.date())) {
                    out.add(ValidationIssue.warn("fixtures", "Locked " + f.home() + "-" + f.away() + " outside the dates of round " + r));
                }
            }
        }

        // constraints
        for (SetupDtos.Rule rule : s.rules() == null ? List.<SetupDtos.Rule>of() : s.rules()) {
            if (!catalog.isEmpty()) {
                SetupDtos.RuleTypeDef def = catalog.get(rule.type());
                if (def == null) {
                    out.add(ValidationIssue.error("constraints", "Rule type " + rule.type() + " does not exist in the federation catalog"));
                    continue;
                }
                if (!def.enabled()) {
                    out.add(ValidationIssue.error("constraints", "Rule type " + rule.type() + " is disabled in the federation catalog"));
                }
                if ("PLANNING".equals(def.scope()) && !SUPPORTED_P2_RULES.contains(rule.type())) {
                    out.add(ValidationIssue.error("constraints", "Rule type " + rule.type() + " has no P2 planning handler"));
                }
                for (SetupDtos.RuleParam param : def.params()) {
                    if (!hasValue(rule, param.key())) {
                        out.add(ValidationIssue.warn("constraints", rule.type() + ": missing value for " + param.label()));
                    }
                }
            }
            if ("MatchNotInRound".equals(rule.type()) && rule.a() != null && rule.b() != null && rule.rounds() != null) {
                for (String rr : rule.rounds().split(",")) {
                    int r = Integer.parseInt(rr.trim());
                    boolean drawn = byRound.getOrDefault(r, List.of()).stream().anyMatch(f -> (f.home().equals(rule.a()) && f.away().equals(rule.b()))
                            || (f.home().equals(rule.b()) && f.away().equals(rule.a())));
                    if (drawn) {
                        out.add(ValidationIssue.error("constraints", "MatchNotInRound: " + rule.a() + "-" + rule.b() + " is drawn in forbidden round " + r));
                    }
                }
            }
            if ("FixedHomeDate".equals(rule.type())) {
                if (rule.team() == null || rule.date() == null) {
                    out.add(ValidationIssue.error("constraints", "FixedHomeDate incomplete"));
                    continue;
                }
                Integer roundOfDate = null;
                for (Map.Entry<Integer, SetupDtos.Round> e : rounds.entrySet()) {
                    if (e.getValue().dates() != null && e.getValue().dates().contains(rule.date())) {
                        roundOfDate = e.getKey();
                    }
                }
                if (roundOfDate == null) {
                    out.add(ValidationIssue.warn("constraints", "FixedHomeDate " + rule.team() + ": no round on " + rule.date()));
                } else {
                    Integer rr = roundOfDate;
                    boolean home = byRound.getOrDefault(rr, List.of()).stream().anyMatch(f -> f.home().equals(rule.team()));
                    if (!home) {
                        out.add(ValidationIssue.error("constraints", "FixedHomeDate: " + rule.team() + " is not at home in round " + rr));
                    }
                }
            }
            if ("VenueUnavailable".equals(rule.type())) {
                if (rule.team() == null || rule.from() == null) {
                    out.add(ValidationIssue.error("constraints", "VenueUnavailable incomplete"));
                } else {
                    if (!codes.contains(rule.team())) {
                        out.add(ValidationIssue.error("constraints", "VenueUnavailable references unknown team " + rule.team()));
                    }
                    if (rule.to() != null && rule.from().isAfter(rule.to())) {
                        out.add(ValidationIssue.error("constraints", "VenueUnavailable starts after it ends"));
                    }
                }
            }
        }
        Map<String, Integer> rest = s.restDays() == null ? Map.of() : s.restDays();
        if (!rest.containsKey(s.code())) {
            out.add(ValidationIssue.warn("constraints", "No rest days configured for " + s.code() + " (default 2)"));
        }
        for (SetupDtos.ExternalMatch e : s.external() == null ? List.<SetupDtos.ExternalMatch>of() : s.external()) {
            if (e.date() == null || e.competition() == null || e.competition().isBlank()) {
                out.add(ValidationIssue.warn("external", "External match without date or competition"));
            } else if (!rest.containsKey(e.competition())) {
                out.add(ValidationIssue.error("external", "No rest days configured for external competition " + e.competition()));
            }
        }
        if (s.includeDependency() && s.dependsOn() != null && !dependencyApproved) {
            out.add(ValidationIssue.warn("external", "No generated calendar yet for " + s.dependsOn()));
        }
        return out;
    }

    public static boolean hasErrors(List<ValidationIssue> issues) {
        return issues.stream().anyMatch(i -> "error".equals(i.level()));
    }

    private static boolean hasValue(SetupDtos.Rule rule, String key) {
        return switch (key) {
        case "a" -> rule.a() != null && !rule.a().isBlank();
        case "b" -> rule.b() != null && !rule.b().isBlank();
        case "team" -> rule.team() != null && !rule.team().isBlank();
        case "teams" -> rule.teams() != null && !rule.teams().isBlank();
        case "rounds" -> rule.rounds() != null && !rule.rounds().isBlank();
        case "minRounds" -> rule.minRounds() != null;
        case "intParam" -> rule.intParam() != null;
        case "from" -> rule.from() != null;
        case "to" -> rule.to() != null;
        case "date" -> rule.date() != null;
        case "definition" -> rule.definition() != null && !rule.definition().isBlank();
        default -> true;
        };
    }
}
