package net.atos.mev.calendarcalculator.setup.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import net.atos.mev.calendarcalculator.setup.GenerationRepository;
import net.atos.mev.calendarcalculator.setup.SeasonSetupRepository;
import net.atos.mev.calendarcalculator.setup.SetupDtos;
import net.atos.mev.calendarcalculator.setup.SetupToEngineMapper;

/**
 * Whole-job failure behaviour on a real migrated database (PostgreSQL / SQL Server).
 * A job is NOT a single transaction: the engine runs outside any transaction and only the final
 * publication (match states + audit + version + files) is atomic. The compensation for a failed job
 * is therefore: nothing published, the job FAILED with a message, protected matches untouched,
 * the slot free for a new job, and orphan jobs closed on restart.
 */
@SpringBootTest
class PlanningJobFailureDatabaseIntegrationTest {
    private static final String SEASON = "2097-98";

    @Autowired JdbcClient jdbc;
    @Autowired SeasonSetupRepository setups;
    @Autowired PlanningJobService jobs;
    @MockitoSpyBean GenerationRepository generations;
    @MockitoSpyBean SetupToEngineMapper mapper;

    private String competitionId;
    private String code;
    private String home;
    private String away;
    private long cs;
    private boolean createdSeason;

    @BeforeEach
    void initialize() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        competitionId = "JOBFAIL_" + suffix;
        code = "J" + suffix;
        home = "H" + suffix;
        away = "A" + suffix;
        createdSeason = jdbc.sql("SELECT COUNT(*) FROM season WHERE id = :s").param("s", SEASON).query(Integer.class).single() == 0;
        if (createdSeason) {
            jdbc.sql("INSERT INTO season (id) VALUES (:s)").param("s", SEASON).update();
        }
        jdbc.sql("INSERT INTO competition (id, code, name, storage_dir, output_suffix, sort_order) VALUES (:c, :code, :c, :c, '', 9999)")
                .param("c", competitionId).param("code", code).update();
        jdbc.sql("""
                INSERT INTO competition_season (competition_id, season_id, season_start, season_end, start_second_leg,
                     max_matches_per_day, max_days_advance_match, max_days_postpone_match, round_calculation_order)
                VALUES (:c, :s, :start, :end, :second, 2, 0, 0, '')
                """).param("c", competitionId).param("s", SEASON).param("start", LocalDate.of(2097, 7, 1))
                .param("end", LocalDate.of(2098, 6, 30)).param("second", LocalDate.of(2098, 1, 10)).update();
        cs = setups.competitionSeasonId(competitionId, SEASON);
        var general = new SetupDtos.General(LocalDate.of(2097, 7, 1), LocalDate.of(2098, 6, 30),
                LocalDate.of(2097, 9, 20), LocalDate.of(2098, 1, 10), 2, 0, 0, false, 1, 1, 2, "sat-sun");
        setups.replace(new SetupDtos.SeasonSetup(competitionId, code, SEASON, general, List.of("18:00"), null, null,
                List.of(new SetupDtos.Team(home, "Home", null, false, false), new SetupDtos.Team(away, "Away", null, false, false)),
                List.of(new SetupDtos.Round(1, null, 1, List.of(LocalDate.of(2097, 9, 20))),
                        new SetupDtos.Round(2, null, 2, List.of(LocalDate.of(2098, 1, 10)))),
                List.of(new SetupDtos.Fixture(null, 1, home, away, false, null, null),
                        new SetupDtos.Fixture(null, 2, away, home, false, null, null)),
                List.of(), List.of(), Map.of("CP", 2), List.of(), List.of(), List.of(), null, false,
                new SetupDtos.EngineSettings(1, 1, "0", "", List.of(new SetupDtos.AssignGroup(1, home + "," + away, 1)), Map.of()),
                0, null));
    }

    @AfterEach
    void cleanup() {
        reset(generations, mapper);
        if (cs == 0) {
            return;
        }
        jdbc.sql("UPDATE planning_job SET version_id = NULL WHERE competition_season_id = :cs").param("cs", cs).update();
        jdbc.sql("DELETE FROM planning_job_log WHERE job_id IN (SELECT id FROM planning_job WHERE competition_season_id = :cs)")
                .param("cs", cs).update();
        jdbc.sql("DELETE FROM planning_job WHERE competition_season_id = :cs").param("cs", cs).update();
        jdbc.sql("DELETE FROM match_change_audit WHERE competition_season_id = :cs").param("cs", cs).update();
        jdbc.sql("DELETE FROM season_match_state WHERE competition_season_id = :cs").param("cs", cs).update();
        jdbc.sql("DELETE FROM round_date WHERE league_round_id IN (SELECT id FROM league_round WHERE competition_season_id = :cs)")
                .param("cs", cs).update();
        for (String table : List.of("generation_version", "fixture", "timeslot", "competition_season_team", "league_round",
                "external_commitment", "blackout", "rest_rule", "team_pair_rule", "constraint_rule", "assign_group",
                "season_export_colour")) {
            jdbc.sql("DELETE FROM " + table + " WHERE competition_season_id = :cs").param("cs", cs).update();
        }
        jdbc.sql("DELETE FROM competition_season WHERE id = :cs").param("cs", cs).update();
        jdbc.sql("DELETE FROM competition WHERE id = :c").param("c", competitionId).update();
        jdbc.sql("DELETE FROM team WHERE code IN (:codes)").param("codes", List.of(home, away)).update();
        if (createdSeason) {
            jdbc.sql("DELETE FROM season WHERE id = :s AND NOT EXISTS (SELECT 1 FROM competition_season WHERE season_id = :s)")
                    .param("s", SEASON).update();
        }
    }

    private SetupDtos.PlanningJob runToEnd() throws InterruptedException {
        var queued = jobs.start(competitionId, SEASON, 2);
        UUID id = UUID.fromString(queued.id());
        for (int i = 0; i < 600; i++) {
            var job = jobs.job(id);
            if (!"QUEUED".equals(job.status()) && !"RUNNING".equals(job.status())) {
                return job;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Job did not finish");
    }

    private int count(String table) {
        return jdbc.sql("SELECT COUNT(*) FROM " + table + " WHERE competition_season_id = :cs").param("cs", cs)
                .query(Integer.class).single();
    }

    private List<String> matchStates() {
        return jdbc.sql("""
                SELECT CONCAT(round_no, '|', home, '|', away, '|', status, '|', play_date, '|', play_hour)
                  FROM season_match_state WHERE competition_season_id = :cs ORDER BY round_no, home, away""")
                .param("cs", cs).query(String.class).list();
    }

    @Test
    void failureWhilePublishingLeavesNothingPartialAndTheJobRecoverable() throws Exception {
        doThrow(new IllegalStateException("simulated publication failure")).when(generations)
                .createVersion(anyLong(), anyInt(), anyInt(), any(), any(), any(), any());

        var failed = runToEnd();

        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.errorMessage()).contains("simulated publication failure");
        assertThat(failed.versionId()).isNull();
        // saveGenerated had already inserted states and audit rows inside its transaction: all rolled back
        assertThat(matchStates()).hasSize(2).allMatch(s -> s.contains("PENDIENTE") && s.endsWith("||"));
        assertThat(count("generation_version")).isZero();
        assertThat(count("match_change_audit")).isZero();
        assertThat(jobs.logs(UUID.fromString(failed.id()), 0)).anyMatch(l -> l.message().contains("Planning failed"));

        reset(generations);
        doCallRealMethod().when(generations).createVersion(anyLong(), anyInt(), anyInt(), any(), any(), any(), any());
        var recovered = runToEnd();
        assertThat(recovered.status()).isEqualTo("SUCCEEDED");
        assertThat(count("generation_version")).isEqualTo(1);
        assertThat(matchStates()).hasSize(2).allMatch(s -> s.contains("PLANIFICADA"));
    }

    @Test
    void failureOfAnAlreadyPlannedSeasonDoesNotTouchProtectedOrPlannedMatches() throws Exception {
        assertThat(runToEnd().status()).isEqualTo("SUCCEEDED");
        jdbc.sql("UPDATE season_match_state SET status = 'ENTREGADA' WHERE competition_season_id = :cs AND round_no = 1")
                .param("cs", cs).update();
        var statesBefore = matchStates();
        int versionsBefore = count("generation_version");
        int auditBefore = count("match_change_audit");
        assertThat(statesBefore).anyMatch(s -> s.startsWith("1|") && s.contains("ENTREGADA"));

        doThrow(new IllegalStateException("simulated engine failure")).when(mapper)
                .map(any(), any(), any(), anyInt(), anyInt());
        var failed = runToEnd();

        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.errorMessage()).contains("simulated engine failure");
        assertThat(matchStates()).isEqualTo(statesBefore);
        assertThat(count("generation_version")).isEqualTo(versionsBefore);
        assertThat(count("match_change_audit")).isEqualTo(auditBefore);

        reset(mapper);
        var recovered = runToEnd();
        assertThat(recovered.status()).isEqualTo("SUCCEEDED");
        assertThat(matchStates()).isEqualTo(statesBefore);
    }

    @Test
    void anOrphanRunningJobIsClosedAsFailedOnRestartAndFreesTheSlot() throws Exception {
        UUID orphan = UUID.randomUUID();
        jdbc.sql("""
                INSERT INTO planning_job (id, competition_season_id, status, first_round_to_assign, last_round_to_assign, started_at)
                VALUES (:id, :cs, 'RUNNING', 1, 2, CURRENT_TIMESTAMP)""").param("id", orphan).param("cs", cs).update();
        org.junit.jupiter.api.Assertions.assertThrows(net.atos.mev.calendarcalculator.setup.SetupExceptions.Conflict.class,
                () -> jobs.start(competitionId, SEASON, 2));

        jobs.failOrphans();

        var closed = jobs.job(orphan);
        assertThat(closed.status()).isEqualTo("FAILED");
        assertThat(closed.errorMessage()).contains("restarted");
        assertThat(count("generation_version")).isZero();
        assertThat(runToEnd().status()).isEqualTo("SUCCEEDED");
    }
}
