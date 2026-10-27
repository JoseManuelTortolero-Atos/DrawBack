package net.atos.mev.calendarcalculator.setup;

import static org.junit.jupiter.api.Assertions.*;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.datasource.url=${CALENDAR_BOOTSTRAP_FRESH_URL}",
        "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml",
        "spring.liquibase.contexts=catalogs", "calendar.legacy-files.enabled=false",
        "calendar.demo.enabled=true", "calendar.demo.season-year=2032", "calendar.demo.team-count=6"
})
@ActiveProfiles("dev")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EnabledIfEnvironmentVariable(named = "CALENDAR_BOOTSTRAP_FRESH_URL", matches = ".+")
class DemoStartupDatabaseIntegrationTest {
    @Autowired DataSource dataSource;
    @Autowired JdbcClient jdbc;
    @Autowired DemoSeeder seeder;

    @Test
    void devOptInRunnerCommitsMinimalExampleAndSecondRunDoesNotOverwriteIt() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM competition_season_team")) {
                assertTrue(rows.next());
                assertEquals(6, rows.getInt(1), "Example must already be committed by the startup runner");
            }
        }
        jdbc.sql("UPDATE team SET name = 'User edit after startup' WHERE code = 'DM01'").update();
        seeder.seed(2034, 4);
        assertEquals("User edit after startup", jdbc.sql("SELECT name FROM team WHERE code = 'DM01'").query(String.class).single());
        assertEquals(1, jdbc.sql("SELECT COUNT(*) FROM competition_season").query(Integer.class).single());
        assertEquals("2032-33", jdbc.sql("SELECT season_id FROM competition_season").query(String.class).single());
        for (String table : new String[] {"fixture", "generation_version", "generated_match", "external_commitment"}) {
            assertEquals(0, jdbc.sql("SELECT COUNT(*) FROM " + table).query(Integer.class).single(), table);
        }
    }
}
