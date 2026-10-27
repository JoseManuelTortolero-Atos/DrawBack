package net.atos.mev.calendarcalculator.setup;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.checks.Check;
import net.atos.mev.calendarcalculator.checks.CheckProcessor;
import net.atos.mev.calendarcalculator.schedules.PlanningParameters;
import net.atos.mev.calendarcalculator.schedules.SchEnvironment;
import net.atos.mev.calendarcalculator.schedules.SetupCalendarBuilder;
import net.atos.mev.calendarcalculator.service.dto.CompetitionRuntimeOverridesDTO;
import net.atos.mev.calendarcalculator.setup.rules.RuleScopeService;

@Service
public class DatabaseEngineConfiguration {
    public record Planning(SetupDtos.SeasonSetup setup, PlanningParameters parameters, SetupCalendarBuilder.Built built) { }
    private static final String PREFIX = "ScheduleMoroccoAlg.";
    private final JdbcClient jdbc;
    private final SeasonSetupRepository setups;
    private final GenerationRepository generations;
    private final SetupToEngineMapper mapper;
    private final RuleScopeService rules;
    @Value("${calendar.results.base-dir:./data/results}")
    private String resultsBaseDirectory = "./data/results";

    public DatabaseEngineConfiguration(JdbcClient jdbc, SeasonSetupRepository setups, GenerationRepository generations,
            SetupToEngineMapper mapper, RuleScopeService rules) {
        this.jdbc = jdbc;
        this.setups = setups;
        this.generations = generations;
        this.mapper = mapper;
        this.rules = rules;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public SetupDtos.SeasonSetup setup(String selector, String season) {
        if (selector == null || selector.isBlank()) throw new IllegalArgumentException("Competition selector is required");
        String key = selector.startsWith("DB:") ? selector.substring(3) : selector;
        int separator = key.lastIndexOf('@');
        if (separator >= 0) {
            String embeddedSeason = key.substring(separator + 1);
            if (season != null && !season.equals(embeddedSeason)) throw new IllegalArgumentException("Conflicting season selectors");
            season = embeddedSeason;
            key = key.substring(0, separator);
        }
        var ids = jdbc.sql("""
                SELECT DISTINCT c.id FROM competition c LEFT JOIN competition_alias a ON a.competition_id = c.id
                WHERE UPPER(c.id) = UPPER(:selector) OR UPPER(c.code) = UPPER(:selector)
                   OR UPPER(a.alias_value) = UPPER(:selector)""").param("selector", key).query(String.class).list();
        if (ids.isEmpty()) throw new SetupExceptions.NotFound("Competition or database alias not found: " + key);
        if (ids.size() != 1) throw new IllegalArgumentException("Ambiguous competition alias; use an explicit competition ID");
        String id = ids.getFirst();
        if (season == null || season.isBlank()) {
            var seasons = jdbc.sql("SELECT season_id FROM competition_season WHERE competition_id = :id ORDER BY season_id")
                    .param("id", id).query(String.class).list();
            if (seasons.size() != 1) {
                throw new IllegalArgumentException("An explicit season is required for " + id + "; use DB:" + id + "@YYYY-YY");
            }
            season = seasons.getFirst();
        }
        return rules.withScopedRules(setups.load(id, season));
    }

    /** Uses the effective snapshot returned by setup, without refreshing rules halfway through a run. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Planning planning(SetupDtos.SeasonSetup effective, Integer lastRound) {
        List<SetupDtos.GeneratedMatch> dependency = List.of();
        String dependencyCode = null;
        if (effective.includeDependency() && effective.dependsOn() != null) {
            dependency = generations.approvedMatches(effective.dependsOn(), effective.season())
                    .orElseThrow(() -> new IllegalArgumentException("The dependency needs an approved P2 version"));
            dependencyCode = setups.competitionCode(effective.dependsOn());
        }
        var input = mapper.map(effective, dependency, dependencyCode, lastRound);
        var built = SetupCalendarBuilder.build(input.input(), P2Settings.colours(effective));
        built.environment().setPlanningParameters(input.parameters());
        return new Planning(effective, input.parameters(), built);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Environment environment(String selector, String season) {
        return environment(setup(selector, season));
    }

    public Environment environment(SetupDtos.SeasonSetup setup) {
        var env = Environment.inMemory(setup.competitionId(), setup.code(),
                setup.teams().stream().map(t -> new String[] {t.code(), t.name() == null ? t.code() : t.name()}).toList());
        var definitions = drawDefinitions(setup);
        var checks = new ArrayList<Check>();
        for (String definition : definitions) {
            var check = CheckProcessor.createCheck(env, definition);
            if (check == null) throw new IllegalArgumentException("Invalid persisted draw check: " + definition);
            checks.add(check);
        }
        env.checks = checks.toArray(Check[]::new);
        env.nChecks = checks.size();
        env.setOriginalProp(drawProperties(setup, definitions));
        return env;
    }

    private static LinkedHashSet<String> drawDefinitions(SetupDtos.SeasonSetup setup) {
        var definitions = new LinkedHashSet<String>();
        for (var rule : setup.rules()) {
            if (net.atos.mev.calendarcalculator.setup.draw.DrawEngine.isCheckDefinition(rule.definition())) {
                definitions.add(rule.definition());
            }
        }
        for (var pair : setup.derbies()) definitions.add("DerbyTeamsCheck," + pair.get(0) + "," + pair.get(1));
        return definitions;
    }

    private static Properties drawProperties(SetupDtos.SeasonSetup setup, LinkedHashSet<String> definitions) {
        var result = new Properties();
        put(result, "name", setup.competitionId());
        put(result, "code", setup.code());
        put(result, "env", "DB:" + setup.competitionId() + "@" + setup.season());
        put(result, "environment", result.getProperty("env"));
        put(result, "nTeams", setup.teams().size());
        put(result, "nRounds", setup.teams().size() - 1);
        for (int i = 0; i < setup.teams().size(); i++) {
            var team = setup.teams().get(i);
            put(result, "team." + i, team.code());
            put(result, "team." + team.code(), team.name() == null ? team.code() : team.name());
        }
        int check = 1;
        for (String definition : definitions) put(result, "check." + check++, definition);
        put(result, "nChecks", definitions.size());
        return result;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Properties properties(String selector, String season, CompetitionRuntimeOverridesDTO overrides) {
        return properties(setup(selector, season), overrides);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Properties properties(SetupDtos.SeasonSetup setup, CompetitionRuntimeOverridesDTO overrides) {
        var result = environment(setup).getOriginalProp();
        var general = setup.general();
        put(result, "start", SchEnvironment.writeDate(general.seasonStart()));
        put(result, "end", SchEnvironment.writeDate(general.seasonEnd()));
        int last = overrides != null && overrides.lastRoundToAssign() != null
                ? overrides.lastRoundToAssign() : general.lastRoundToAssign();
        var params = mapper.toParameters(setup, last);
        put(result, PREFIX + "lastRoundToAssign", last);
        put(result, PREFIX + "failOnNoFeasibleAssignment", params.failOnNoFeasibleAssignment());
        put(result, PREFIX + "maxDaysAdvanceMatch", general.maxDaysAdvanceMatch());
        put(result, PREFIX + "maxDaysPostponeMatch", general.maxDaysPostponeMatch());
        put(result, PREFIX + "maxMatchesPerDay", general.maxMatchesPerDay());
        put(result, PREFIX + "timeAssignsForwardLook", general.timeAssignsForwardLook());
        put(result, PREFIX + "timeslots", String.join(",", setup.timeslots()));
        put(result, PREFIX + "teamsWithoutLight", String.join(",", setup.teams().stream().filter(SetupDtos.Team::noLight).map(SetupDtos.Team::code).toList()));
        put(result, PREFIX + "teamsToOptimizeTimes", String.join(",", setup.teams().stream().filter(SetupDtos.Team::optimizeTimes).map(SetupDtos.Team::code).toList()));
        put(result, PREFIX + "timeslotWithoutLight", setup.noLightSlot());
        put(result, PREFIX + "startSecondLeg", SchEnvironment.writeDate(general.startSecondLeg()));
        if (setup.ramadan() != null) {
            if (setup.ramadan().start() != null) put(result, PREFIX + "ramadan.start", SchEnvironment.writeDate(setup.ramadan().start()));
            if (setup.ramadan().end() != null) put(result, PREFIX + "ramadan.end", SchEnvironment.writeDate(setup.ramadan().end()));
            put(result, PREFIX + "ramadan.timeslots", setup.ramadan().slot());
        }
        put(result, PREFIX + "groupsToRun", params.groupsToRun());
        put(result, PREFIX + "groupToOptimizeTimes", params.groupToOptimizeTimes());
        put(result, PREFIX + "groupsCanGoToMAJ", String.join(",", params.groupsCanGoToMAJ()));
        put(result, PREFIX + "roundCalculationOrder", java.util.Arrays.stream(params.roundCalculationOrder()).mapToObj(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
        for (int i = 0; i < params.assignGroups().size(); i++) {
            put(result, PREFIX + "assignGroups." + (i + 1), String.join(",", params.assignGroups().get(i)));
            put(result, PREFIX + "roundsForwardLook." + (i + 1), params.roundsForwardLookPerGroup().get(i));
        }
        put(result, PREFIX + "competitions", String.join(",", params.restDays().keySet()));
        setup.restDays().forEach((code, days) -> put(result, PREFIX + "restDays." + code, days));
        for (int i = 0; i < setup.notHomeSameDay().size(); i++) {
            put(result, PREFIX + "teamsNotHomeSameDay." + (i + 1), String.join(",", setup.notHomeSameDay().get(i)));
        }
        result.putAll(P2Settings.colours(setup));
        put(result, PREFIX + "outputDatesCSV", params.outputDatesCSV());
        put(result, PREFIX + "outputDatesExcel", params.outputDatesExcel());
        put(result, PREFIX + "outputTeamCSV", params.outputTeamCSV());
        put(result, PREFIX + "outputTeamExcel", params.outputTeamExcel());
        put(result, PREFIX + "outputStatisticsCSV", params.outputStatisticsCSV());
        put(result, PREFIX + "outputTimeStatisticsCSV", params.outputTimeStatisticsCSV());
        String storage = jdbc.sql("SELECT storage_dir FROM competition WHERE id = :id")
                .param("id", setup.competitionId()).query(String.class).single();
        var base = java.nio.file.Path.of(resultsBaseDirectory).toAbsolutePath().normalize();
        var folder = base.resolve(storage).resolve(setup.season()).resolve("legacy").normalize();
        if (!folder.startsWith(base)) throw new IllegalArgumentException("Invalid competition storage directory");
        put(result, "resultsFolder", folder);
        if (!setup.fixtures().isEmpty()) {
            put(result, "tournament", SetupCalendarBuilder.build(mapper.toInput(setup, List.of(), null)).environment().getTour().toStringOneLineWithAssignments());
        }
        if (overrides != null) {
            put(result, "datesFile", overrides.datesFile());
            put(result, "resultsFolder", overrides.resultsFolder());
        }
        return result;
    }

    private static void put(Properties target, String name, Object value) {
        if (value != null) target.setProperty(name, value.toString());
    }
}
