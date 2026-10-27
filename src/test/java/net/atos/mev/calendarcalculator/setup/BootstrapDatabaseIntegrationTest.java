package net.atos.mev.calendarcalculator.setup;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = {
        "spring.datasource.url=${CALENDAR_BOOTSTRAP_FRESH_URL}",
        "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml",
        "spring.liquibase.contexts=catalogs", "calendar.legacy-files.enabled=false", "calendar.demo.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "CALENDAR_BOOTSTRAP_FRESH_URL", matches = ".+")
class BootstrapDatabaseIntegrationTest {
    @Autowired JdbcClient jdbc;
    @Autowired DemoSeeder seeder;
    @Autowired SetupCatalogRepository catalog;
    @Autowired SeasonSetupRepository setups;
    @Autowired MockMvc mvc;

    @Test
    void freshInstallationHasCatalogsButNoBusinessExamples() throws Exception {
        for (String table : new String[] {"competition", "season", "team", "competition_season", "fixture",
                "generation_version", "generated_match", "generation_file", "constraint_rule", "rule_scope_assignment"}) {
            assertEquals(0, jdbc.sql("SELECT COUNT(*) FROM " + table).query(Integer.class).single(), table);
        }
        assertEquals(19, jdbc.sql("SELECT COUNT(*) FROM external_competition").query(Integer.class).single());
        for (String federation : new String[] {"FRMF", "DEMO"}) {
            assertEquals(12, catalog.ruleTypes(federation).size());
            assertEquals(2, catalog.ruleTypes(federation).stream().filter(t -> "PLANNING".equals(t.scope())).count());
        }
        assertEquals(1, jdbc.sql("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                 WHERE TABLE_NAME = 'match_change_audit' AND COLUMN_NAME = 'actor_type'""")
                .query(Integer.class).single());
        assertEquals(2, jdbc.sql("""
                SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
                 WHERE TABLE_NAME IN ('planning_job', 'generation_version')
                   AND COLUMN_NAME = 'first_round_to_assign'""")
                .query(Integer.class).single());
        mvc.perform(get("/api/setup/reference-catalogs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.externalCompetitions.length()").value(19))
                .andExpect(jsonPath("$.excelColours").isArray())
                .andExpect(jsonPath("$.excelColours[?(@ == 'RED')]").exists());
    }

    @Test
    void explicitMinimalExampleIsConfigurableIdempotentAndPreservesEdits() {
        seeder.seed(2032, 6);
        var setup = setups.load("DEV_MINI", "2032-33");
        assertEquals(6, setup.teams().size());
        assertEquals(java.util.List.of("15:00"), setup.timeslots());
        assertEquals(1, setup.engine().assignGroups().size());
        assertTrue(setup.fixtures().isEmpty());
        assertTrue(setup.external().isEmpty());
        assertTrue(setup.rules().isEmpty());
        jdbc.sql("UPDATE team SET name = 'User edited' WHERE code = 'DM01'").update();
        seeder.seed(2034, 4);
        assertEquals(1, jdbc.sql("SELECT COUNT(*) FROM competition_season").query(Integer.class).single());
        assertEquals(6, jdbc.sql("SELECT COUNT(*) FROM team").query(Integer.class).single());
        assertEquals("User edited", jdbc.sql("SELECT name FROM team WHERE code = 'DM01'").query(String.class).single());
        assertEquals(0, jdbc.sql("SELECT COUNT(*) FROM generation_version").query(Integer.class).single());
    }

    @Test
    void newFederationIncludesBothStagesAndInternalChecksStayDisabled() {
        catalog.createFederation("CAT023", new SetupDtos.FederationUpsertRequest("Catalog 023", "MA", "Africa/Casablanca"));
        var rules = catalog.ruleTypes("CAT023");
        assertEquals(12, rules.size());
        assertEquals(2, rules.stream().filter(t -> "PLANNING".equals(t.scope())).count());
        var internal = rules.stream().filter(t -> "HalfMatchesAtHome".equals(t.type())).findFirst().orElseThrow();
        assertFalse(internal.enabled());
        assertTrue(internal.allowedScopes().isEmpty());
        assertEquals(java.util.List.of("COMPETITION_SEASON"),
                rules.stream().filter(t -> "FixedHomeDate".equals(t.type())).findFirst().orElseThrow().allowedScopes());
    }
}
