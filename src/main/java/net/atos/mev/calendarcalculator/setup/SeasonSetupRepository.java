package net.atos.mev.calendarcalculator.setup;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import net.atos.mev.calendarcalculator.setup.SetupDtos.SeasonSetup;

/**
 * Lectura y reemplazo transaccional de la configuración de una competición y temporada.
 * Bloqueo optimista con {@code competition_season.row_version}: el PUT debe traer el valor leído.
 * Los fixtures se leen con {@code ORDER BY round_no, list_order} (el orden es significativo).
 */
@Repository
public class SeasonSetupRepository {

    private final JdbcClient jdbc;
    private final RoundDateRepository roundDates;

    public SeasonSetupRepository(JdbcClient jdbc, RoundDateRepository roundDates) {
        this.jdbc = jdbc;
        this.roundDates = roundDates;
    }

    public List<SetupDtos.CompetitionSeasonRef> listCompetitions() {
        return jdbc.sql("""
                  SELECT cs.id, c.id AS cid, c.code, c.name, cs.season_id, c.storage_dir, c.output_suffix, cs.depends_on_competition_id,
                          COALESCE(c.federation_code, 'FRMF') AS federation_id
                  FROM competition_season cs JOIN competition c ON c.id = cs.competition_id
                 ORDER BY c.sort_order, cs.season_id DESC""")
                .query((rs, i) -> new SetupDtos.CompetitionSeasonRef(rs.getLong("id"), rs.getString("cid"), rs.getString("code"),
                        rs.getString("name"), rs.getString("season_id"), rs.getString("storage_dir"), rs.getString("output_suffix"),
                        rs.getString("depends_on_competition_id"), rs.getString("federation_id")))
                .list();
    }

    public long competitionSeasonId(String competitionId, String season) {
        return jdbc.sql("SELECT id FROM competition_season WHERE competition_id = :c AND season_id = :s")
                .param("c", competitionId).param("s", season).query(Long.class).optional()
                .orElseThrow(() -> new SetupExceptions.NotFound("Unknown competition/season " + competitionId + " " + season));
    }

    public String competitionCode(String competitionId) {
        return jdbc.sql("SELECT code FROM competition WHERE id = :c").param("c", competitionId).query(String.class).optional()
                .orElseThrow(() -> new SetupExceptions.NotFound("Unknown competition " + competitionId));
    }

    @Transactional(readOnly = true)
    public SeasonSetup load(String competitionId, String season) {
        long cs = competitionSeasonId(competitionId, season);
        Map<String, Object> row = jdbc.sql("""
                SELECT cs.*, c.code FROM competition_season cs JOIN competition c ON c.id = cs.competition_id WHERE cs.id = :id""")
                .param("id", cs).query().singleRow();
        List<String> slots = jdbc.sql("SELECT slot FROM timeslot WHERE competition_season_id = :id ORDER BY list_order")
                .param("id", cs).query(String.class).list();
        List<SetupDtos.Team> teams = jdbc.sql("""
                SELECT t.code, COALESCE(cst.display_name, t.name) AS name, t.city, cst.no_light, cst.optimize_times
                  FROM competition_season_team cst JOIN team t ON t.code = cst.team_code
                 WHERE cst.competition_season_id = :id ORDER BY cst.list_order""")
                .param("id", cs)
                .query((rs, i) -> new SetupDtos.Team(rs.getString("code"), rs.getString("name"), rs.getString("city"),
                        rs.getBoolean("no_light"), rs.getBoolean("optimize_times")))
                .list();
        List<SetupDtos.Round> rounds = new ArrayList<>();
        jdbc.sql("SELECT id, round_no, label, leg FROM league_round WHERE competition_season_id = :id ORDER BY list_order")
                .param("id", cs).query().listOfRows().forEach(r -> {
                    List<LocalDate> dates = jdbc.sql("SELECT play_date FROM round_date WHERE league_round_id = :r ORDER BY play_date")
                            .param("r", r.get("id")).query((rs, i) -> rs.getDate(1).toLocalDate()).list();
                    rounds.add(new SetupDtos.Round((Integer) r.get("round_no"), (String) r.get("label"), (Integer) r.get("leg"), dates));
                });
        List<SetupDtos.Fixture> fixtures = jdbc.sql("""
                SELECT f.id, f.round_no, f.home, f.away, f.locked,
                       CASE WHEN s.round_no IS NULL THEN f.play_date ELSE s.play_date END AS play_date,
                       CASE WHEN s.round_no IS NULL THEN f.play_hour ELSE s.play_hour END AS play_hour
                  FROM fixture f
                  LEFT JOIN season_match_state s
                    ON s.competition_season_id = f.competition_season_id
                   AND s.round_no = f.round_no AND s.home = f.home AND s.away = f.away
                 WHERE f.competition_season_id = :id ORDER BY f.round_no, f.list_order""")
                .param("id", cs)
                .query((rs, i) -> new SetupDtos.Fixture(rs.getLong("id"), rs.getInt("round_no"), rs.getString("home"), rs.getString("away"),
                        rs.getBoolean("locked"), date(rs, "play_date"), rs.getString("play_hour")))
                .list();
        List<SetupDtos.ExternalMatch> external = jdbc.sql("""
                SELECT id, competition_code, round_label, play_date, play_hour, home, away FROM external_commitment
                 WHERE competition_season_id = :id ORDER BY list_order""")
                .param("id", cs)
                .query((rs, i) -> new SetupDtos.ExternalMatch(rs.getLong("id"), rs.getString("competition_code"), rs.getString("round_label"),
                        date(rs, "play_date"), rs.getString("play_hour"), rs.getString("home"), rs.getString("away")))
                .list();
        List<SetupDtos.Blackout> blackouts = jdbc.sql("SELECT id, label, date_from, date_to FROM blackout WHERE competition_season_id = :id ORDER BY date_from, id")
                .param("id", cs)
                .query((rs, i) -> new SetupDtos.Blackout(rs.getLong("id"), rs.getString("label"), date(rs, "date_from"), date(rs, "date_to")))
                .list();
        Map<String, Integer> rest = new LinkedHashMap<>();
        jdbc.sql("SELECT competition_code, rest_days FROM rest_rule WHERE competition_season_id = :id ORDER BY list_order")
                .param("id", cs).query().listOfRows().forEach(r -> rest.put((String) r.get("competition_code"), (Integer) r.get("rest_days")));
        List<List<String>> notHome = pairs(cs, "NOT_HOME_SAME_DAY");
        List<List<String>> derbies = pairs(cs, "DERBY");
        List<SetupDtos.Rule> rules = jdbc.sql("SELECT * FROM constraint_rule WHERE competition_season_id = :id ORDER BY list_order")
                .param("id", cs)
                .query((rs, i) -> RuleDefinitionCodec.hydrate(rs.getLong("id"), rs.getString("rule_type"), rs.getString("team_a"),
                        rs.getString("team_b"), rs.getString("team"), rs.getString("teams"), rs.getString("rounds"),
                        (Integer) rs.getObject("min_rounds"), (Integer) rs.getObject("int_param"), date(rs, "date_from"), date(rs, "date_to"),
                        date(rs, "fixed_date"), rs.getString("definition"), rs.getString("note")))
                .list();
        List<SetupDtos.AssignGroup> groups = jdbc.sql("SELECT group_no, teams, rounds_forward_look FROM assign_group WHERE competition_season_id = :id ORDER BY group_no")
                .param("id", cs)
                .query((rs, i) -> new SetupDtos.AssignGroup(rs.getInt(1), rs.getString(2), rs.getInt(3))).list();

        SetupDtos.General general = new SetupDtos.General(ld(row.get("season_start")), ld(row.get("season_end")), ld(row.get("first_round_date")),
                ld(row.get("start_second_leg")), (Integer) row.get("max_matches_per_day"), (Integer) row.get("max_days_advance_match"),
                (Integer) row.get("max_days_postpone_match"), (Boolean) row.get("allow_maj"), (Integer) row.get("rounds_forward_look"),
                (Integer) row.get("time_assigns_forward_look"), (Integer) row.get("last_round_to_assign"), (String) row.get("matchday_pattern"));
        Map<String, String> colours = new LinkedHashMap<>();
        jdbc.sql("SELECT competition_code, colour FROM season_export_colour WHERE competition_season_id = :id ORDER BY competition_code")
                .param("id", cs).query().listOfRows().forEach(r -> colours.put((String) r.get("competition_code"), (String) r.get("colour")));
        SetupDtos.EngineSettings engine = new SetupDtos.EngineSettings((Integer) row.get("groups_to_run"), (Integer) row.get("group_to_optimize_times"),
                (String) row.get("groups_can_go_to_maj"), (String) row.get("round_calculation_order"), groups, colours);
        return new SeasonSetup(competitionId, (String) row.get("code"), season, general, slots,
                new SetupDtos.Ramadan(ld(row.get("ramadan_start")), ld(row.get("ramadan_end")), (String) row.get("ramadan_slot")),
                (String) row.get("no_light_slot"), teams, rounds, fixtures, external, blackouts, rest, notHome, derbies, rules,
                (String) row.get("depends_on_competition_id"), (Boolean) row.get("include_dependency"), engine,
                (Integer) row.get("row_version"), instant(row.get("updated_at")));
    }

    /**
     * Reemplaza la configuración completa en una transacción. Lanza 409 si {@code rowVersion}
     * no coincide con el guardado. Devuelve el nuevo row_version.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public int replace(SeasonSetup s) {
        var issues = P2Settings.validate(s);
        if (SetupValidator.hasErrors(issues)) {
            throw new SetupExceptions.Invalid("Invalid P2 settings", issues);
        }
        long cs = competitionSeasonId(s.competitionId(), s.season());
        assertProtectedMatchesUnchanged(cs, s.fixtures());
        SetupDtos.General g = s.general();
        SetupDtos.EngineSettings e = s.engine();
        int updated = jdbc.sql("""
                UPDATE competition_season SET season_start = :start, season_end = :end, first_round_date = :first, start_second_leg = :leg2,
                       max_matches_per_day = :maxDay, max_days_advance_match = :adv, max_days_postpone_match = :post, allow_maj = :maj,
                       rounds_forward_look = :look, time_assigns_forward_look = :tlook, last_round_to_assign = :last, matchday_pattern = :pattern,
                       ramadan_start = :rs, ramadan_end = :re, ramadan_slot = :rslot, no_light_slot = :nls, include_dependency = :dep,
                       groups_can_go_to_maj = CASE WHEN :majFlag = 1 THEN (CASE WHEN groups_can_go_to_maj = '0' THEN '1' ELSE groups_can_go_to_maj END) ELSE '0' END,
                       row_version = row_version + 1, updated_at = CURRENT_TIMESTAMP
                 WHERE id = :id AND row_version = :rv""")
                .param("start", g.seasonStart()).param("end", g.seasonEnd()).param("first", g.firstRoundDate()).param("leg2", g.startSecondLeg())
                .param("maxDay", g.maxMatchesPerDay()).param("adv", g.maxDaysAdvanceMatch()).param("post", g.maxDaysPostponeMatch())
                .param("maj", g.allowMAJ()).param("majFlag", g.allowMAJ() ? 1 : 0).param("look", g.roundsForwardLook()).param("tlook", g.timeAssignsForwardLook())
                .param("last", g.lastRoundToAssign()).param("pattern", g.matchdayPattern() == null ? "sat-sun" : g.matchdayPattern())
                .param("rs", s.ramadan() == null ? null : s.ramadan().start()).param("re", s.ramadan() == null ? null : s.ramadan().end())
                .param("rslot", s.ramadan() == null ? null : s.ramadan().slot()).param("nls", s.noLightSlot())
                .param("dep", s.includeDependency()).param("id", cs).param("rv", s.rowVersion())
                .update();
        if (updated == 0) {
            throw new SetupExceptions.Conflict("The configuration was saved by someone else (rowVersion " + s.rowVersion() + "). Reload.");
        }
        if (e != null) {
            jdbc.sql("""
                    UPDATE competition_season SET groups_to_run = :g, group_to_optimize_times = :o,
                        round_calculation_order = :ord, groups_can_go_to_maj = :maj WHERE id = :id""")
                    .param("g", e.groupsToRun()).param("o", e.groupToOptimizeTimes()).param("ord", e.roundCalculationOrder() == null ? "" : e.roundCalculationOrder())
                    .param("maj", e.groupsCanGoToMAJ() == null ? "0" : e.groupsCanGoToMAJ()).param("id", cs).update();
            if (e.assignGroups() != null) {
                jdbc.sql("DELETE FROM assign_group WHERE competition_season_id = :id").param("id", cs).update();
                for (var group : e.assignGroups()) {
                    jdbc.sql("INSERT INTO assign_group (competition_season_id, group_no, teams, rounds_forward_look) VALUES (:id, :n, :t, :l)")
                            .param("id", cs).param("n", group.groupNo()).param("t", group.teams())
                            .param("l", group.groupNo() == 1 ? g.roundsForwardLook() : group.roundsForwardLook()).update();
                }
            }
            if (e.exportColours() != null) {
                jdbc.sql("DELETE FROM season_export_colour WHERE competition_season_id = :id").param("id", cs).update();
                e.exportColours().forEach((code, colour) ->
                        jdbc.sql("INSERT INTO season_export_colour (competition_season_id, competition_code, colour) VALUES (:id, :c, :v)")
                                .param("id", cs).param("c", code).param("v", colour).update());
            }
        }
        // grupo 1 = equipos de la liga; su roundsForwardLook se edita en pantalla
        jdbc.sql("UPDATE assign_group SET rounds_forward_look = :look WHERE competition_season_id = :id AND group_no = 1")
                .param("look", g.roundsForwardLook()).param("id", cs).update();

        for (String t : List.of("timeslot", "competition_season_team", "league_round", "fixture", "external_commitment", "blackout",
                "rest_rule", "team_pair_rule", "constraint_rule")) {
            jdbc.sql("DELETE FROM " + t + " WHERE competition_season_id = :id").param("id", cs).update();
        }
        for (int i = 0; i < s.timeslots().size(); i++) {
            jdbc.sql("INSERT INTO timeslot (competition_season_id, list_order, slot) VALUES (:id, :o, :s)")
                    .param("id", cs).param("o", i).param("s", s.timeslots().get(i)).update();
        }
        for (int i = 0; i < s.teams().size(); i++) {
            SetupDtos.Team t = s.teams().get(i);
            int existing = jdbc.sql("UPDATE team SET city = COALESCE(:city, city) WHERE code = :c")
                    .param("c", t.code()).param("city", t.city()).update();
            if (existing == 0) {
                jdbc.sql("INSERT INTO team (code, name, city) VALUES (:c, :n, :city)")
                        .param("c", t.code()).param("n", t.name() == null ? t.code() : t.name()).param("city", t.city()).update();
            }
            jdbc.sql("""
                    INSERT INTO competition_season_team (competition_season_id, team_code, list_order, display_name, no_light, optimize_times)
                    VALUES (:id, :c, :o, :n, :nl, :opt)""")
                    .param("id", cs).param("c", t.code()).param("o", i).param("n", t.name()).param("nl", t.noLight()).param("opt", t.optimizeTimes())
                    .update();
        }
        for (int i = 0; i < s.rounds().size(); i++) {
            SetupDtos.Round r = s.rounds().get(i);
            GeneratedKeyHolder keys = new GeneratedKeyHolder();
            jdbc.sql("INSERT INTO league_round (competition_season_id, list_order, round_no, label, leg) VALUES (:id, :o, :n, :l, :leg)")
                    .param("id", cs).param("o", i).param("n", r.n()).param("l", r.label()).param("leg", r.leg()).update(keys, "id");
            long rid = java.util.Objects.requireNonNull(keys.getKey(), "Missing league round ID").longValue();
            for (LocalDate d : r.dates() == null ? List.<LocalDate>of() : r.dates().stream().distinct().toList()) {
                jdbc.sql("INSERT INTO round_date (league_round_id, play_date) VALUES (:r, :d)").param("r", rid).param("d", d).update();
            }
        }
        for (int i = 0; i < s.fixtures().size(); i++) {
            SetupDtos.Fixture f = s.fixtures().get(i);
            jdbc.sql("""
                    INSERT INTO fixture (competition_season_id, round_no, list_order, home, away, locked, play_date, play_hour)
                    VALUES (:id, :r, :o, :h, :a, :l, :d, :hr)""")
                    .param("id", cs).param("r", f.round()).param("o", i).param("h", f.home()).param("a", f.away()).param("l", f.locked())
                    .param("d", f.date()).param("hr", f.hour()).update();
        }
        jdbc.sql("""
                INSERT INTO season_match_state
                    (competition_season_id, round_no, home, away, status, play_date, play_hour, updated_at)
                SELECT f.competition_season_id, f.round_no, f.home, f.away,
                       CASE WHEN f.play_date IS NULL THEN 'PENDIENTE' ELSE 'PLANIFICADA' END,
                       f.play_date, f.play_hour, CURRENT_TIMESTAMP
                  FROM fixture f
                 WHERE f.competition_season_id = :cs
                   AND NOT EXISTS (
                       SELECT 1 FROM season_match_state s
                        WHERE s.competition_season_id = f.competition_season_id
                          AND s.round_no = f.round_no AND s.home = f.home AND s.away = f.away
                   )""")
                .param("cs", cs).update();
        for (SetupDtos.Fixture fixture : s.fixtures()) {
            String status = fixture.date() == null ? MatchStateService.PENDING : MatchStateService.PLANNED;
            jdbc.sql("""
                       UPDATE season_match_state
                          SET status = :status, play_date = :date, play_hour = :hour, updated_at = CURRENT_TIMESTAMP
                        WHERE competition_season_id = :cs AND round_no = :round AND home = :home AND away = :away
                          AND status NOT IN ('ENTREGADA', 'JUGADA')""")
                       .param("status", status).param("date", fixture.date()).param("hour", fixture.hour())
                       .param("cs", cs).param("round", fixture.round()).param("home", fixture.home()).param("away", fixture.away())
                       .update();
        }
        List<SetupDtos.ExternalMatch> ext = s.external() == null ? List.of() : s.external();
        for (int i = 0; i < ext.size(); i++) {
            SetupDtos.ExternalMatch x = ext.get(i);
            jdbc.sql("""
                    INSERT INTO external_commitment (competition_season_id, list_order, competition_code, round_label, play_date, play_hour, home, away)
                    VALUES (:id, :o, :c, :r, :d, :h, :home, :away)""")
                    .param("id", cs).param("o", i).param("c", x.competition()).param("r", x.round()).param("d", x.date()).param("h", x.hour())
                    .param("home", x.home()).param("away", x.away()).update();
        }
        for (SetupDtos.Blackout b : s.blackouts() == null ? List.<SetupDtos.Blackout>of() : s.blackouts()) {
            jdbc.sql("INSERT INTO blackout (competition_season_id, label, date_from, date_to) VALUES (:id, :l, :f, :t)")
                    .param("id", cs).param("l", b.label()).param("f", b.from()).param("t", b.to()).update();
        }
        int o = 0;
        for (Map.Entry<String, Integer> r : (s.restDays() == null ? Map.<String, Integer>of() : s.restDays()).entrySet()) {
            jdbc.sql("INSERT INTO rest_rule (competition_season_id, list_order, competition_code, rest_days) VALUES (:id, :o, :c, :d)")
                    .param("id", cs).param("o", o++).param("c", r.getKey()).param("d", r.getValue()).update();
        }
        insertPairs(cs, "NOT_HOME_SAME_DAY", s.notHomeSameDay());
        insertPairs(cs, "DERBY", s.derbies());
        List<SetupDtos.Rule> rules = s.rules() == null ? List.of() : s.rules();
        for (int i = 0; i < rules.size(); i++) {
            SetupDtos.Rule r = rules.get(i);
            jdbc.sql("""
                    INSERT INTO constraint_rule (competition_season_id, list_order, rule_type, team_a, team_b, team, teams, rounds, min_rounds,
                                                 int_param, date_from, date_to, fixed_date, definition, note)
                    VALUES (:id, :o, :type, :a, :b, :team, :teams, :rounds, :min, :intParam, :from, :to, :date, :def, :note)""")
                    .param("id", cs).param("o", i).param("type", r.type()).param("a", r.a()).param("b", r.b()).param("team", r.team())
                    .param("teams", r.teams()).param("rounds", r.rounds()).param("min", r.minRounds()).param("intParam", r.intParam())
                    .param("from", r.from()).param("to", r.to()).param("date", r.date())
                    .param("def", RuleDefinitionCodec.toDefinition(r)).param("note", r.note()).update();
        }
        return s.rowVersion() + 1;
    }

        @Transactional
        public void lockFixture(String competitionId, String season, SetupDtos.FixtureLockRequest body) {
                long cs = competitionSeasonId(competitionId, season);
                Map<String, Object> fixture = jdbc.sql("""
                                SELECT id, round_no
                                  FROM fixture
                                 WHERE competition_season_id = :id AND home = :home AND away = :away""")
                                .param("id", cs).param("home", body.home()).param("away", body.away())
                        .query().listOfRows().stream().findFirst().orElseThrow(() -> new SetupExceptions.NotFound(
                                                "Fixture " + body.home() + "-" + body.away() + " not found"));

        int round = ((Number) fixture.get("round_no")).intValue();
        String currentStatus = jdbc.sql("""
                        SELECT status FROM season_match_state
                         WHERE competition_season_id = :cs AND round_no = :r AND home = :home AND away = :away""")
                        .param("cs", cs).param("r", round).param("home", body.home()).param("away", body.away())
                        .query(String.class).optional().orElse(MatchStateService.PENDING);
        if (MatchStateService.DELIVERED.equals(currentStatus) || MatchStateService.PLAYED.equals(currentStatus)) {
                        throw new SetupExceptions.Conflict("Delivered or played fixtures require the exceptional reprogramming endpoint");
        }
        if (body.date() == null && MatchStateService.PLANNED.equals(currentStatus)) {
                        throw new SetupExceptions.Conflict("A planned fixture can return to pending only through an explicit replan");
        }
                        jdbc.sql("""
                                        UPDATE fixture
                                   SET locked = :locked, play_date = :date, play_hour = :hour
                                 WHERE id = :id""")
                                .param("locked", body.locked()).param("date", body.date()).param("hour", body.hour())
                                .param("id", fixture.get("id")).update();
        if (body.date() != null) {
                jdbc.sql("""
                                UPDATE season_match_state
                                   SET status = CASE WHEN status = 'PENDIENTE' THEN 'PLANIFICADA' ELSE status END,
                                       play_date = :date, play_hour = :hour, updated_at = CURRENT_TIMESTAMP
                                 WHERE competition_season_id = :cs AND round_no = :r AND home = :home AND away = :away""")
                                .param("date", body.date()).param("hour", body.hour()).param("cs", cs).param("r", round)
                                .param("home", body.home()).param("away", body.away()).update();
        }

                if (body.date() != null) roundDates.include(cs, round, body.date());
        }

        private void assertProtectedMatchesUnchanged(long cs, List<SetupDtos.Fixture> requestedFixtures) {
                List<Map<String, Object>> protectedMatches = jdbc.sql("""
                        SELECT round_no, home, away, play_date, play_hour, status
                          FROM season_match_state
                         WHERE competition_season_id = :cs AND status IN ('PLANIFICADA', 'ENTREGADA', 'JUGADA')""")
                        .param("cs", cs).query().listOfRows();
                for (Map<String, Object> row : protectedMatches) {
                    int round = ((Number) row.get("round_no")).intValue();
                    String home = (String) row.get("home");
                    String away = (String) row.get("away");
                    String status = (String) row.get("status");
                    LocalDate protectedDate = ld(row.get("play_date"));
                    String protectedHour = (String) row.get("play_hour");
                    boolean unchanged = requestedFixtures.stream().anyMatch(f -> f.round() == round && home.equals(f.home())
                            && away.equals(f.away()) && Objects.equals(f.date(), protectedDate)
                            && Objects.equals(f.hour(), protectedHour));
                    if (!unchanged) {
                        String action = MatchStateService.PLANNED.equals(status)
                                ? "Cancel its planning through the audited unplan endpoint first"
                                : "Saving setup would alter protected match";
                        throw new SetupExceptions.Conflict(action + " " + round + " " + home + "-" + away);
                    }
                }
        }

    private void insertPairs(long cs, String kind, List<List<String>> pairs) {
        if (pairs == null) {
            return;
        }
        for (int i = 0; i < pairs.size(); i++) {
            jdbc.sql("INSERT INTO team_pair_rule (competition_season_id, kind, list_order, team_a, team_b) VALUES (:id, :k, :o, :a, :b)")
                    .param("id", cs).param("k", kind).param("o", i).param("a", pairs.get(i).get(0)).param("b", pairs.get(i).get(1)).update();
        }
    }

    private List<List<String>> pairs(long cs, String kind) {
        return jdbc.sql("SELECT team_a, team_b FROM team_pair_rule WHERE competition_season_id = :id AND kind = :k ORDER BY list_order")
                .param("id", cs).param("k", kind).query((rs, i) -> List.of(rs.getString(1), rs.getString(2))).list();
    }

    private static LocalDate date(ResultSet rs, String col) throws SQLException {
        Date d = rs.getDate(col);
        return d == null ? null : d.toLocalDate();
    }

    private static LocalDate ld(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Date d) {
            return d.toLocalDate();
        }
        return (LocalDate) o;
    }

    private static Instant instant(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof java.sql.Timestamp t) {
            return t.toInstant();
        }
        if (o instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        if (o instanceof Instant instant) {
            return instant;
        }
        if ("microsoft.sql.DateTimeOffset".equals(o.getClass().getName())) {
            try {
                Object value = o.getClass().getMethod("getOffsetDateTime").invoke(o);
                return ((OffsetDateTime) value).toInstant();
            } catch (ReflectiveOperationException | ClassCastException exception) {
                throw new IllegalArgumentException("Could not convert SQL Server DateTimeOffset", exception);
            }
        }
        throw new IllegalArgumentException("Unsupported timestamp type: " + o.getClass().getName());
    }
}
