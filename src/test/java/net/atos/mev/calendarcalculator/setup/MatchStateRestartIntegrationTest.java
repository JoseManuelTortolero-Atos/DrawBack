package net.atos.mev.calendarcalculator.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;

import net.atos.mev.calendarcalculator.CalendarcalculatorApplication;
import net.atos.mev.calendarcalculator.setup.SetupDtos.Fixture;
import net.atos.mev.calendarcalculator.setup.SetupDtos.GeneratedMatch;
import net.atos.mev.calendarcalculator.setup.SetupDtos.MatchStatusRequest;

@SpringBootTest
class MatchStateRestartIntegrationTest {

    private static final String COMPETITION = "BOTOLA_D1";
    private static final String SEASON = "2026-27";

    @Autowired
    JdbcClient jdbc;
    @Autowired
    SeasonSetupRepository setups;
    @Autowired
    MatchStateService states;
    @Autowired
    GenerationRepository generations;
    @TempDir
    Path temporary;

    @Test
    void matchStateAndAuditSurviveApplicationContextRestart() {
        Fixture fixture = setups.load(COMPETITION, SEASON).fixtures().get(0);
        long cs = setups.competitionSeasonId(COMPETITION, SEASON);
        LocalDate date = setups.load(COMPETITION, SEASON).general().seasonStart().plusDays(1);
        String reason = "Restart durability check " + UUID.randomUUID();
        jdbc.sql("""
                UPDATE season_match_state
                   SET status = 'PLANIFICADA', play_date = :date, play_hour = '19:00'
                 WHERE competition_season_id = :cs AND round_no = :round AND home = :home AND away = :away""")
                .param("date", date).param("cs", cs).param("round", fixture.round())
                .param("home", fixture.home()).param("away", fixture.away()).update();
        states.changeStatus(cs,
                new MatchStatusRequest(fixture.round(), fixture.home(), fixture.away(), MatchStateService.DELIVERED,
                        reason),
                "restart-test-subject", "ROLE_TEST_SCHEDULE_ADMIN", "USER");

        verifyPersistedStateInNewContext(cs, fixture, reason);
        verifyPersistedStateInNewContext(cs, fixture, reason);
    }

    @Test
    void fullBackendProcessRestartPreservesBusinessDataVersionsStatesAndAudit() throws Exception {
        Fixture fixture = setups.load(COMPETITION, SEASON).fixtures().get(0);
        long cs = setups.competitionSeasonId(COMPETITION, SEASON);
        LocalDate date = setups.load(COMPETITION, SEASON).general().seasonStart().plusDays(1);
        String reason = "Process restart durability " + UUID.randomUUID();
        jdbc.sql("""
                UPDATE season_match_state
                   SET status = 'PLANIFICADA', play_date = :date, play_hour = '19:00'
                 WHERE competition_season_id = :cs AND round_no = :round AND home = :home AND away = :away""")
                .param("date", date).param("cs", cs).param("round", fixture.round())
                .param("home", fixture.home()).param("away", fixture.away()).update();
        states.changeStatus(cs,
                new MatchStatusRequest(fixture.round(), fixture.home(), fixture.away(), MatchStateService.DELIVERED, reason),
                "process-restart-subject", "ROLE_TEST_SCHEDULE_ADMIN", "USER");
        SetupDtos.Version marker = generations.createVersion(cs, fixture.round(), fixture.round(), null,
                "{\"restartMarker\":\"" + reason + "\"}",
                List.of(new GeneratedMatch(COMPETITION, String.valueOf(fixture.round()), date, "19:00",
                        fixture.home(), fixture.away())), Map.of());
        PersistenceSnapshot before = snapshot();
        assertTrue(before.competitions() > 0 && before.competitionSeasons() > 0
                && before.seasons() > 0 && before.teams() > 0);
        assertTrue(before.versions() > 0 && before.generatedMatches() > 0 && before.fixtures() > 0);
        assertTrue(before.ruleToggles().stream().anyMatch("true"::equals));
        assertTrue(before.ruleToggles().stream().anyMatch("false"::equals));

        startAndStopBackendProcess(temporary.resolve("backend-first-start.log"));
        assertEquals(before, snapshot(), "The first backend process must not lose persisted business data");
        startAndStopBackendProcess(temporary.resolve("backend-second-start.log"));
        assertEquals(before, snapshot(), "The second backend process must not lose persisted business data");

        assertEquals(1, jdbc.sql("""
                SELECT COUNT(*) FROM generation_version
                 WHERE id = :id AND first_round_to_assign = :first AND last_round_to_assign = :last""")
                .param("id", marker.id()).param("first", fixture.round()).param("last", fixture.round())
                .query(Integer.class).single());
        assertEquals(1, jdbc.sql("SELECT COUNT(*) FROM generated_match WHERE version_id = :id")
                .param("id", marker.id()).query(Integer.class).single());
        assertEquals(1, jdbc.sql("""
                SELECT COUNT(*) FROM match_change_audit
                 WHERE competition_season_id = :cs AND round_no = :round AND home = :home AND away = :away
                   AND actor = 'process-restart-subject' AND reason = :reason""")
                .param("cs", cs).param("round", fixture.round()).param("home", fixture.home()).param("away", fixture.away())
                .param("reason", reason).query(Integer.class).single());
    }

    private void verifyPersistedStateInNewContext(long cs, Fixture fixture, String reason) {
        try (ConfigurableApplicationContext restarted = new SpringApplicationBuilder(CalendarcalculatorApplication.class)
                .profiles("test")
                .run("--spring.liquibase.drop-first=false", "--server.port=0")) {
            JdbcClient restartedJdbc = restarted.getBean(JdbcClient.class);
            assertEquals(MatchStateService.DELIVERED, restartedJdbc.sql("""
                    SELECT status FROM season_match_state
                     WHERE competition_season_id = :cs AND round_no = :round AND home = :home AND away = :away""")
                    .param("cs", cs).param("round", fixture.round()).param("home", fixture.home()).param("away", fixture.away())
                    .query(String.class).single());
            assertEquals(1, restartedJdbc.sql("""
                    SELECT COUNT(*) FROM match_change_audit
                     WHERE competition_season_id = :cs AND round_no = :round AND home = :home AND away = :away
                       AND actor = 'restart-test-subject' AND reason = :reason""")
                    .param("cs", cs).param("round", fixture.round()).param("home", fixture.home()).param("away", fixture.away())
                    .param("reason", reason)
                    .query(Integer.class).single());
        }
    }

    private PersistenceSnapshot snapshot() {
        List<String> ruleToggles = jdbc.sql("SELECT enabled FROM rule_type_catalog ORDER BY federation_code, rule_type")
                .query((rs, row) -> Boolean.toString(rs.getBoolean(1))).list();
        List<String> states = jdbc.sql("SELECT status, COUNT(*) FROM season_match_state GROUP BY status ORDER BY status")
                .query((rs, row) -> rs.getString(1) + ":" + rs.getLong(2)).list();
        return new PersistenceSnapshot(count("federation"), count("competition"), count("competition_season"),
                count("season"), count("team"), count("constraint_rule"),
                count("fixture"), count("generation_version"), count("generated_match"), count("season_match_state"),
                count("match_change_audit"), ruleToggles, states);
    }

    private long count(String table) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table).query(Long.class).single();
    }

    private void startAndStopBackendProcess(Path log) throws Exception {
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        assertNotNull(classpath);
        assertFalse(classpath.isBlank());
        String executable = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java").toString();
        Process process = new ProcessBuilder(executable, "-cp", classpath, CalendarcalculatorApplication.class.getName(),
                "--spring.liquibase.drop-first=false",
                "--spring.liquibase.change-log=classpath:db/changelog/db.changelog-integration.yaml",
                "--spring.liquibase.contexts=legacy-fixtures", "--calendar.demo.enabled=false", "--server.port=0")
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
            boolean started = false;
            while (System.nanoTime() < deadline && process.isAlive()) {
                if (Files.exists(log) && Files.readString(log).contains("Started CalendarcalculatorApplication")) {
                    started = true;
                    break;
                }
                Thread.sleep(250);
            }
            assertTrue(started, "Backend subprocess did not start: "
                    + (Files.exists(log) ? Files.readString(log) : "no process output"));
        } finally {
            process.destroy();
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "Backend process did not stop gracefully");
        }
    }

    private record PersistenceSnapshot(long federations, long competitions, long competitionSeasons, long seasons,
            long teams, long restrictions, long fixtures, long versions, long generatedMatches, long matchStates,
            long audits, List<String> ruleToggles, List<String> statuses) {
    }
}
