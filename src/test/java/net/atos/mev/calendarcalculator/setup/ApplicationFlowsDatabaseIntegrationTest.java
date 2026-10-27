package net.atos.mev.calendarcalculator.setup;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import net.atos.mev.calendarcalculator.setup.rules.RuleScopeDtos;
import net.atos.mev.calendarcalculator.setup.draw.DrawDtos;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Real API, persistence and legacy engine; each case owns a six-team, ten-round test season. */
@SpringBootTest
@AutoConfigureMockMvc
class ApplicationFlowsDatabaseIntegrationTest {

    private static final String SEASON = "2026-27";
    private static final LocalDate FIRST_SATURDAY = LocalDate.of(2026, 10, 17);
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired JdbcClient jdbc;
    @Autowired MatchStateService matchStates;
    @Autowired SeasonSetupRepository setups;
    @Autowired GenerationRepository generations;
    private String federation;
    private String competition;
    private String code;
    private String url;
    private List<SetupDtos.Team> teams;
    private final List<String> extraTeamCodes = new ArrayList<>();
    private final List<String> extraCompetitions = new ArrayList<>();

    @BeforeEach
    void createIsolatedSeason() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 7).toUpperCase(java.util.Locale.ROOT);
        federation = "IT" + suffix;
        competition = "IT_" + suffix;
        code = "I" + suffix;
        url = "/api/setup/competitions/" + competition + "/seasons/" + SEASON;
        teams = IntStream.range(0, 6).mapToObj(i -> new SetupDtos.Team("Q" + suffix.substring(0, 5) + i,
                "Test club " + i, null, false, false)).toList();
        mvc.perform(post("/api/federations/" + federation).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new SetupDtos.FederationUpsertRequest("Test federation", "MA", "Africa/Casablanca"))))
                .andExpect(status().isOk());
        mvc.perform(post("/api/federations/" + federation + "/competitions").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CompetitionAdminController.CreateCompetition(competition, code, "Test league"))))
                .andExpect(status().isCreated());
        mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                new MasterDataController.CreateSeason(LocalDate.of(2026, 9, 1), LocalDate.of(2027, 6, 30),
                        FIRST_SATURDAY, FIRST_SATURDAY.plusWeeks(5)))))
                .andExpect(status().isCreated());
        SetupDtos.General general = new SetupDtos.General(LocalDate.of(2026, 9, 1), LocalDate.of(2027, 6, 30),
                FIRST_SATURDAY, FIRST_SATURDAY.plusWeeks(5), 3, 0, 0, false, 1, 1, 10, "sat-sun");
        List<SetupDtos.Round> rounds = setupRounds(10);
        SetupDtos.SeasonSetup setup = new SetupDtos.SeasonSetup(competition, code, SEASON, general,
                List.of("15:00", "17:00", "19:00"), null, "15:00", teams, rounds, fixtures(), List.of(), List.of(),
                Map.of(code, 3, "CL", 3), List.of(), List.of(), List.of(), null, false, null, 0, null);
        mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(setup)))
                .andExpect(status().isOk());
    }

    @AfterEach
    void cleanIsolatedSeason() {
        if (federation != null) {
            jdbc.sql("DELETE FROM rule_scope_assignment WHERE federation_code = :f").param("f", federation).update();
        }
        for (String extra : extraCompetitions) {
            jdbc.sql("DELETE FROM competition_season WHERE competition_id = :c").param("c", extra).update();
            jdbc.sql("DELETE FROM competition WHERE id = :c").param("c", extra).update();
        }
        if (competition != null) {
            jdbc.sql("DELETE FROM competition_season WHERE competition_id = :c").param("c", competition).update();
            jdbc.sql("DELETE FROM competition WHERE id = :c").param("c", competition).update();
        }
        if (teams != null) {
            for (SetupDtos.Team team : teams) {
                jdbc.sql("DELETE FROM team WHERE code = :c").param("c", team.code()).update();
            }
        }
        for (String extra : extraTeamCodes) {
            jdbc.sql("DELETE FROM team WHERE code = :c").param("c", extra).update();
        }
        if (federation != null) {
            jdbc.sql("DELETE FROM rule_type_allowed_scope WHERE federation_code = :f").param("f", federation).update();
            jdbc.sql("DELETE FROM rule_type_catalog WHERE federation_code = :f").param("f", federation).update();
            jdbc.sql("DELETE FROM federation WHERE code = :f").param("f", federation).update();
        }
    }

    @Test
    void federationCanBeEditedDeactivatedAndReactivatedWithoutLosingItsLeague() throws Exception {
        mvc.perform(put("/api/federations/" + federation).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renamed federation\",\"countryCode\":\"MA\",\"timeZone\":\"Africa/Casablanca\"}"))
                .andExpect(status().isOk());
        assertEquals("Renamed federation", federations(false).stream().filter(f -> federation.equals(f.code()))
                .findFirst().orElseThrow().name());
        federationStatus("INACTIVE");
        assertTrue(federations(false).stream().noneMatch(f -> federation.equals(f.code())));
        assertEquals("INACTIVE", federations(true).stream().filter(f -> federation.equals(f.code())).findFirst().orElseThrow().status());
        assertEquals(competition, competitions().getFirst().id());
        federationStatus("ACTIVE");
        assertTrue(federations(false).stream().anyMatch(f -> federation.equals(f.code())));
        assertEquals(6, setup().teams().size());
    }

    @Test
    void invalidFederationStatusIsRejectedWithoutChangingTheFederation() throws Exception {
        mvc.perform(patch("/api/federations/" + federation + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DELETED\"}")).andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("status must be ACTIVE or INACTIVE"));
        mvc.perform(patch("/api/federations/" + federation + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":null}")).andExpect(status().isBadRequest());
        assertEquals("ACTIVE", federations(true).stream().filter(f -> federation.equals(f.code())).findFirst().orElseThrow().status());
    }

    @Test
    void leagueCanBeCreatedReadRenamedAndRejectsStaleEditsAndDuplicateIds() throws Exception {
        assertEquals(federation, competitions().getFirst().federationCode());
        mvc.perform(put("/api/competitions/" + competition).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renamed league\",\"originalName\":\"Test league\"}"))
                .andExpect(status().isOk());
        mvc.perform(put("/api/competitions/" + competition).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Stale edit\",\"originalName\":\"Test league\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/federations/" + federation + "/competitions").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CompetitionAdminController.CreateCompetition(competition, code, "Duplicate"))))
                .andExpect(status().isConflict());
        assertEquals("Renamed league", competitions().getFirst().name());
        assertEquals(30, setup().fixtures().size());
    }

    @Test
    void leagueCreationRejectsUnknownFederationsAndInvalidCodes() throws Exception {
        String body = json.writeValueAsString(new CompetitionAdminController.CreateCompetition(competition + "_OTHER", code + "X", "Other"));
        mvc.perform(post("/api/federations/NO_SUCH_FED/competitions").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/federations/" + federation + "/competitions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"lower-case\",\"code\":\"bad\",\"name\":\"Invalid\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(1, competitions().size());
    }

    @Test
    void leagueDeletionExplainsItsSeasonReferencesAndDoesNotDeleteAnything() throws Exception {
        String problem = mvc.perform(delete("/api/competitions/" + competition)).andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertTrue(problem.contains("seasons"));
        assertTrue(problem.contains("blockers"));
        assertTrue(problem.contains("No data was deleted"));
        assertEquals(competition, competitions().getFirst().id());
    }

    @Test
    void emptySeasonLeagueAndFederationCanBeDeletedWithoutDeletingSharedTeams() throws Exception {
        saveChange(tree -> {
            for (String field : List.of("teams", "rounds", "fixtures", "timeslots", "external", "blackouts",
                    "notHomeSameDay", "derbies", "rules")) {
                tree.set(field, json.createArrayNode());
            }
            tree.set("restDays", json.createObjectNode());
            tree.putNull("engine");
        });
        mvc.perform(delete(url)).andExpect(status().isNoContent());
        mvc.perform(get(url)).andExpect(status().isNotFound());
        mvc.perform(get("/api/competitions/" + competition + "/seasons")).andExpect(status().isOk())
                .andExpect(content().json("[]"));
        mvc.perform(delete("/api/competitions/" + competition)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/federations/" + federation)).andExpect(status().isNoContent());
        mvc.perform(get("/api/federations/" + federation)).andExpect(status().isNotFound());
        assertEquals(0, jdbc.sql("SELECT COUNT(*) FROM rule_type_catalog WHERE federation_code = :f")
                .param("f", federation).query(Integer.class).single());
        for (SetupDtos.Team team : teams) {
            mvc.perform(get("/api/teams/" + team.code())).andExpect(status().isOk());
        }
    }

    @Test
    void federationDeletionExplainsReferencesAndPreservesItsCatalog() throws Exception {
        String problem = mvc.perform(delete("/api/federations/" + federation)).andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertTrue(problem.contains("1 competitions"), problem);
        assertTrue(problem.contains("deactivate"), problem);
        assertTrue(problem.contains("No data was deleted"), problem);
        mvc.perform(get("/api/federations/" + federation)).andExpect(status().isOk());
        assertEquals(12, jdbc.sql("SELECT COUNT(*) FROM rule_type_catalog WHERE federation_code = :f")
                .param("f", federation).query(Integer.class).single());
        assertEquals(6, setup().teams().size());
    }

    @Test
    void federationCreateAndUpdateRejectInvalidFieldsAndDuplicateIdentifiers() throws Exception {
        String endpoint = "/api/federations/" + federation;
        for (String invalid : List.of(
                "{\"name\":\"\",\"countryCode\":\"MA\",\"timeZone\":\"Africa/Casablanca\"}",
                "{\"name\":\"Valid\",\"countryCode\":\"Morocco\",\"timeZone\":\"Africa/Casablanca\"}",
                "{\"name\":\"Valid\",\"countryCode\":\"MA\",\"timeZone\":\"Unknown/Nowhere\"}")) {
            mvc.perform(put(endpoint).contentType(MediaType.APPLICATION_JSON).content(invalid))
                    .andExpect(status().isBadRequest());
        }
        String valid = "{\"name\":\"Valid\",\"countryCode\":\"MA\",\"timeZone\":\"Africa/Casablanca\"}";
        mvc.perform(post("/api/federations/bad-code").contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isBadRequest());
        mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/federations/NO_SUCH_FED").contentType(MediaType.APPLICATION_JSON).content(valid))
                .andExpect(status().isNotFound());
        assertEquals("Test federation", federations(true).stream().filter(f -> federation.equals(f.code())).findFirst().orElseThrow().name());
    }

    @Test
    void standaloneTeamSupportsCreateReadConcurrentSafeUpdateAndDelete() throws Exception {
        String teamCode = code;
        extraTeamCodes.add(teamCode);
        String endpoint = "/api/teams/" + teamCode;
        mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Independent club\",\"city\":null}")).andExpect(status().isCreated());
        MasterDataController.MasterTeam created = json.readValue(mvc.perform(get(endpoint)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), MasterDataController.MasterTeam.class);
        assertEquals(0, created.registrations());
        assertNull(created.city());
        String update = "{\"name\":\"Renamed club\",\"city\":\"Rabat\",\"originalName\":\"Independent club\",\"originalCity\":null}";
        mvc.perform(put(endpoint).contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isOk());
        mvc.perform(put(endpoint).contentType(MediaType.APPLICATION_JSON).content(update)).andExpect(status().isConflict());
        mvc.perform(put(endpoint).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Wrong city edit\",\"city\":\"Fez\",\"originalName\":\"Renamed club\",\"originalCity\":null}"))
                .andExpect(status().isConflict());
        MasterDataController.MasterTeam updated = json.readValue(mvc.perform(get(endpoint)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), MasterDataController.MasterTeam.class);
        assertEquals("Renamed club", updated.name());
        assertEquals("Rabat", updated.city());
        mvc.perform(delete(endpoint)).andExpect(status().isNoContent());
        mvc.perform(get(endpoint)).andExpect(status().isNotFound());
        mvc.perform(delete(endpoint)).andExpect(status().isNotFound());
    }

    @Test
    void teamDeletionReportsRegistrationsAndFixturesWithoutDeletingThem() throws Exception {
        String endpoint = "/api/teams/" + teams.getFirst().code();
        String problem = mvc.perform(delete(endpoint)).andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertTrue(problem.contains("1 season registrations"), problem);
        assertTrue(problem.contains("10 fixture match references"), problem);
        assertTrue(problem.contains("blockers"), problem);
        assertTrue(problem.contains("No data was deleted"), problem);
        mvc.perform(get(endpoint)).andExpect(status().isOk());
        assertEquals(teams, setup().teams());
        assertEquals(30, setup().fixtures().size());
    }

    @Test
    void teamCreationRejectsInvalidFieldsAndDuplicatesWithoutChangingMasterData() throws Exception {
        String endpoint = "/api/teams/" + teams.getFirst().code();
        mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Duplicate\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/teams/too-long-code").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Valid\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(endpoint).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"originalName\":\"Test club 0\"}")).andExpect(status().isBadRequest());
        mvc.perform(put(endpoint).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Missing concurrency check\"}"))
                .andExpect(status().isBadRequest());
        assertEquals("Test club 0", setup().teams().getFirst().name());
    }

    @Test
    void seasonDeletionListsParticipantsConfigurationAndMatchesWithoutDeletingAny() throws Exception {
        SetupDtos.SeasonSetup before = setup();
        String problem = mvc.perform(delete(url)).andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertTrue(problem.contains("6 participants"), problem);
        assertTrue(problem.contains("30 fixtures"), problem);
        assertTrue(problem.contains("No data was deleted"), problem);
        assertEquals(before, setup());
    }

    @Test
    void seasonCreationListsEmptyConfigurationAndRejectsDuplicatesInvalidYearsAndDates() throws Exception {
        String second = "/api/setup/competitions/" + competition + "/seasons/2027-28";
        MasterDataController.CreateSeason dates = new MasterDataController.CreateSeason(
                LocalDate.of(2027, 9, 1), LocalDate.of(2028, 6, 30), null, LocalDate.of(2028, 1, 1));
        String body = json.writeValueAsString(dates);
        SetupDtos.SeasonSetup created = json.readValue(mvc.perform(post(second).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), SetupDtos.SeasonSetup.class);
        assertTrue(created.teams().isEmpty());
        assertTrue(created.fixtures().isEmpty());
        assertTrue(created.rounds().isEmpty());
        assertNull(created.general().firstRoundDate());
        List<SetupDtos.CompetitionSeasonRef> seasons = json.readValue(mvc.perform(get("/api/competitions/" + competition + "/seasons"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), new TypeReference<>() { });
        assertEquals(List.of("2026-27", "2027-28"), seasons.stream().map(SetupDtos.CompetitionSeasonRef::season).sorted().toList());
        mvc.perform(post(second).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        mvc.perform(post(second.replace("2027-28", "2027-29")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        mvc.perform(post(second.replace("2027-28", "2028-29")).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new MasterDataController.CreateSeason(dates.seasonEnd(), dates.seasonStart(), null, dates.startSecondLeg()))))
                .andExpect(status().isBadRequest());
        mvc.perform(delete(second)).andExpect(status().isNoContent());
        mvc.perform(get(second)).andExpect(status().isNotFound());
        assertEquals(30, setup().fixtures().size());
    }

    @Test
    void deletesOfUnknownMasterEntitiesAndSeasonsReturnNotFound() throws Exception {
        for (String endpoint : List.of("/api/teams/ZZNONE", "/api/federations/NO_SUCH_FED",
                "/api/competitions/NO_SUCH_COMP", url.replace(SEASON, "2090-91"))) {
            mvc.perform(delete(endpoint)).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/competitions/NO_SUCH_COMP/seasons")).andExpect(status().isNotFound());
    }

    @Test
    void generatedVersionSnapshotCanBeRestoredWithoutLosingItsMatchesOrFiles() throws Exception {
        SetupDtos.SeasonSetup initial = setup();
        SetupDtos.PlanningJob version = plan(5);
        List<SetupDtos.GeneratedMatch> matches = ownMatches(version);
        saveChange(tree -> tree.set("timeslots", json.valueToTree(List.of("20:00"))));
        int changedVersion = setup().rowVersion();
        mvc.perform(post(url + "/versions/" + version.versionNo() + "/restore")).andExpect(status().isConflict());
        assertEquals(changedVersion, setup().rowVersion());
        // Las jornadas PLANIFICADAS están protegidas; se simula una anulación auditada previa.
        long cs = setups.competitionSeasonId(competition, SEASON);
        jdbc.sql("UPDATE season_match_state SET status = 'PENDIENTE', play_date = NULL, play_hour = NULL WHERE competition_season_id = :cs")
                .param("cs", cs).update();
        SetupDtos.SeasonSetup restored = json.readValue(mvc.perform(post(url + "/versions/" + version.versionNo() + "/restore"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), SetupDtos.SeasonSetup.class);
        assertEquals(initial.timeslots(), restored.timeslots());
        assertEquals(initial.teams(), restored.teams());
        assertEquals(changedVersion + 1, restored.rowVersion());
        assertEquals(matches.stream().map(m -> m.round() + m.date() + m.hour() + m.home() + m.away()).toList(),
                ownMatches(version).stream().map(m -> m.round() + m.date() + m.hour() + m.home() + m.away()).toList());
        mvc.perform(get(url + "/versions/" + version.versionNo() + "/files/fullCalendar.xlsx")).andExpect(status().isOk());
        String problem = mvc.perform(delete(url)).andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertTrue(problem.contains("generated versions"), problem);
        assertTrue(problem.contains("planning jobs"), problem);
    }

    @Test
    void restrictionCrudProtectsInheritedExceptionsAndRejectsStaleEdits() throws Exception {
        ObjectNode body = json.createObjectNode();
        body.put("scopeType", "FEDERATION_DEFAULT");
        body.put("ruleType", "DerbyTeams");
        body.put("a", teams.get(0).code());
        body.put("b", teams.get(1).code());
        String collection = "/api/setup/federations/" + federation + "/rule-assignments";
        RuleScopeDtos.Assignment parent = json.readValue(mvc.perform(post(collection).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                RuleScopeDtos.Assignment.class);
        body.put("scopeType", "COMPETITION_SEASON");
        body.put("competitionId", competition);
        body.put("season", SEASON);
        body.put("exceptionOf", parent.id());
        RuleScopeDtos.Assignment exception = json.readValue(mvc.perform(post(collection).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(),
                RuleScopeDtos.Assignment.class);
        String parentUrl = "/api/setup/rule-assignments/" + parent.id();
        String problem = mvc.perform(delete(parentUrl)).andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertTrue(problem.contains("1 rule exceptions"), problem);
        assertTrue(problem.contains("No data was deleted"), problem);
        List<RuleScopeDtos.Assignment> assignments = json.readValue(mvc.perform(get(collection)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), new TypeReference<>() { });
        assertEquals(2, assignments.size());
        body.put("scopeType", "FEDERATION_DEFAULT");
        body.remove("exceptionOf");
        body.put("rowVersion", parent.rowVersion());
        body.put("note", "Updated without changing its exception");
        mvc.perform(put(parentUrl).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isOk());
        mvc.perform(put(parentUrl).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/setup/rule-assignments/" + exception.id())).andExpect(status().isOk());
        mvc.perform(delete(parentUrl)).andExpect(status().isOk());
        mvc.perform(delete(parentUrl)).andExpect(status().isNotFound());
    }

    @Test
    void teamReferencedOnlyByAFederationRuleCannotBeDeletedUntilTheRuleIsRemoved() throws Exception {
        String teamCode = code;
        extraTeamCodes.add(teamCode);
        String endpoint = "/api/teams/" + teamCode;
        mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Rule-only club\"}"))
                .andExpect(status().isCreated());
        ObjectNode body = json.createObjectNode();
        body.put("scopeType", "FEDERATION_DEFAULT");
        body.put("ruleType", "NotConsecutiveFarAway");
        body.put("teams", teamCode + "," + teams.getFirst().code());
        RuleScopeDtos.Assignment rule = json.readValue(mvc.perform(post("/api/setup/federations/" + federation + "/rule-assignments")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), RuleScopeDtos.Assignment.class);
        String problem = mvc.perform(delete(endpoint)).andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertTrue(problem.contains("1 assigned restriction references"), problem);
        mvc.perform(delete("/api/setup/rule-assignments/" + rule.id())).andExpect(status().isOk());
        mvc.perform(delete(endpoint)).andExpect(status().isNoContent());
    }

    @Test
    void queuedPlanningJobCanBeCancelledWithoutProducingAVersionOrChangingConfiguration() throws Exception {
        UUID jobId = UUID.randomUUID();
        SetupDtos.SeasonSetup before = setup();
        jdbc.sql("""
                INSERT INTO planning_job (id, competition_season_id, status, last_round_to_assign)
                VALUES (:id, (SELECT id FROM competition_season WHERE competition_id = :c AND season_id = :s), 'QUEUED', 5)""")
                .param("id", jobId).param("c", competition).param("s", SEASON).update();
        SetupDtos.PlanningJob cancelled = json.readValue(mvc.perform(post("/api/setup/planning-jobs/" + jobId + "/cancel"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), SetupDtos.PlanningJob.class);
        assertEquals("CANCELLED", cancelled.status());
        assertNotNull(cancelled.finishedAt());
        assertNull(cancelled.versionNo());
        assertTrue(jdbc.sql("SELECT cancel_requested FROM planning_job WHERE id = :id")
                .param("id", jobId).query(Boolean.class).single());
        assertEquals(before, setup());
        mvc.perform(get(url + "/versions")).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(post("/api/setup/planning-jobs/" + UUID.randomUUID() + "/cancel")).andExpect(status().isNotFound());
    }

    @Test
    void legacyRuleDefinitionsProtectTeamsEvenWhenTheirStructuredColumnsAreEmpty() throws Exception {
        String teamCode = code;
        extraTeamCodes.add(teamCode);
        String endpoint = "/api/teams/" + teamCode;
        mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Legacy rule club\"}"))
                .andExpect(status().isCreated());
        SetupDtos.Rule legacy = new SetupDtos.Rule(null, "LEGACY_CHECK", null, null, null, null, null, null, null,
                null, null, null, "NotConsecutiveFarAwayCheck," + teamCode + "," + teams.getFirst().code(), null);
        saveChange(tree -> tree.set("rules", json.valueToTree(List.of(legacy))));
        String problem = mvc.perform(delete(endpoint)).andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertTrue(problem.contains("1 season restriction references"), problem);
        saveChange(tree -> tree.set("rules", json.createArrayNode()));
        mvc.perform(delete(endpoint)).andExpect(status().isNoContent());
    }

    @Test
    void federationP1PairCoordinatesDifferentLeaguesAndPersistsEveryCandidate() throws Exception {
        SetupDtos.SeasonSetup second = createOtherLeague("B");
        RuleScopeDtos.Assignment rule = createHomePair(teams.getFirst().code(), second.teams().getFirst().code());
        List<SetupDtos.ValidationIssue> issues = json.readValue(mvc.perform(post(url + "/draw/validate"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), new TypeReference<>() { });
        assertTrue(issues.stream().noneMatch(i -> "error".equals(i.level())), issues.toString());
        String problem = mvc.perform(post(url + "/draw-batches").contentType(MediaType.APPLICATION_JSON)
                .content("{\"candidates\":10,\"includeDependents\":false}")).andExpect(status().isUnprocessableEntity())
                .andReturn().getResponse().getContentAsString();
        assertTrue(problem.contains("Include the related competitions"), problem);
        var effective = json.readValue(mvc.perform(get(url + "/effective-rules")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), RuleScopeDtos.EffectiveRules.class);
        assertTrue(effective.rules().stream().anyMatch(r -> rule.id().equals(r.assignmentId()) && r.applied()));
        String missingCount = mvc.perform(post(url + "/draw-batches").contentType(MediaType.APPLICATION_JSON)
                .content("{\"includeDependents\":false}")).andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertTrue(missingCount.contains("candidates is required"), missingCount);
        DrawDtos.DrawBatch batch = json.readValue(mvc.perform(post(url + "/draw-batches").contentType(MediaType.APPLICATION_JSON)
                .content("{\"candidates\":1000}")).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString(),
                DrawDtos.DrawBatch.class);
        long deadline = System.nanoTime() + Duration.ofSeconds(90).toNanos();
        while ("RUNNING".equals(batch.status()) && System.nanoTime() < deadline) {
            Thread.sleep(100);
            batch = json.readValue(mvc.perform(get("/api/setup/draw-batches/" + batch.id())).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(), DrawDtos.DrawBatch.class);
        }
        assertEquals("DONE", batch.status(), batch.message());
        assertEquals(1000, batch.generated(), batch.message());
        assertEquals(2, batch.leagues().size(), "No P2 dependency is necessary to coordinate a federation pair");
        for (int candidate = 1; candidate <= 1000; candidate++) {
            DrawDtos.Candidate result = json.readValue(mvc.perform(get("/api/setup/draw-batches/" + batch.id() + "/candidates/" + candidate))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), DrawDtos.Candidate.class);
            assertEquals(2, result.leagues().size());
            for (int round = 1; round <= 10; round++) {
                int r = round;
                long home = result.leagues().stream().flatMap(l -> l.matches().stream())
                        .filter(m -> m.round() == r && (m.home().equals(rule.a()) || m.home().equals(rule.b()))).count();
                assertEquals(1, home, "Pair must have opposite home/away orientation in both legs");
            }
        }
        mvc.perform(delete("/api/setup/draw-batches/" + batch.id())).andExpect(status().isNoContent());
        assertNull(setup().dependsOn(), "P1 coordination must not change P2 dependencies");
        assertEquals(30, setup().fixtures().size(), "Generating alternatives does not choose one");
    }

    @Test
    void federationPairExceptionDisabledRuleAndDisabledTypeStopCoordinatingTheOtherLeague() throws Exception {
        SetupDtos.SeasonSetup second = createOtherLeague("B");
        RuleScopeDtos.Assignment parent = createHomePair(teams.getFirst().code(), second.teams().getFirst().code());
        ObjectNode body = json.createObjectNode();
        body.put("scopeType", "COMPETITION_SEASON");
        body.put("competitionId", second.competitionId());
        body.put("season", SEASON);
        body.put("exceptionOf", parent.id());
        RuleScopeDtos.Assignment exception = json.readValue(mvc.perform(post("/api/setup/federations/" + federation + "/rule-assignments")
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), RuleScopeDtos.Assignment.class);
        assertSingleLeagueDrawIsValid();
        mvc.perform(delete("/api/setup/rule-assignments/" + exception.id())).andExpect(status().isOk());
        body = json.valueToTree(parent);
        body.put("enabled", false);
        mvc.perform(put("/api/setup/rule-assignments/" + parent.id()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().isOk());
        assertSingleLeagueDrawIsValid();
        body.put("enabled", true);
        body.put("rowVersion", parent.rowVersion() + 1);
        mvc.perform(put("/api/setup/rule-assignments/" + parent.id()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().isOk());
        mvc.perform(put("/api/setup/federations/" + federation + "/rule-types/DerbyTeams")
                .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":false}")).andExpect(status().isOk());
        assertSingleLeagueDrawIsValid();
    }

    @Test
    void impossibleP1TriangleReportsTheConflictInsteadOfIgnoringPairsOrCreatingABatch() throws Exception {
        createHomePair(teams.get(0).code(), teams.get(1).code());
        createHomePair(teams.get(0).code(), teams.get(2).code());
        createHomePair(teams.get(1).code(), teams.get(2).code());
        List<SetupDtos.ValidationIssue> issues = json.readValue(mvc.perform(post(url + "/draw/validate"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), new TypeReference<>() { });
        assertTrue(issues.stream().anyMatch(i -> "error".equals(i.level()) && i.msg().contains("incompatible")), issues.toString());
        mvc.perform(post(url + "/draw-batches").contentType(MediaType.APPLICATION_JSON).content("{\"candidates\":1}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get(url + "/draw-batches")).andExpect(status().isOk()).andExpect(content().json("[]"));
        assertEquals(30, setup().fixtures().size());
    }

    @Test
    void federationPairRejectsAnotherFederationsTeamAndReportsAnUnregisteredCounterpart() throws Exception {
        mvc.perform(post("/api/setup/federations/" + federation + "/rule-assignments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"scopeType\":\"FEDERATION_DEFAULT\",\"ruleType\":\"DerbyTeams\",\"a\":\""
                        + teams.getFirst().code() + "\",\"b\":\"WAC\"}")).andExpect(status().isBadRequest());
        String standalone = code;
        extraTeamCodes.add(standalone);
        mvc.perform(post("/api/teams/" + standalone).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Pending registration\"}"))
                .andExpect(status().isCreated());
        createHomePair(teams.getFirst().code(), standalone);
        List<SetupDtos.ValidationIssue> issues = json.readValue(mvc.perform(post(url + "/draw/validate"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), new TypeReference<>() { });
        assertTrue(issues.stream().anyMatch(i -> "error".equals(i.level()) && i.msg().contains("register " + standalone)), issues.toString());
        mvc.perform(post(url + "/draw-batches").contentType(MediaType.APPLICATION_JSON).content("{\"candidates\":1}"))
                .andExpect(status().isUnprocessableEntity());
    }

    private void assertSingleLeagueDrawIsValid() throws Exception {
        List<SetupDtos.ValidationIssue> issues = json.readValue(mvc.perform(post(url + "/draw/validate").param("includeDependents", "false"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), new TypeReference<>() { });
        assertTrue(issues.stream().noneMatch(i -> "error".equals(i.level())), issues.toString());
    }

    private RuleScopeDtos.Assignment createHomePair(String first, String second) throws Exception {
        return json.readValue(mvc.perform(post("/api/setup/federations/" + federation + "/rule-assignments")
                .contentType(MediaType.APPLICATION_JSON).content("{\"scopeType\":\"FEDERATION_DEFAULT\",\"ruleType\":\"DerbyTeams\",\"a\":\""
                        + first + "\",\"b\":\"" + second + "\"}")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), RuleScopeDtos.Assignment.class);
    }

    private SetupDtos.SeasonSetup createOtherLeague(String suffix) throws Exception {
        String id = competition + "_" + suffix;
        String leagueCode = code + suffix;
        String endpoint = "/api/setup/competitions/" + id + "/seasons/" + SEASON;
        extraCompetitions.add(id);
        mvc.perform(post("/api/federations/" + federation + "/competitions").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new CompetitionAdminController.CreateCompetition(id, leagueCode, "Other league"))))
                .andExpect(status().isCreated());
        mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(
                new MasterDataController.CreateSeason(LocalDate.of(2026, 9, 1), LocalDate.of(2027, 6, 30),
                        FIRST_SATURDAY, FIRST_SATURDAY.plusWeeks(5))))).andExpect(status().isCreated());
        SetupDtos.SeasonSetup current = setup();
        List<SetupDtos.Team> others = IntStream.range(0, 6).mapToObj(i -> new SetupDtos.Team(
                suffix + teams.get(i).code(), "Other club " + i, null, false, false)).toList();
        others.forEach(t -> extraTeamCodes.add(t.code()));
        List<SetupDtos.Fixture> fixtures = current.fixtures().stream().map(f -> new SetupDtos.Fixture(null, f.round(),
                suffix + f.home(), suffix + f.away(), false, null, null)).toList();
        SetupDtos.SeasonSetup other = new SetupDtos.SeasonSetup(id, leagueCode, SEASON, current.general(), current.timeslots(),
                null, current.noLightSlot(), others, current.rounds(), fixtures, List.of(), List.of(), Map.of(), List.of(), List.of(),
                List.of(), null, false, null, 0, null);
        return json.readValue(mvc.perform(put(endpoint).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(other)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), SetupDtos.SeasonSetup.class);
    }

    @Test
    void participantCanBeRemovedAddedAndEditedWithoutDeletingTheSharedTeam() throws Exception {
        SetupDtos.Team original = teams.getFirst();
        saveChange(tree -> tree.set("teams", json.valueToTree(teams.subList(1, teams.size()))));
        assertTrue(setup().teams().stream().noneMatch(t -> original.code().equals(t.code())));
        assertEquals(1, jdbc.sql("SELECT COUNT(*) FROM team WHERE code = :c").param("c", original.code()).query(Integer.class).single());
        List<SetupDtos.Team> edited = new ArrayList<>(teams);
        edited.set(0, new SetupDtos.Team(original.code(), "Edited participant", "Rabat", true, true));
        saveChange(tree -> tree.set("teams", json.valueToTree(edited)));
        assertEquals(edited.getFirst(), setup().teams().getFirst());
        assertEquals(30, setup().fixtures().size());
        assertTrue(validation(5).stream().noneMatch(i -> "teams".equals(i.section()) && "error".equals(i.level())));
    }

    @Test
    void newParticipantCreatesATeamAndCanBeRemovedFromTheSeason() throws Exception {
        String newCode = teams.getFirst().code() + "N";
        extraTeamCodes.add(newCode);
        SetupDtos.Team added = new SetupDtos.Team(newCode, "New club", "Rabat", false, false);
        List<SetupDtos.Team> expanded = new ArrayList<>(teams);
        expanded.add(added);
        saveChange(tree -> tree.set("teams", json.valueToTree(expanded)));
        assertEquals(added, setup().teams().getLast());
        assertEquals("New club", jdbc.sql("SELECT name FROM team WHERE code = :c").param("c", newCode).query(String.class).single());
        saveChange(tree -> tree.set("teams", json.valueToTree(teams)));
        assertEquals(teams, setup().teams());
        assertEquals(1, jdbc.sql("SELECT COUNT(*) FROM team WHERE code = :c").param("c", newCode).query(Integer.class).single());
    }

    @Test
    void removingATeamFromAnExistingDrawBlocksPlanningUntilTheDrawIsRepaired() throws Exception {
        saveChange(tree -> tree.set("teams", json.valueToTree(teams.subList(1, teams.size()))));
        assertTrue(validation(5).stream().anyMatch(i -> "teams".equals(i.section()) && "error".equals(i.level())));
        mvc.perform(post(url + "/planning-jobs").contentType(MediaType.APPLICATION_JSON).content("{\"lastRoundToAssign\":5}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void staleSeasonSaveIsRejectedAndDoesNotOverwriteTheNewerParticipants() throws Exception {
        SetupDtos.SeasonSetup stale = setup();
        saveChange(tree -> tree.set("timeslots", json.valueToTree(List.of("20:00"))));
        mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(stale)))
                .andExpect(status().isConflict());
        assertEquals(List.of("20:00"), setup().timeslots());
        assertEquals(stale.rowVersion() + 1, setup().rowVersion());
        assertEquals(teams, setup().teams());
    }

    @Test
    void planningFiveThenTenRoundsPreservesExplicitlyConfirmedDatesAndHours() throws Exception {
        SetupDtos.PlanningJob first = plan(5);
        List<SetupDtos.GeneratedMatch> five = ownMatches(first);
        assertSchedule(five, 5);
        for (SetupDtos.GeneratedMatch match : five) {
            setups.lockFixture(competition, SEASON,
                    new SetupDtos.FixtureLockRequest(match.home(), match.away(), match.date(), match.hour(), true));
        }
        assertEquals(15, setup().fixtures().stream().filter(SetupDtos.Fixture::locked).count());
        approve(first);
        saveChange(tree -> tree.set("timeslots", json.valueToTree(List.of("20:00"))));
        SetupDtos.PlanningJob second = plan(10);
        List<SetupDtos.GeneratedMatch> ten = ownMatches(second);
        assertSchedule(ten, 10);
        assertEquals(byFixture(five), byFixture(ten.stream().filter(m -> Integer.parseInt(m.round()) <= 5).toList()));
        assertTrue(ten.stream().filter(m -> Integer.parseInt(m.round()) > 5).allMatch(m -> "20:00".equals(m.hour())));
        assertEquals(byFixture(five), byFixture(ownMatches(first)));
        approve(second);
        List<SetupDtos.Version> versions = json.readValue(mvc.perform(get(url + "/versions")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), new TypeReference<>() { });
        assertEquals(1, versions.stream().filter(v -> "APPROVED".equals(v.status())).count());
        assertEquals("ARCHIVED", versions.stream().filter(v -> v.versionNo() == first.versionNo()).findFirst().orElseThrow().status());
        byte[] xlsx = mvc.perform(get(url + "/versions/" + second.versionNo() + "/files/fullCalendar.xlsx"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheet("Calendar");
            assertNotNull(sheet);
            List<String> exported = new ArrayList<>();
            for (org.apache.poi.ss.usermodel.Row row : sheet) {
                if (code.equals(row.getCell(0).getStringCellValue()) && !row.getCell(5).getStringCellValue().isBlank()) {
                    exported.add(row.getCell(1).getStringCellValue() + "/" + row.getCell(3).getStringCellValue() + "/"
                            + row.getCell(4).getStringCellValue() + "/" + row.getCell(5).getStringCellValue() + "/"
                            + row.getCell(6).getStringCellValue());
                }
            }
            assertEquals(30, exported.size());
            assertEquals(ten.stream().map(m -> m.round() + "/"
                    + net.atos.mev.calendarcalculator.schedules.SchEnvironment.writeDate(m.date()) + "/" + m.hour() + "/"
                    + m.home() + "/" + m.away()).sorted().toList(), exported.stream().sorted().toList());
        }
    }

    @Test
    void approvalAloneDoesNotFreezePreviouslyGeneratedFixtures() throws Exception {
        saveChange(tree -> tree.set("timeslots", json.valueToTree(List.of("15:00"))));
        SetupDtos.PlanningJob first = plan(5);
        List<SetupDtos.GeneratedMatch> before = ownMatches(first);
        approve(first);
        assertTrue(setup().fixtures().stream().noneMatch(SetupDtos.Fixture::locked));
        saveChange(tree -> tree.set("timeslots", json.valueToTree(List.of("19:00"))));
        List<SetupDtos.GeneratedMatch> after = ownMatches(plan(10));
        assertSchedule(after, 10);
        assertTrue(before.stream().allMatch(m -> "15:00".equals(m.hour())));
        // Las jornadas ya PLANIFICADAS están protegidas: la aprobación no las congela, pero la replanificación incremental las conserva.
        assertEquals(byFixture(before), byFixture(after.stream().filter(m -> Integer.parseInt(m.round()) <= 5).toList()));
        assertTrue(after.stream().filter(m -> Integer.parseInt(m.round()) > 5).allMatch(m -> "19:00".equals(m.hour())));
    }

    @Test
    void planningRespectsFifaBlackoutChampionsRestAndRamadanTimeslot() throws Exception {
        LocalDate thirdSaturday = FIRST_SATURDAY.plusWeeks(2);
        LocalDate championsDate = thirdSaturday.minusDays(3);
        saveChange(tree -> {
            tree.set("blackouts", json.valueToTree(List.of(new SetupDtos.Blackout(null, "FIFA window", FIRST_SATURDAY, FIRST_SATURDAY))));
            tree.set("external", json.valueToTree(List.of(new SetupDtos.ExternalMatch(null, "CL", "1", championsDate,
                    "21:00", teams.getFirst().code(), "EXT"))));
            tree.set("ramadan", json.valueToTree(new SetupDtos.Ramadan(thirdSaturday, thirdSaturday.plusDays(1), "22:00")));
        });
        List<SetupDtos.GeneratedMatch> matches = ownMatches(plan(5));
        assertSchedule(matches, 5);
        assertTrue(matches.stream().noneMatch(m -> FIRST_SATURDAY.equals(m.date())));
        List<SetupDtos.GeneratedMatch> third = matches.stream().filter(m -> "3".equals(m.round())).toList();
        assertEquals(3, third.size());
        assertTrue(third.stream().allMatch(m -> "22:00".equals(m.hour())));
        SetupDtos.GeneratedMatch affected = third.stream()
                .filter(m -> teams.getFirst().code().equals(m.home()) || teams.getFirst().code().equals(m.away())).findFirst().orElseThrow();
        assertTrue(ChronoUnit.DAYS.between(championsDate, affected.date()) > 3);
        assertEquals(thirdSaturday.plusDays(1), affected.date());
    }

    @Test
    void impossibleCalendarFailsWithoutPersistingASuccessfulPartialVersion() throws Exception {
        SetupDtos.PlanningJob original = plan(1);
        long cs = setups.competitionSeasonId(competition, SEASON);
        List<SetupDtos.GeneratedMatch> official = ownMatches(original);
        for (SetupDtos.GeneratedMatch match : official.subList(0, 2)) {
            matchStates.changeStatus(cs, new SetupDtos.MatchStatusRequest(1, match.home(), match.away(),
                    MatchStateService.DELIVERED, "Official delivery before failed generation"),
                    "failure-test-subject", "ROLE_TEST_SCHEDULE_ADMIN", "USER");
        }
        SetupDtos.GeneratedMatch played = official.getFirst();
        matchStates.changeStatus(cs, new SetupDtos.MatchStatusRequest(1, played.home(), played.away(),
                MatchStateService.PLAYED, "Official completion before failed generation"),
                "failure-test-subject", "ROLE_TEST_SCHEDULE_ADMIN", "USER");
        saveChange(tree -> tree.set("blackouts", json.valueToTree(List.of(new SetupDtos.Blackout(null, "No available days",
                FIRST_SATURDAY.plusWeeks(2), FIRST_SATURDAY.plusWeeks(2).plusDays(1))))));
        List<SetupDtos.MatchState> before = matchStates.list(cs);
        long audits = jdbc.sql("SELECT COUNT(*) FROM match_change_audit WHERE competition_season_id = :cs")
                .param("cs", cs).query(Long.class).single();
        SetupDtos.PlanningJob job = plan(2, 5, "FAILED");
        assertTrue(job.errorMessage().contains("No feasible assignment"), job.errorMessage());
        assertNotNull(job.startedAt());
        assertNotNull(job.finishedAt());
        assertNull(job.versionId());
        assertNull(job.versionNo());
        assertEquals(before, matchStates.list(cs));
        assertEquals(audits, jdbc.sql("SELECT COUNT(*) FROM match_change_audit WHERE competition_season_id = :cs")
                .param("cs", cs).query(Long.class).single());
        List<SetupDtos.Version> versions = json.readValue(mvc.perform(get(url + "/versions")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), new TypeReference<>() { });
        assertEquals(1, versions.size());
        assertEquals(official, ownMatches(original).stream().map(match -> new SetupDtos.GeneratedMatch(
                match.competition(), match.round(), match.date(), match.hour(), match.home(), match.away(),
                MatchStateService.PLANNED)).toList());
        assertEquals(0, jdbc.sql("SELECT COUNT(*) FROM generation_version WHERE job_id = :job")
                .param("job", UUID.fromString(job.id())).query(Integer.class).single());
        assertFalse(jdbc.sql("SELECT COUNT(*) FROM planning_job_log WHERE job_id = :job AND level = 'ERROR'")
                .param("job", UUID.fromString(job.id())).query(Integer.class).single() == 0);
        saveChange(tree -> tree.set("blackouts", json.createArrayNode()));
        SetupDtos.PlanningJob recovered = plan(2, 5, "SUCCEEDED");
        assertNotEquals(job.id(), recovered.id());
        assertEquals("FAILED", json.readValue(mvc.perform(get("/api/setup/planning-jobs/" + job.id()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                SetupDtos.PlanningJob.class).status());
        assertEquals(before.stream().filter(state -> state.round() == 1).toList(),
                matchStates.list(cs).stream().filter(state -> state.round() == 1).toList());
    }

    @Test
    void planningRejectsMissingDatesAndOutOfRangeRoundsWithoutCreatingAJob() throws Exception {
        saveChange(tree -> tree.set("rounds", json.valueToTree(setupRounds(5))));
        assertTrue(validation(10).stream().anyMatch(i -> "matchdays".equals(i.section()) && "error".equals(i.level())));
        for (int last : new int[] {0, 10, 11}) {
            mvc.perform(post(url + "/planning-jobs").contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(new SetupDtos.StartPlanningRequest(last))))
                    .andExpect(status().isUnprocessableEntity());
        }
        assertEquals(0, jdbc.sql("SELECT COUNT(*) FROM planning_job WHERE competition_season_id = (SELECT id FROM competition_season WHERE competition_id = :c)")
                .param("c", competition).query(Integer.class).single());
    }

    @Test
    void planningFromOneRoundPersistsItsRangeAndCanBeRepeatedWithoutChangingOtherRounds() throws Exception {
        SetupDtos.PlanningJob first = plan(3, 3, "SUCCEEDED");
        assertEquals(3, first.firstRoundToAssign());
        assertEquals(3, first.lastRoundToAssign());
        List<SetupDtos.GeneratedMatch> initialMatches = ownMatches(first);
        assertEquals(3, initialMatches.size());
        assertTrue(initialMatches.stream().allMatch(match -> "3".equals(match.round())));
        assertEquals(0, setup().fixtures().stream().filter(f -> f.round() != 3 && f.date() != null).count());

        SetupDtos.Version firstVersion = json.readValue(mvc.perform(get(url + "/versions"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                new TypeReference<List<SetupDtos.Version>>() { }).getFirst();
        assertEquals(3, firstVersion.firstRoundToAssign());
        assertEquals(3, firstVersion.lastRoundToAssign());
        long auditCount = jdbc.sql("""
                SELECT COUNT(*) FROM match_change_audit
                 WHERE competition_season_id = (SELECT id FROM competition_season WHERE competition_id = :c AND season_id = :s)
                   AND round_no = 3 AND action = 'PLANNING_GENERATED'""")
                .param("c", competition).param("s", SEASON).query(Long.class).single();

        SetupDtos.GeneratedMatch delivered = initialMatches.get(0);
        SetupDtos.GeneratedMatch played = initialMatches.get(1);
        SetupDtos.GeneratedMatch fixed = initialMatches.get(2);
        setups.lockFixture(competition, SEASON,
                new SetupDtos.FixtureLockRequest(fixed.home(), fixed.away(), fixed.date(), fixed.hour(), true));
        long cs = jdbc.sql("SELECT id FROM competition_season WHERE competition_id = :c AND season_id = :s")
                .param("c", competition).param("s", SEASON).query(Long.class).single();
        for (SetupDtos.GeneratedMatch match : List.of(delivered, played)) {
            matchStates.changeStatus(cs, new SetupDtos.MatchStatusRequest(3, match.home(), match.away(),
                   MatchStateService.DELIVERED, "Protect incremental planning fixture"),
                   "incremental-test-subject", "ROLE_TEST_SCHEDULE_ADMIN", "USER");
        }
        matchStates.changeStatus(cs, new SetupDtos.MatchStatusRequest(3, played.home(), played.away(),
                MatchStateService.PLAYED, "Confirm incremental planning fixture played"),
                "incremental-test-subject", "ROLE_TEST_SCHEDULE_ADMIN", "USER");

        SetupDtos.PlanningJob repeated = plan(3, 3, "SUCCEEDED");
        List<SetupDtos.GeneratedMatch> expectedMatches = initialMatches.stream().map(match -> {
            String status = match.home().equals(delivered.home()) && match.away().equals(delivered.away())
                    ? MatchStateService.DELIVERED
                    : match.home().equals(played.home()) && match.away().equals(played.away())
                            ? MatchStateService.PLAYED : MatchStateService.PLANNED;
            return new SetupDtos.GeneratedMatch(match.competition(), match.round(), match.date(), match.hour(),
                    match.home(), match.away(), status);
        }).toList();
        assertEquals(expectedMatches, ownMatches(repeated));
        assertEquals(auditCount, jdbc.sql("""
                SELECT COUNT(*) FROM match_change_audit
                 WHERE competition_season_id = (SELECT id FROM competition_season WHERE competition_id = :c AND season_id = :s)
                   AND round_no = 3 AND action = 'PLANNING_GENERATED'""")
                .param("c", competition).param("s", SEASON).query(Long.class).single());
        assertEquals(1, jdbc.sql("""
                SELECT COUNT(*) FROM season_match_state
                 WHERE competition_season_id = :cs AND round_no = 3 AND home = :home AND away = :away
                  AND status = 'ENTREGADA' AND play_date = :date AND play_hour = :hour""")
                .param("cs", cs).param("home", delivered.home()).param("away", delivered.away())
                .param("date", delivered.date()).param("hour", delivered.hour()).query(Integer.class).single());
        assertEquals(1, jdbc.sql("""
                SELECT COUNT(*) FROM season_match_state
                 WHERE competition_season_id = :cs AND round_no = 3 AND home = :home AND away = :away
                  AND status = 'JUGADA' AND play_date = :date AND play_hour = :hour""")
                .param("cs", cs).param("home", played.home()).param("away", played.away())
                .param("date", played.date()).param("hour", played.hour()).query(Integer.class).single());
        assertTrue(matchStates.protectFinalizedMatches(setups.load(competition, SEASON)).fixtures().stream()
                .filter(fixture -> fixture.round() == 3)
                .filter(fixture -> (fixture.home().equals(delivered.home()) && fixture.away().equals(delivered.away()))
                       || (fixture.home().equals(played.home()) && fixture.away().equals(played.away()))
                       || (fixture.home().equals(fixed.home()) && fixture.away().equals(fixed.away())))
                .allMatch(SetupDtos.Fixture::locked));
        assertEquals(1, jdbc.sql("""
                SELECT COUNT(*) FROM fixture
                 WHERE competition_season_id = :cs AND round_no = 3 AND home = :home AND away = :away
                   AND locked = :locked AND play_date = :date AND play_hour = :hour""")
                .param("cs", cs).param("home", fixed.home()).param("away", fixed.away()).param("locked", true)
                .param("date", fixed.date()).param("hour", fixed.hour()).query(Integer.class).single());
    }

    private List<SetupDtos.Fixture> fixtures() {
        int[][] pairs = {{0,5,1,4,2,3}, {4,0,3,5,2,1}, {0,3,4,2,5,1}, {2,0,1,3,5,4}, {0,1,2,5,3,4}};
        List<SetupDtos.Fixture> result = new ArrayList<>();
        for (int r = 0; r < pairs.length; r++) {
            for (int p = 0; p < 6; p += 2) {
                String home = teams.get(pairs[r][p]).code(), away = teams.get(pairs[r][p + 1]).code();
                result.add(new SetupDtos.Fixture(null, r + 1, home, away, false, null, null));
                result.add(new SetupDtos.Fixture(null, r + 6, away, home, false, null, null));
            }
        }
        return result.stream().sorted(Comparator.comparingInt(SetupDtos.Fixture::round)).toList();
    }

    private List<SetupDtos.Round> setupRounds(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(r -> new SetupDtos.Round(r, null, r <= 5 ? 1 : 2,
                List.of(FIRST_SATURDAY.plusWeeks(r - 1), FIRST_SATURDAY.plusWeeks(r - 1).plusDays(1)))).toList();
    }

    private SetupDtos.SeasonSetup setup() throws Exception {
        return json.readValue(mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), SetupDtos.SeasonSetup.class);
    }

    private void saveChange(Consumer<ObjectNode> change) throws Exception {
        ObjectNode tree = json.valueToTree(setup());
        change.accept(tree);
        mvc.perform(put(url).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(tree))).andExpect(status().isOk());
    }

    private List<SetupDtos.ValidationIssue> validation(int last) throws Exception {
        return json.readValue(mvc.perform(post(url + "/validate").param("lastRoundToAssign", String.valueOf(last)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), new TypeReference<>() { });
    }

    private List<SetupDtos.Federation> federations(boolean inactive) throws Exception {
        return json.readValue(mvc.perform(get("/api/federations").param("includeInactive", String.valueOf(inactive)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), new TypeReference<>() { });
    }

    private List<SetupDtos.CompetitionSummary> competitions() throws Exception {
        return json.readValue(mvc.perform(get("/api/federations/" + federation + "/competitions"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), new TypeReference<>() { });
    }

    private void federationStatus(String state) throws Exception {
        mvc.perform(patch("/api/federations/" + federation + "/status").contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"" + state + "\"}")).andExpect(status().isOk());
    }

    private SetupDtos.PlanningJob plan(int last) throws Exception {
        return plan(last, "SUCCEEDED");
    }

    private SetupDtos.PlanningJob plan(int last, String expectedStatus) throws Exception {
        return plan(null, last, expectedStatus);
    }

    private SetupDtos.PlanningJob plan(Integer first, int last, String expectedStatus) throws Exception {
        SetupDtos.PlanningJob job = json.readValue(mvc.perform(post(url + "/planning-jobs").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new SetupDtos.StartPlanningRequest(first, last))))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString(), SetupDtos.PlanningJob.class);
        long deadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (List.of("QUEUED", "RUNNING").contains(job.status()) && System.nanoTime() < deadline) {
            Thread.sleep(100);
            job = json.readValue(mvc.perform(get("/api/setup/planning-jobs/" + job.id())).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString(), SetupDtos.PlanningJob.class);
        }
        if (List.of("QUEUED", "RUNNING").contains(job.status())) {
            mvc.perform(post("/api/setup/planning-jobs/" + job.id() + "/cancel")).andExpect(status().isOk());
            fail("Planning exceeded 30 seconds: " + job.id());
        }
        assertEquals(expectedStatus, job.status(), job.errorMessage());
        if ("SUCCEEDED".equals(expectedStatus)) {
            assertNotNull(job.versionNo());
        }
        return job;
    }

    private List<SetupDtos.GeneratedMatch> ownMatches(SetupDtos.PlanningJob job) throws Exception {
        List<SetupDtos.GeneratedMatch> all = json.readValue(mvc.perform(get(url + "/versions/" + job.versionNo() + "/matches"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), new TypeReference<>() { });
        return all.stream().filter(m -> code.equals(m.competition())).toList();
    }

    private void approve(SetupDtos.PlanningJob job) throws Exception {
        // El endpoint de aprobación exige OIDC (denyAll sin él); se usa el repositorio directamente.
        generations.approve(setups.competitionSeasonId(competition, SEASON), job.versionNo(), "test-approver");
        List<SetupDtos.Version> versions = generations.versions(setups.competitionSeasonId(competition, SEASON));
        assertEquals("APPROVED", versions.stream().filter(v -> v.versionNo() == job.versionNo()).findFirst().orElseThrow().status());
    }

    private void assertSchedule(List<SetupDtos.GeneratedMatch> matches, int last) throws Exception {
        assertEquals(last * 3, matches.size());
        assertEquals(last * 3, byFixture(matches).size());
        assertTrue(matches.stream().collect(Collectors.groupingBy(SetupDtos.GeneratedMatch::date, Collectors.counting()))
                .values().stream().allMatch(count -> count <= 3), "Maximum three matches per day");
        Map<Integer, List<SetupDtos.Fixture>> expected = setup().fixtures().stream().collect(Collectors.groupingBy(SetupDtos.Fixture::round));
        for (int round = 1; round <= last; round++) {
            String label = String.valueOf(round);
            List<SetupDtos.GeneratedMatch> actual = matches.stream().filter(m -> label.equals(m.round())).toList();
            assertEquals(3, actual.size());
            assertEquals(expected.get(round).stream().map(f -> f.home() + "/" + f.away()).collect(Collectors.toSet()),
                    actual.stream().map(m -> m.home() + "/" + m.away()).collect(Collectors.toSet()));
            for (SetupDtos.GeneratedMatch match : actual) {
                assertTrue(List.of(FIRST_SATURDAY.plusWeeks(round - 1), FIRST_SATURDAY.plusWeeks(round - 1).plusDays(1)).contains(match.date()));
                assertNotNull(match.hour());
                assertDoesNotThrow(() -> java.time.LocalTime.parse(match.hour()), match.toString());
            }
        }
        for (SetupDtos.Team team : teams) {
            List<LocalDate> dates = matches.stream().filter(m -> team.code().equals(m.home()) || team.code().equals(m.away()))
                    .map(SetupDtos.GeneratedMatch::date).sorted().toList();
            assertEquals(last, dates.size());
            for (int i = 1; i < dates.size(); i++) {
                assertTrue(ChronoUnit.DAYS.between(dates.get(i - 1), dates.get(i)) > 3, "Rest days for " + team.code());
            }
        }
    }

    private Map<String, SetupDtos.GeneratedMatch> byFixture(List<SetupDtos.GeneratedMatch> matches) {
        return matches.stream().collect(Collectors.toMap(m -> m.round() + "/" + m.home() + "/" + m.away(), m -> m));
    }
}
