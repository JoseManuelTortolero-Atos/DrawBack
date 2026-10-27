package net.atos.mev.calendarcalculator.setup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import net.atos.mev.calendarcalculator.setup.MasterDataController.CreateSeason;
import net.atos.mev.calendarcalculator.setup.MasterDataController.MasterTeam;
import net.atos.mev.calendarcalculator.setup.MasterDataController.TeamRequest;

@Service
public class MasterDataService {
    private static final String TEAM_SELECT = """
            SELECT t.code, t.name, t.city,
                   (SELECT COUNT(*) FROM competition_season_team cst WHERE cst.team_code = t.code) AS registrations
              FROM team t""";
    private final JdbcClient jdbc;
    private final SeasonSetupRepository setups;
    private final SetupCatalogRepository catalog;

    public MasterDataService(JdbcClient jdbc, SeasonSetupRepository setups, SetupCatalogRepository catalog) {
        this.jdbc = jdbc;
        this.setups = setups;
        this.catalog = catalog;
    }

    public List<MasterTeam> teams() {
        return jdbc.sql(TEAM_SELECT + " ORDER BY t.code").query((rs, i) -> new MasterTeam(rs.getString("code"),
                rs.getString("name"), rs.getString("city"), rs.getInt("registrations"))).list();
    }

    public MasterTeam team(String code) {
        return jdbc.sql(TEAM_SELECT + " WHERE t.code = :code").param("code", code)
                .query((rs, i) -> new MasterTeam(rs.getString("code"), rs.getString("name"), rs.getString("city"), rs.getInt("registrations")))
                .optional().orElseThrow(() -> new SetupExceptions.NotFound("Team " + code + " not found"));
    }

    @Transactional
    public MasterTeam createTeam(String code, TeamRequest body) {
        if (code == null || !code.matches("[A-Z0-9]{2,8}")) {
            throw new IllegalArgumentException("Team code must contain 2-8 uppercase letters or digits");
        }
        validateTeam(body);
        jdbc.sql("INSERT INTO team (code, name, city) VALUES (:code, :name, :city)")
                .param("code", code).param("name", body.name().trim()).param("city", clean(body.city())).update();
        return team(code);
    }

    @Transactional
    public MasterTeam updateTeam(String code, TeamRequest body) {
        validateTeam(body);
        if (body.originalName() == null) {
            throw new IllegalArgumentException("originalName and the original city are required for a concurrent-safe edit");
        }
        team(code);
        int updated = jdbc.sql("""
                UPDATE team SET name = :name, city = :city
                 WHERE code = :code AND name = :originalName
                   AND (city = :originalCity OR (city IS NULL AND :originalCity IS NULL))""")
                .param("code", code).param("name", body.name().trim()).param("city", clean(body.city()))
                .param("originalName", body.originalName()).param("originalCity", body.originalCity(), java.sql.Types.VARCHAR).update();
        if (updated == 0) {
            throw new SetupExceptions.Conflict("Team changed. Reload before saving; no data was overwritten");
        }
        return team(code);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void deleteTeam(String code) {
        team(code);
        List<String> blockers = new ArrayList<>();
        add(blockers, count("SELECT COUNT(*) FROM competition_season_team WHERE team_code = :value", code), "season registrations");
        for (String table : List.of("fixture", "generated_match", "external_commitment")) {
            String label = switch (table) {
                case "fixture" -> "fixture match references";
                case "generated_match" -> "generated match references";
                default -> "external match references";
            };
            add(blockers, count("SELECT COUNT(*) FROM " + table + " WHERE home = :value OR away = :value", code), label);
        }
        add(blockers, count("SELECT COUNT(*) FROM team_pair_rule WHERE team_a = :value OR team_b = :value", code), "team pairing rules");
        long seasonRules = jdbc.sql("SELECT rule_type, team_a, team_b, team, teams, definition FROM constraint_rule")
                .query((rs, i) -> RuleDefinitionCodec.hydrate(null, rs.getString("rule_type"), rs.getString("team_a"),
                        rs.getString("team_b"), rs.getString("team"), rs.getString("teams"), null, null, null,
                        null, null, null, rs.getString("definition"), null))
                .list().stream().filter(r -> code.equals(r.a()) || code.equals(r.b()) || code.equals(r.team())
                        || containsTeam(r.teams(), code)).count();
        add(blockers, seasonRules, "season restriction references");
        long assignedRules = jdbc.sql("SELECT team_a, team_b, team, teams FROM rule_scope_assignment").query().listOfRows().stream()
                .filter(r -> code.equals(r.get("team_a")) || code.equals(r.get("team_b"))
                        || code.equals(r.get("team")) || containsTeam(r.get("teams"), code)).count();
        add(blockers, assignedRules, "assigned restriction references");
        for (String table : List.of("assign_group", "external_commitment", "generated_match")) {
            String column = table.equals("assign_group") ? "teams" : "home";
            long references = jdbc.sql("SELECT " + column + " FROM " + table).query(String.class).list().stream()
                    .filter(list -> table.equals("assign_group") || (list != null && list.contains(",")))
                    .filter(list -> containsTeam(list, code)).count();
            String label = switch (table) {
                case "assign_group" -> "scheduling group references";
                case "external_commitment" -> "external match team lists";
                default -> "generated match team lists";
            };
            add(blockers, references, label);
        }
        long candidates = jdbc.sql("SELECT position_teams FROM draw_candidate_league").query(String.class).list().stream()
                .filter(list -> containsTeam(list, code)).count();
        add(blockers, candidates, "draw candidate references");
        ensureEmpty("team " + code, blockers, "Remove registrations, match and restriction references first, or keep the shared team");
        jdbc.sql("DELETE FROM team WHERE code = :value").param("value", code).update();
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void deleteFederation(String code) {
        if (count("SELECT COUNT(*) FROM federation WHERE code = :value", code) == 0) {
            throw new SetupExceptions.NotFound("Federation " + code + " not found");
        }
        List<String> blockers = new ArrayList<>();
        if ("FRMF".equals(code)) {
            blockers.add("System reference catalog: FRMF supplies the default rule types for new federations");
        }
        add(blockers, count("SELECT COUNT(*) FROM competition WHERE federation_code = :value", code), "competitions");
        add(blockers, count("SELECT COUNT(*) FROM rule_scope_assignment WHERE federation_code = :value", code), "assigned restrictions");
        ensureEmpty("federation " + code, blockers, "Remove its competitions and assigned restrictions first, or deactivate the federation to retain its data");
        jdbc.sql("DELETE FROM rule_type_allowed_scope WHERE federation_code = :value").param("value", code).update();
        jdbc.sql("DELETE FROM rule_type_catalog WHERE federation_code = :value").param("value", code).update();
        jdbc.sql("DELETE FROM federation WHERE code = :value").param("value", code).update();
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void deleteCompetition(String id) {
        catalog.competition(id);
        List<String> blockers = new ArrayList<>();
        add(blockers, count("SELECT COUNT(*) FROM competition_season WHERE competition_id = :value", id), "seasons");
        add(blockers, count("SELECT COUNT(*) FROM competition_season WHERE depends_on_competition_id = :value", id), "dependent seasons");
        add(blockers, count("SELECT COUNT(*) FROM competition_alias WHERE competition_id = :value", id), "competition aliases");
        add(blockers, count("SELECT COUNT(*) FROM rule_scope_assignment WHERE competition_id = :value", id), "assigned restrictions");
        add(blockers, count("SELECT COUNT(*) FROM draw_batch_league WHERE competition_id = :value", id), "draw batch references");
        String leagueCode = catalog.competition(id).code();
        add(blockers, count("SELECT COUNT(*) FROM external_commitment WHERE competition_code = :value", leagueCode), "external match references");
        add(blockers, count("SELECT COUNT(*) FROM generated_match WHERE competition_code = :value", leagueCode), "generated match references");
        ensureEmpty("competition " + id, blockers, "Remove its seasons and other references first; calendars are never deleted automatically");
        jdbc.sql("DELETE FROM competition WHERE id = :value").param("value", id).update();
    }

    public List<SetupDtos.CompetitionSeasonRef> seasons(String id) {
        catalog.competition(id);
        return setups.listCompetitions().stream().filter(s -> id.equals(s.competitionId())).toList();
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public SetupDtos.SeasonSetup createSeason(String id, String season, CreateSeason body) {
        catalog.competition(id);
        if (season == null || !season.matches("[0-9]{4}-[0-9]{2}")
                || Integer.parseInt(season.substring(5)) != (Integer.parseInt(season.substring(0, 4)) + 1) % 100) {
            throw new IllegalArgumentException("Season must be YYYY-YY with consecutive years, for example 2026-27");
        }
        if (body == null || body.seasonStart() == null || body.seasonEnd() == null || body.startSecondLeg() == null
                || !body.seasonStart().isBefore(body.seasonEnd()) || body.startSecondLeg().isBefore(body.seasonStart())
                || body.startSecondLeg().isAfter(body.seasonEnd()) || (body.firstRoundDate() != null
                && (body.firstRoundDate().isBefore(body.seasonStart()) || body.firstRoundDate().isAfter(body.startSecondLeg())))) {
            throw new IllegalArgumentException("Provide valid season dates, a second-leg start within the season and an optional first-round date before the second leg");
        }
        if (count("SELECT COUNT(*) FROM season WHERE id = :value", season) == 0) {
            jdbc.sql("INSERT INTO season (id) VALUES (:value)").param("value", season).update();
        }
        jdbc.sql("""
                INSERT INTO competition_season (competition_id, season_id, season_start, season_end, first_round_date,
                    start_second_leg, max_matches_per_day, max_days_advance_match, max_days_postpone_match,
                    allow_maj, rounds_forward_look, time_assigns_forward_look, last_round_to_assign, round_calculation_order)
                VALUES (:id, :season, :start, :end, :first, :second, 4, 7, 70, :maj, 1, 1, 1, '')""")
                .param("id", id).param("season", season).param("start", body.seasonStart()).param("end", body.seasonEnd())
                .param("first", body.firstRoundDate()).param("second", body.startSecondLeg()).param("maj", false).update();
        return setups.load(id, season);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void deleteSeason(String id, String season) {
        long cs = setups.competitionSeasonId(id, season);
        List<String> blockers = new ArrayList<>();
        Map<String, String> referencesByTable = Map.ofEntries(
                Map.entry("competition_season_team", "participants"), Map.entry("timeslot", "time slots"),
                Map.entry("assign_group", "scheduling groups"), Map.entry("league_round", "matchdays"),
                Map.entry("fixture", "fixtures"), Map.entry("external_commitment", "external matches"),
                Map.entry("blackout", "blackout periods"), Map.entry("rest_rule", "rest rules"),
                Map.entry("team_pair_rule", "team pairing rules"), Map.entry("constraint_rule", "season restrictions"),
                Map.entry("generation_version", "generated versions"), Map.entry("planning_job", "planning jobs"),
                Map.entry("draw_batch", "draw batches"));
        for (Map.Entry<String, String> reference : referencesByTable.entrySet()) {
            long references = jdbc.sql("SELECT COUNT(*) FROM " + reference.getKey() + " WHERE competition_season_id = :cs")
                    .param("cs", cs).query(Long.class).single();
            add(blockers, references, reference.getValue());
        }
        add(blockers, jdbc.sql("SELECT COUNT(*) FROM rule_scope_assignment WHERE competition_id = :id AND season_code = :season")
                .param("id", id).param("season", season).query(Long.class).single(), "assigned restrictions");
        add(blockers, jdbc.sql("SELECT COUNT(*) FROM competition_season WHERE depends_on_competition_id = :id AND season_id = :season")
                .param("id", id).param("season", season).query(Long.class).single(), "dependent seasons");
        for (String table : List.of("draw_batch_league", "draw_candidate_league")) {
            add(blockers, jdbc.sql("SELECT COUNT(*) FROM " + table + " WHERE competition_season_id = :cs")
                    .param("cs", cs).query(Long.class).single(),
                    table.equals("draw_batch_league") ? "draw batch references" : "draw candidate references");
        }
        ensureEmpty("season " + id + "/" + season, blockers, "Remove its participants, configuration and references first; generated calendars and jobs prevent deletion");
        jdbc.sql("DELETE FROM competition_season WHERE id = :cs").param("cs", cs).update();
    }

    private long count(String sql, String value) {
        return jdbc.sql(sql).param("value", value).query(Long.class).single();
    }

    private static boolean containsTeam(Object list, String code) {
        return list != null && Arrays.stream(list.toString().split(",")).map(String::trim).anyMatch(code::equals);
    }

    private static void add(List<String> blockers, long count, String label) {
        if (count > 0) {
            blockers.add(count + " " + label);
        }
    }

    private static void ensureEmpty(String entity, List<String> blockers, String guidance) {
        if (!blockers.isEmpty()) {
            throw new SetupExceptions.BlockedDelete("Cannot delete " + entity + ": " + String.join("; ", blockers)
                    + ". " + guidance + ". No data was deleted.", blockers);
        }
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void validateTeam(TeamRequest body) {
        if (body == null || body.name() == null || body.name().isBlank() || body.name().trim().length() > 160
                || (body.city() != null && body.city().trim().length() > 80)) {
            throw new IllegalArgumentException("Team name is required (maximum 160 characters); city has maximum 80 characters");
        }
    }
}
