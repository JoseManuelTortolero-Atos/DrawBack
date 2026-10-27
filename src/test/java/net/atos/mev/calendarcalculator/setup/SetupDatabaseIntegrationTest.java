package net.atos.mev.calendarcalculator.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * PostgreSQL real (CI: servicio postgres:16; ver src/test/resources/application.yml).
 * Seed Liquibase + API + job completo con paridad + bloqueo optimista.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SetupDatabaseIntegrationTest {

    private static final String D1 = "/api/setup/competitions/BOTOLA_D1/seasons/2026-27";
    private static final String D2 = "/api/setup/competitions/BOTOLA_D2/seasons/2026-27";
    private static final String F2 = "/api/setup/competitions/CNPFF_D2/seasons/2026-27";
    private static final Set<String> DEFAULT_RULE_TYPES = Set.of("DerbyTeams", "MatchNotInRound", "SeparateMatches",
            "LimitHomeTogether", "PlayTogether", "NotConsecutiveFarAway", "LimitAgainstTeamsInRounds",
            "LimitAgainstTeamsInRoundsHome", "ForcePositions", "HalfMatchesAtHome", "VenueUnavailable", "FixedHomeDate");

    @Autowired
    MockMvc mvc;
    @Autowired
    JsonMapper json;
    private <T> T read(String body, TypeReference<T> type) throws Exception {
        return json.readValue(body, type);
    }

    @Test
    void seedIsLoaded() throws Exception {
        List<SetupDtos.CompetitionSeasonRef> list = read(mvc.perform(get("/api/setup/competitions")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), new TypeReference<>() { });
        assertEquals(List.of("BOTOLA_D1", "BOTOLA_D2", "CNPFF_D1", "CNPFF_D2"), list.stream().map(SetupDtos.CompetitionSeasonRef::competitionId).toList());
        SetupDtos.SeasonSetup d1 = read(mvc.perform(get(D1)).andReturn().getResponse().getContentAsString(), new TypeReference<>() { });
        assertEquals(16, d1.teams().size());
        assertEquals(240, d1.fixtures().size());
        assertEquals(20, d1.notHomeSameDay().size());
        List<SetupDtos.Version> versions = read(mvc.perform(get(D1 + "/versions")).andReturn().getResponse().getContentAsString(), new TypeReference<>() { });
        SetupDtos.Version v0 = versions.stream().filter(v -> v.versionNo() == 0).findFirst().orElseThrow();
        assertEquals("APPROVED", v0.status());
        assertEquals("LEGACY_IMPORT", v0.source());
        assertEquals(6, v0.files().size());
    }

    @Test
    @Transactional
    void newlyCreatedFederationReceivesDefaultP1AndP2RuleTypes() throws Exception {
        String code = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 15).toUpperCase();
        mvc.perform(post("/api/federations/" + code).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Catalog test\",\"countryCode\":\"MA\",\"timeZone\":\"Africa/Casablanca\"}"))
                .andExpect(status().isOk());

        List<SetupDtos.RuleTypeDef> types = ruleTypes(code);

        assertEquals(DEFAULT_RULE_TYPES, types.stream().map(SetupDtos.RuleTypeDef::type).collect(Collectors.toSet()));
        assertTrue(types.stream().filter(type -> type.enabled()).allMatch(type -> !type.allowedScopes().isEmpty()));
        assertEquals(2, types.stream().filter(type -> "PLANNING".equals(type.scope())).count());
    }

    @Test
    void existingDemoFederationReceivesDefaultP1AndP2RuleTypesFromLiquibase() throws Exception {
        List<SetupDtos.RuleTypeDef> types = ruleTypes("DEMO");

        assertEquals(DEFAULT_RULE_TYPES, types.stream().map(SetupDtos.RuleTypeDef::type).collect(Collectors.toSet()));
        assertTrue(types.stream().filter(type -> type.enabled()).allMatch(type -> !type.allowedScopes().isEmpty()));
    }

    @Test
    @Transactional
    void staleRowVersionGives409() throws Exception {
        String body = mvc.perform(get(F2)).andReturn().getResponse().getContentAsString();
        mvc.perform(put(F2).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());
        // segundo PUT con el mismo rowVersion (ya incrementado en BD) -> 409
        mvc.perform(put(F2).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
    }

    @Test
    void fullJobReproducesTheLegacyV0() throws Exception {
        String started = mvc.perform(post(D1 + "/planning-jobs").contentType(MediaType.APPLICATION_JSON).content("{\"lastRoundToAssign\":5}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        SetupDtos.PlanningJob job = read(started, new TypeReference<>() { });
        UUID id = UUID.fromString(job.id());
        for (int i = 0; i < 120 && !List.of("SUCCEEDED", "FAILED", "CANCELLED").contains(job.status()); i++) {
            Thread.sleep(500);
            job = read(mvc.perform(get("/api/setup/planning-jobs/" + id)).andReturn().getResponse().getContentAsString(), new TypeReference<>() { });
        }
        assertEquals("SUCCEEDED", job.status(), String.valueOf(job.errorMessage()));
        List<SetupDtos.GeneratedMatch> v0 = read(mvc.perform(get(D1 + "/versions/0/matches")).andReturn().getResponse().getContentAsString(),
                new TypeReference<>() { });
        List<SetupDtos.GeneratedMatch> vn = read(mvc.perform(get(D1 + "/versions/" + job.versionNo() + "/matches")).andReturn().getResponse()
                .getContentAsString(), new TypeReference<>() { });
        assertEquals(v0.stream().filter(m -> m.competition().equals("D1")).toList(), vn.stream().filter(m -> m.competition().equals("D1")).toList());
        List<SetupDtos.JobLogLine> logs = read(mvc.perform(get("/api/setup/planning-jobs/" + id + "/logs")).andReturn().getResponse()
                .getContentAsString(), new TypeReference<>() { });
        assertTrue(logs.stream().anyMatch(l -> l.message().startsWith("Version v")));
        mvc.perform(get(D1 + "/versions/" + job.versionNo() + "/files/fullCalendar.xlsx")).andExpect(status().isOk());
    }

    @Test
    void botolaD2PlanningAppliesAllSeventeenPersistedNoHomeSameDayPairs() throws Exception {
        SetupDtos.SeasonSetup setup = read(mvc.perform(get(D2)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                new TypeReference<>() { });
        assertEquals(17, setup.notHomeSameDay().size());
        assertEquals(17, setup.notHomeSameDay().stream()
                .map(pair -> String.join(",", pair.stream().sorted().toList())).collect(Collectors.toSet()).size());
        assertTrue(setup.notHomeSameDay().stream().noneMatch(pair -> pair.get(0).equals(pair.get(1))));

        String started = mvc.perform(post(D2 + "/planning-jobs").contentType(MediaType.APPLICATION_JSON)
                .content("{\"lastRoundToAssign\":5}")).andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        SetupDtos.PlanningJob job = read(started, new TypeReference<>() { });
        UUID id = UUID.fromString(job.id());
        for (int i = 0; i < 120 && !List.of("SUCCEEDED", "FAILED", "CANCELLED").contains(job.status()); i++) {
            Thread.sleep(500);
            job = read(mvc.perform(get("/api/setup/planning-jobs/" + id)).andReturn().getResponse().getContentAsString(),
                    new TypeReference<>() { });
        }
        assertEquals("SUCCEEDED", job.status(), String.valueOf(job.errorMessage()));

        List<SetupDtos.GeneratedMatch> matches = read(mvc.perform(get(D2 + "/versions/" + job.versionNo() + "/matches"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), new TypeReference<>() { });
        List<SetupDtos.GeneratedMatch> d2Matches = matches.stream().filter(m -> setup.code().equals(m.competition())).toList();
        assertFalse(d2Matches.isEmpty());
        Map<LocalDate, Set<String>> homeTeamsByDate = d2Matches.stream().filter(m -> m.date() != null)
                .collect(Collectors.groupingBy(SetupDtos.GeneratedMatch::date,
                        Collectors.mapping(SetupDtos.GeneratedMatch::home, Collectors.toSet())));
        for (List<String> pair : setup.notHomeSameDay()) {
            assertFalse(homeTeamsByDate.values().stream().anyMatch(homeTeams -> homeTeams.containsAll(pair)),
                    () -> "D2 teams " + pair + " were both at home on the same date");
        }
    }

    @Test
    void invalidLastRoundGives422WithIssues() throws Exception {
        mvc.perform(post(D1 + "/planning-jobs").contentType(MediaType.APPLICATION_JSON).content("{\"lastRoundToAssign\":6}"))
                .andExpect(status().isUnprocessableEntity());
    }

    private List<SetupDtos.RuleTypeDef> ruleTypes(String federationCode) throws Exception {
        return read(mvc.perform(get("/api/setup/federations/" + federationCode + "/rule-types")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), new TypeReference<>() { });
    }
}
