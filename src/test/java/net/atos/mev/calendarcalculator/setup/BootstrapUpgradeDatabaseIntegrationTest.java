package net.atos.mev.calendarcalculator.setup;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.DirectoryResourceAccessor;

@EnabledIfEnvironmentVariable(named = "CALENDAR_BOOTSTRAP_TEST_URL", matches = ".+")
class BootstrapUpgradeDatabaseIntegrationTest {
    @TempDir Path temporary;

    private String resource(String name) throws Exception {
        try (var input = getClass().getClassLoader().getResourceAsStream("db/changelog/" + name)) {
            assertNotNull(input);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void upgradePreservesOriginalIdentitiesChecksumsAndAllHistoricalRows() throws Exception {
        String url = System.getenv("CALENDAR_BOOTSTRAP_TEST_URL");
        assertNotNull(url, "Use a dedicated disposable database via CALENDAR_BOOTSTRAP_TEST_URL");
        Path destination = temporary.resolve("db/changelog");
        for (String resource : List.of("db.changelog-schema.yaml", "db.changelog-legacy-fixtures.yaml")) {
            Path source = Path.of(getClass().getClassLoader().getResource("db/changelog/" + resource).toURI()).getParent();
            try (var paths = Files.walk(source)) {
                for (Path path : paths.toList()) {
                    Path copy = destination.resolve(source.relativize(path));
                    if (Files.isDirectory(path)) Files.createDirectories(copy);
                    else Files.copy(path, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        Path original = temporary.resolve("db/changelog/db.changelog-master.yaml");
        Files.createDirectories(original.getParent());
        String schema = resource("db.changelog-schema.yaml");
        String fixtures = resource("db.changelog-legacy-fixtures.yaml");
        String header = "databaseChangeLog:\n  - logicalFilePath: db/changelog/db.changelog-master.yaml\n";
        assertTrue(fixtures.startsWith(header));
        String updates = resource("db.changelog-updates.yaml")
                .replace("  - include:\n      file: changes/009-reference-catalogs.yaml\n      relativeToChangelogFile: true\n", "")
                .replace("  - include:\n      file: changes/010-legacy-database-aliases.yaml\n      relativeToChangelogFile: true\n", "")
                .replace("  - include:\n      file: changes/011-match-state-and-audit.yaml\n      relativeToChangelogFile: true\n", "")
                .replace("  - include:\n      file: changes/012-match-audit-actor-type.yaml\n      relativeToChangelogFile: true\n", "")
                .replace("  - include:\n      file: changes/013-planning-round-range.yaml\n      relativeToChangelogFile: true\n", "")
                .replace("  - include:\n      file: changes/014-match-state-season-cascade.yaml\n      relativeToChangelogFile: true\n", "");
        assertTrue(schema.startsWith(header));
        Files.writeString(original, "databaseChangeLog:\n" + schema.substring(header.length()) + fixtures.substring(header.length())
                + updates.substring("databaseChangeLog:\n".length()));
        try (var connection = DriverManager.getConnection(url, System.getenv("DB_USERNAME"), System.getenv("DB_PASSWORD"))) {
            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var resources = new DirectoryResourceAccessor(temporary)) {
                new Liquibase("db/changelog/db.changelog-master.yaml", resources, database).update(new Contexts(), new LabelExpression());
            }
            List<String> before = rows(connection, "SELECT id, author, filename, md5sum FROM databasechangelog ORDER BY id, author, filename");
            assertEquals(45, rows(connection, "SELECT id FROM databasechangelog WHERE id LIKE 'b26-seed-%'").size());
            List<String> dataBefore = businessRows(connection);
            try (var resources = new ClassLoaderResourceAccessor()) {
                var current = new Liquibase("db/changelog/db.changelog-master.yaml", resources, database);
                current.validate();
                current.update(new Contexts("catalogs"), new LabelExpression());
                current.update(new Contexts("seed"), new LabelExpression());
            }
            List<String> after = rows(connection, "SELECT id, author, filename, md5sum FROM databasechangelog ORDER BY id, author, filename");
            assertTrue(after.containsAll(before), "Original changelog identities and checksums must remain unchanged");
            assertEquals(before.size() + 6, after.size(),
                    "Reference catalogs, database aliases, match state, audit actor type, planning range and cascade migrations should be added");
            assertEquals(1, rows(connection, "SELECT id FROM databasechangelog WHERE id = 'match-state-and-audit'").size(),
                    "The match-state and audit changeset must be applied on upgrade");
            assertEquals(1, rows(connection, """
                    SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
                     WHERE TABLE_NAME = 'match_change_audit' AND COLUMN_NAME = 'actor_type'""").size(),
                    "The audit actor classification must be available after upgrade");
            assertEquals(2, rows(connection, """
                    SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
                     WHERE TABLE_NAME IN ('planning_job', 'generation_version')
                       AND COLUMN_NAME = 'first_round_to_assign'""").size(),
                    "The incremental range must be available after upgrade");
            assertEquals(rows(connection, "SELECT id FROM fixture").size(),
                    rows(connection, "SELECT competition_season_id, round_no, home, away FROM season_match_state").size(),
                    "Existing fixtures must be backfilled with their initial sports state");
            assertEquals(dataBefore, businessRows(connection), "No historical or user data may be reloaded or removed");
        }
    }

    private List<String> businessRows(java.sql.Connection connection) throws Exception {
        var result = new ArrayList<String>();
        for (String table : List.of("competition", "season", "team", "competition_season", "fixture",
                "generated_match", "generation_file", "constraint_rule")) {
            result.addAll(rows(connection, "SELECT * FROM " + table + " ORDER BY 1"));
        }
        result.addAll(rows(connection, """
                SELECT id, competition_season_id, version_no, status, last_round_to_assign, source, job_id,
                       config_snapshot, created_at, approved_at, approved_by
                  FROM generation_version ORDER BY id"""));
        result.addAll(rows(connection, """
                SELECT id, competition_season_id, status, last_round_to_assign, cancel_requested, created_at,
                       started_at, finished_at, error_message, version_id
                  FROM planning_job ORDER BY id"""));
        return result;
    }

    private List<String> rows(java.sql.Connection connection, String sql) throws Exception {
        var result = new ArrayList<String>();
        try (var statement = connection.createStatement(); var rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                var cells = new ArrayList<String>();
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) cells.add(rs.getString(i));
                result.add(cells.toString());
            }
        }
        return result;
    }
}
