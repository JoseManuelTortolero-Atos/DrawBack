package net.atos.mev.calendarcalculator.setup;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import net.atos.mev.calendarcalculator.schedules.PlanningParameters;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class DatabaseEngineConfigurationDatabaseIntegrationTest {
    private static final String ALIASES = "/api/setup/competitions/BOTOLA_D1/legacy-aliases";
    @Autowired DatabaseEngineConfiguration configuration;
    @Autowired SetupToEngineMapper mapper;
    @Autowired JdbcClient jdbc;
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;

    @Test
    void aliasesAndExplicitSelectorsReadTheSameDatabaseSnapshot() {
        var explicit = configuration.setup("DB:BOTOLA_D1@2026-27", null);
        assertEquals(explicit, configuration.setup("MoroccoDiv1.properties", "2026-27"));
        assertEquals(explicit, configuration.setup("d1", "2026-27"));
        assertThrows(SetupExceptions.NotFound.class, () -> configuration.setup("missing.properties", null));
        assertThrows(IllegalArgumentException.class, () -> configuration.setup("DB:BOTOLA_D1@2026-27", "2030-31"));
    }

    @Test
    void memoryAdapterPreservesChecksGroupsTimesAndRestDaysWithoutReadingReferenceFiles() {
        var setup = configuration.setup("DB:CNPFF_D1@2026-27", null);
        var properties = configuration.properties("DB:CNPFF_D1@2026-27", null, null);
        var parsed = PlanningParameters.fromProperties(properties, setup.code());
        var typed = mapper.toParameters(setup, setup.general().lastRoundToAssign());
        assertEquals(typed.timeslots(), parsed.timeslots());
        assertEquals(typed.groupsToRun(), parsed.groupsToRun());
        assertEquals(typed.groupsCanGoToMAJ(), parsed.groupsCanGoToMAJ());
        assertArrayEquals(typed.roundCalculationOrder(), parsed.roundCalculationOrder());
        assertEquals(typed.roundsForwardLookPerGroup(), parsed.roundsForwardLookPerGroup());
        assertEquals(typed.restDays(), parsed.restDays());
        assertEquals(typed.failOnNoFeasibleAssignment(), parsed.failOnNoFeasibleAssignment());
        var env = configuration.environment(setup);
        assertEquals(env.nChecks, Integer.parseInt(properties.getProperty("nChecks")));
        assertEquals(properties.getProperty("nChecks"), env.getOriginalProp().getProperty("nChecks"));
        assertEquals(setup.teams().size(), env.nTeams);
        assertNotNull(configuration.planning(setup, 1).built().environment().getPlanningParameters());
    }

    @Test
    void aliasCrudUsesCompareAndSwapAndRejectsCrossCompetitionOrReservedNames() throws Exception {
        change("compat-p1", null, 200);
        assertEquals("BOTOLA_D1", configuration.setup("compat-p1", "2026-27").competitionId());
        change("overwritten", null, 409);
        change("overwritten", "COMPAT-P1", 409);
        change("compat-p2", "compat-p1", 200);
        change("BOTOLA_D2", "compat-p2", 409);
        change("DB:BOTOLA_D2", "compat-p2", 400);
        change("alias@2030-31", "compat-p2", 400);
        change(null, "compat-p2", 200);
        assertThrows(SetupExceptions.NotFound.class, () -> configuration.setup("compat-p2", "2026-27"));
        mvc.perform(put(ALIASES + "/FILE_ENV").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("value", "must-not-change")))).andExpect(status().isBadRequest());
    }

    @Test
    void adapterSerializesItsCapturedSnapshotInsteadOfRefreshingBusinessData() {
        var captured = configuration.setup("DB:CNPFF_D1@2026-27", null);
        var team = captured.teams().getFirst();
        jdbc.sql("""
                UPDATE competition_season_team SET display_name = 'Changed after snapshot'
                WHERE competition_season_id = (SELECT id FROM competition_season WHERE competition_id = 'CNPFF_D1' AND season_id = '2026-27')
                  AND team_code = :team""").param("team", team.code()).update();
        assertEquals(team.name(), configuration.properties(captured, null).getProperty("team." + team.code()));
        assertEquals("Changed after snapshot", configuration.properties("DB:CNPFF_D1@2026-27", null, null)
                .getProperty("team." + team.code()));
    }

    @Test
    void duplicateAliasesFailExplicitlyInsteadOfSelectingOneCompetition() {
        jdbc.sql("INSERT INTO competition_alias (competition_id, alias_type, alias_value) VALUES (:id, 'LEGACY_COLLISION', 'ambiguous-test')")
                .param("id", "BOTOLA_D1").update();
        jdbc.sql("INSERT INTO competition_alias (competition_id, alias_type, alias_value) VALUES (:id, 'LEGACY_COLLISION', 'ambiguous-test')")
                .param("id", "BOTOLA_D2").update();
        assertThrows(IllegalArgumentException.class, () -> configuration.setup("ambiguous-test", "2026-27"));
    }

    private void change(String value, String expected, int status) throws Exception {
        mvc.perform(put(ALIASES + "/LEGACY_TEST").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(new LegacyAliasController.Change(value, expected))))
                .andExpect(status().is(status));
        mvc.perform(get(ALIASES)).andExpect(status().isOk());
    }
}
