package net.atos.mev.calendarcalculator.setup;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

@Repository
public class SetupCatalogRepository {

    private static final String DEFAULT_RULE_TYPES =
            "'DerbyTeams','MatchNotInRound','SeparateMatches','LimitHomeTogether','PlayTogether',"
                    + "'NotConsecutiveFarAway','LimitAgainstTeamsInRounds','LimitAgainstTeamsInRoundsHome','ForcePositions',"
                    + "'HalfMatchesAtHome','VenueUnavailable','FixedHomeDate'";
    private static final int DEFAULT_RULE_TYPE_COUNT = 12;
    private static final SetupDtos.Federation DEFAULT_FEDERATION = new SetupDtos.Federation(
            "FRMF", "FRMF", "Fédération Royale Marocaine de Football", "MA", "Africa/Casablanca", "ACTIVE", null, null);

    private final JdbcClient jdbc;
    private final JsonMapper json;

    public SetupCatalogRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public List<SetupDtos.Federation> listFederations(boolean includeInactive) {
        String sql = """
                SELECT code, name, country_code, time_zone, status, created_at, updated_at
                  FROM federation
                """ + (includeInactive ? " ORDER BY code" : " WHERE status = 'ACTIVE' ORDER BY code");
        return jdbc.sql(sql).query((rs, i) -> new SetupDtos.Federation(
                rs.getString("code"), rs.getString("code"), rs.getString("name"), rs.getString("country_code"),
                rs.getString("time_zone"), rs.getString("status"), ts(rs.getObject("created_at")), ts(rs.getObject("updated_at"))))
                .list();
    }

    public SetupDtos.Federation requireFederation(String code) {
        return federation(code).orElseThrow(() -> new SetupExceptions.NotFound("Federation " + code + " not found"));
    }

    public List<SetupDtos.RuleTypeDef> ruleTypes(String federationCode) {
        return jdbc.sql("""
                SELECT rule_type, technical_code, functional_name, functional_description, phase_code,
                       category_code, parameter_schema, support_status, engine_class, engine_version,
                       editable_definition, enabled
                  FROM rule_type_catalog
                 WHERE federation_code = :f
                 ORDER BY sort_order""")
                .param("f", federationCode)
                .query((rs, i) -> new SetupDtos.RuleTypeDef(
                        rs.getString("rule_type"),
                        rs.getString("functional_name"),
                        icon(rs.getString("rule_type")),
                        scope(rs.getString("phase_code")),
                        rs.getString("technical_code"),
                        rs.getString("functional_description"),
                        rs.getString("category_code"),
                        allowedScopes(federationCode, rs.getString("rule_type")),
                        rs.getString("support_status"),
                        rs.getString("engine_class"),
                        rs.getString("engine_version"),
                        rs.getBoolean("enabled"),
                        rs.getBoolean("editable_definition"),
                        params(rs.getString("parameter_schema"))))
                .list();
    }

    public SetupDtos.RuleTypeDef updateRuleTypeStatus(String federationCode, String ruleType, boolean enabled) {
        jdbc.sql("UPDATE rule_type_catalog SET enabled = :enabled WHERE federation_code = :f AND rule_type = :r")
                .param("enabled", enabled).param("f", federationCode).param("r", ruleType).update();
        return ruleTypes(federationCode).stream().filter(r -> r.type().equals(ruleType)).findFirst()
                .orElseThrow(() -> new SetupExceptions.NotFound("Rule type " + ruleType + " not found for federation " + federationCode));
    }

    public String federationCodeForCompetition(String competitionId) {
        try {
            return jdbc.sql("SELECT federation_code FROM competition WHERE id = :id")
                    .param("id", competitionId)
                    .query(String.class)
                    .optional()
                    .orElse(DEFAULT_FEDERATION.code());
        } catch (DataAccessException ignored) {
            return DEFAULT_FEDERATION.code();
        }
    }

    public List<SetupDtos.CompetitionSummary> competitions(String federationCode) {
        return jdbc.sql("""
                SELECT c.id, c.code, c.name, c.federation_code,
                       COUNT(DISTINCT cs.season_id) AS seasons,
                       COALESCE(MAX(team_stats.participants), 0) AS participants,
                       COALESCE((SELECT COUNT(*) FROM rule_type_catalog rtc
                                  WHERE rtc.federation_code = c.federation_code AND rtc.enabled = :catalogEnabled), 0) AS effective_rules
                  FROM competition c
             LEFT JOIN competition_season cs ON cs.competition_id = c.id
             LEFT JOIN (
                    SELECT cst.competition_season_id, COUNT(*) AS participants
                      FROM competition_season_team cst
                     GROUP BY cst.competition_season_id
                ) team_stats ON team_stats.competition_season_id = cs.id
                 WHERE c.federation_code = :f
              GROUP BY c.id, c.code, c.name, c.federation_code
              ORDER BY c.code""")
                .param("f", federationCode).param("catalogEnabled", true)
                .query((rs, i) -> summary(rs.getString("id"), rs.getString("code"), rs.getString("name"), rs.getString("federation_code"),
                        rs.getInt("seasons"), rs.getInt("participants"), rs.getInt("effective_rules")))
                .list();
    }

    public SetupDtos.CompetitionSummary competition(String competitionId) {
        return jdbc.sql("""
                SELECT c.id, c.code, c.name, c.federation_code,
                       COUNT(DISTINCT cs.season_id) AS seasons,
                       COALESCE(MAX(team_stats.participants), 0) AS participants,
                       COALESCE((SELECT COUNT(*) FROM rule_type_catalog rtc
                                  WHERE rtc.federation_code = c.federation_code AND rtc.enabled = :catalogEnabled), 0) AS effective_rules
                  FROM competition c
             LEFT JOIN competition_season cs ON cs.competition_id = c.id
             LEFT JOIN (
                    SELECT cst.competition_season_id, COUNT(*) AS participants
                      FROM competition_season_team cst
                     GROUP BY cst.competition_season_id
                ) team_stats ON team_stats.competition_season_id = cs.id
                 WHERE c.id = :id
              GROUP BY c.id, c.code, c.name, c.federation_code""")
                .param("id", competitionId).param("catalogEnabled", true)
                .query((rs, i) -> summary(rs.getString("id"), rs.getString("code"), rs.getString("name"), rs.getString("federation_code"),
                        rs.getInt("seasons"), rs.getInt("participants"), rs.getInt("effective_rules")))
                .optional()
                .orElseThrow(() -> new SetupExceptions.NotFound("Competition " + competitionId + " not found"));
    }

    @Transactional
    public SetupDtos.Federation createFederation(String code, SetupDtos.FederationUpsertRequest body) {
        if (code == null || !code.matches("[A-Z0-9_]{2,16}")) {
            throw new IllegalArgumentException("Federation code must contain 2-16 uppercase letters, digits or underscores");
        }
        validateFederation(body);
        jdbc.sql("""
                INSERT INTO federation (code, name, country_code, time_zone, status)
                VALUES (:code, :name, :country, :tz, 'ACTIVE')""")
                .param("code", code)
                .param("name", body.name())
                .param("country", body.countryCode())
                .param("tz", body.timeZone())
                .update();
        seedDefaultRuleTypes(code);
        return federation(code).orElseThrow(() -> new SetupExceptions.NotFound("Federation " + code + " not found after insert"));
    }

    public SetupDtos.Federation updateFederation(String code, SetupDtos.FederationUpsertRequest body) {
        validateFederation(body);
        int updated = jdbc.sql("""
                UPDATE federation
                   SET name = :name, country_code = :country, time_zone = :tz, updated_at = CURRENT_TIMESTAMP
                 WHERE code = :code""")
                .param("name", body.name())
                .param("country", body.countryCode())
                .param("tz", body.timeZone())
                .param("code", code)
                .update();
        if (updated == 0) {
            throw new SetupExceptions.NotFound("Federation " + code + " not found");
        }
        return federation(code).orElseThrow(() -> new SetupExceptions.NotFound("Federation " + code + " not found"));
    }

    public SetupDtos.Federation updateFederationStatus(String code, String status) {
        int updated = jdbc.sql("UPDATE federation SET status = :status, updated_at = CURRENT_TIMESTAMP WHERE code = :code")
                .param("status", status)
                .param("code", code)
                .update();
        if (updated == 0) {
            throw new SetupExceptions.NotFound("Federation " + code + " not found");
        }
        return federation(code).orElseThrow(() -> new SetupExceptions.NotFound("Federation " + code + " not found"));
    }

    private static void validateFederation(SetupDtos.FederationUpsertRequest body) {
        if (body == null || body.name() == null || body.name().isBlank() || body.name().length() > 160
                || body.countryCode() == null || !body.countryCode().matches("[A-Z]{2}")
                || body.timeZone() == null || body.timeZone().length() > 80) {
            throw new IllegalArgumentException("Federation name, two-letter uppercase country code and valid time zone are required");
        }
        try {
            java.time.ZoneId.of(body.timeZone());
        } catch (java.time.DateTimeException e) {
            throw new IllegalArgumentException("Unknown time zone: " + body.timeZone(), e);
        }
    }

    private SetupDtos.Federation toFederation(String code) {
        if ("FRMF".equals(code)) {
            return DEFAULT_FEDERATION;
        }
        return new SetupDtos.Federation(code, code, code, "MA", "Africa/Casablanca", "ACTIVE", null, null);
    }

    private List<String> allowedScopes(String federationCode, String ruleType) {
        return jdbc.sql("""
                SELECT scope_type
                  FROM rule_type_allowed_scope
                 WHERE federation_code = :f AND rule_type = :r
                 ORDER BY scope_type""")
                .param("f", federationCode).param("r", ruleType)
                .query(String.class).list();
    }

    private void seedDefaultRuleTypes(String federationCode) {
        int seededTypes = jdbc.sql("""
                INSERT INTO rule_type_catalog (
                    federation_code, rule_type, technical_code, functional_name, functional_description,
                    phase_code, category_code, parameter_schema, support_status, engine_class, engine_version,
                    editable_definition, enabled, sort_order
                )
                SELECT :targetFederation, base_catalog.rule_type, base_catalog.technical_code, base_catalog.functional_name,
                       base_catalog.functional_description, base_catalog.phase_code, base_catalog.category_code,
                       base_catalog.parameter_schema,
                       base_catalog.support_status, base_catalog.engine_class, base_catalog.engine_version,
                       base_catalog.editable_definition, base_catalog.enabled, base_catalog.sort_order
                  FROM rule_type_catalog base_catalog
                 WHERE base_catalog.federation_code = 'FRMF'
                   AND base_catalog.rule_type IN (%s)
                   AND NOT EXISTS (
                       SELECT 1 FROM rule_type_catalog existing
                        WHERE existing.federation_code = :targetFederation
                          AND existing.rule_type = base_catalog.rule_type
                   )""".formatted(DEFAULT_RULE_TYPES))
                .param("targetFederation", federationCode)
                .update();
        if (seededTypes != DEFAULT_RULE_TYPE_COUNT) {
            throw new IllegalStateException("Expected to seed " + DEFAULT_RULE_TYPE_COUNT
                    + " default P1/P2 rule types for federation " + federationCode + " but seeded " + seededTypes);
        }
        jdbc.sql("""
                INSERT INTO rule_type_allowed_scope (federation_code, rule_type, scope_type)
                SELECT :targetFederation, base_scope.rule_type, base_scope.scope_type
                  FROM rule_type_allowed_scope base_scope
                 WHERE base_scope.federation_code = 'FRMF'
                   AND base_scope.rule_type IN (%s)
                   AND NOT EXISTS (
                       SELECT 1 FROM rule_type_allowed_scope existing
                        WHERE existing.federation_code = :targetFederation
                          AND existing.rule_type = base_scope.rule_type
                          AND existing.scope_type = base_scope.scope_type
                   )""".formatted(DEFAULT_RULE_TYPES))
                .param("targetFederation", federationCode)
                .update();
    }

    private List<SetupDtos.RuleParam> params(String schema) {
        if (schema == null || schema.isBlank()) {
            return List.of();
        }
        try {
            return Arrays.asList(json.readValue(schema, SetupDtos.RuleParam[].class));
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String scope(String phaseCode) {
        return "P2".equalsIgnoreCase(phaseCode) ? "PLANNING" : "DRAW";
    }

    private Optional<SetupDtos.Federation> federation(String code) {
        return jdbc.sql("SELECT code, name, country_code, time_zone, status, created_at, updated_at FROM federation WHERE code = :code")
                .param("code", code)
                .query((rs, i) -> new SetupDtos.Federation(rs.getString("code"), rs.getString("code"), rs.getString("name"),
                        rs.getString("country_code"), rs.getString("time_zone"), rs.getString("status"),
                        ts(rs.getObject("created_at")), ts(rs.getObject("updated_at"))))
                .optional();
    }

    private SetupDtos.CompetitionSummary summary(String id, String code, String name, String federationCode, int seasons,
            int participants, int effectiveRules) {
        return new SetupDtos.CompetitionSummary(id, code, name, modality(id, code), level(id, code), federationCode, "ACTIVE", seasons,
                participants, effectiveRules);
    }

    private String modality(String id, String code) {
        return (id != null && id.startsWith("CNPFF")) || (code != null && code.startsWith("F")) ? "WOMEN" : "MEN";
    }

    private int level(String id, String code) {
        String text = (id == null ? "" : id) + " " + (code == null ? "" : code);
        return text.contains("D2") || text.contains("F2") ? 2 : 1;
    }

    private Instant ts(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toInstant();
        }
        if (value instanceof java.time.OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        return null;
    }

    private String icon(String ruleType) {
        return ICONS.getOrDefault(ruleType, "shield");
    }

    private static final Map<String, String> ICONS = new LinkedHashMap<>();
    static {
        ICONS.put("DerbyTeams", "derby");
        ICONS.put("MatchNotInRound", "ban");
        ICONS.put("SeparateMatches", "split");
        ICONS.put("LimitHomeTogether", "home");
        ICONS.put("PlayTogether", "link");
        ICONS.put("NotConsecutiveFarAway", "move");
        ICONS.put("LimitAgainstTeamsInRounds", "filter");
        ICONS.put("LimitAgainstTeamsInRoundsHome", "filter");
        ICONS.put("ForcePositions", "pin");
        ICONS.put("HalfMatchesAtHome", "home");
        ICONS.put("VenueUnavailable", "stadium");
        ICONS.put("FixedHomeDate", "pin");
        ICONS.put("LEGACY_CHECK", "file");
    }
}