package net.atos.mev.calendarcalculator.setup.jobs;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import net.atos.mev.calendarcalculator.setup.LegacyExcelBridge;
import net.atos.mev.calendarcalculator.setup.SeasonSetupRepository;
import net.atos.mev.calendarcalculator.setup.SetupDtos;
import net.atos.mev.calendarcalculator.setup.SetupExceptions;
import net.atos.mev.calendarcalculator.setup.SetupToEngineMapper;
import net.atos.mev.calendarcalculator.setup.SetupValidator;

/**
 * Requires an ephemeral migrated PostgreSQL/SQL Server database. Only the worker is
 * intercepted: transactions, SQL locks, CAS, validation and repository replacement are real.
 */
@SpringBootTest
class PlanningJobAtomicImportDatabaseIntegrationTest {
    @Autowired JdbcClient jdbc;
    @Autowired SeasonSetupRepository setups;
    @Autowired PlatformTransactionManager manager;
    @Autowired SetupValidator validator;
    @Autowired SetupToEngineMapper mapper;
    @MockitoSpyBean PlanningJobService jobs;
    @MockitoBean LegacyExcelBridge excel;
    private String competitionId;
    private String code;
    private String home;
    private String away;
    private long cs;
    private SetupDtos.SeasonSetup original;
    private final byte[] workbook = {1, 2, 3};
    private CountDownLatch ran;
    private AtomicReference<SetupDtos.SeasonSetup> workerSnapshot;
    private AtomicReference<SetupDtos.SeasonSetup> workerDatabaseSnapshot;
    private AtomicReference<Throwable> workerError;
    private static final String SEASON = "2098-99";
    private boolean createdSeason;

    @BeforeEach
    void initialize() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
        competitionId = "ATOMIC_" + suffix;
        code = "T" + suffix;
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
                """).param("c", competitionId).param("s", SEASON).param("start", LocalDate.of(2098, 7, 1))
                .param("end", LocalDate.of(2099, 6, 30)).param("second", LocalDate.of(2099, 1, 10)).update();
        cs = setups.competitionSeasonId(competitionId, SEASON);
        var general = new SetupDtos.General(LocalDate.of(2098, 7, 1), LocalDate.of(2099, 6, 30),
                LocalDate.of(2098, 9, 20), LocalDate.of(2099, 1, 10), 2, 0, 0, false, 1, 1, 2, "sat-sun");
        var draft = new SetupDtos.SeasonSetup(competitionId, code, SEASON, general, List.of("18:00"), null, null,
                List.of(new SetupDtos.Team(home, "Home", null, false, false), new SetupDtos.Team(away, "Away", null, false, false)),
                List.of(new SetupDtos.Round(1, null, 1, List.of(LocalDate.of(2098, 9, 20))),
                        new SetupDtos.Round(2, null, 2, List.of(LocalDate.of(2099, 1, 10)))),
                List.of(new SetupDtos.Fixture(null, 1, home, away, false, null, null),
                        new SetupDtos.Fixture(null, 2, away, home, false, null, null)),
                List.of(), List.of(), Map.of("CP", 2), List.of(), List.of(), List.of(), null, false,
                new SetupDtos.EngineSettings(1, 1, "0", "", List.of(new SetupDtos.AssignGroup(1, home + "," + away, 1)), Map.of()),
                0, null);
        setups.replace(draft);
        original = setups.load(competitionId, SEASON);
        assertThat(SetupValidator.hasErrors(validator.validate(original, 2, true))).isFalse();
        ran = new CountDownLatch(1);
        workerSnapshot = new AtomicReference<>();
        workerDatabaseSnapshot = new AtomicReference<>();
        workerError = new AtomicReference<>();
        doAnswer(call -> {
            try {
                workerSnapshot.set(call.getArgument(2));
                workerDatabaseSnapshot.set(setups.load(competitionId, SEASON));
                UUID id = call.getArgument(0);
                assertThat(jobs.job(id).status()).isEqualTo("QUEUED");
            } catch (AssertionError | RuntimeException e) {
                workerError.set(e);
            } finally {
                ran.countDown();
            }
            return null;
        }).when(jobs).run(any(UUID.class), anyLong(), any(), nullable(List.class), anyInt(), anyInt(), anyBoolean());
        when(excel.importPreview(any(), any(byte[].class))).thenAnswer(call -> imported(call.getArgument(0)));
    }

    @AfterEach
    void cleanup() {
        if (cs == 0) return;
        jdbc.sql("DELETE FROM planning_job_log WHERE job_id IN (SELECT id FROM planning_job WHERE competition_season_id = :cs)")
                .param("cs", cs).update();
        jdbc.sql("DELETE FROM planning_job WHERE competition_season_id = :cs").param("cs", cs).update();
        jdbc.sql("DELETE FROM draw_batch_league WHERE batch_id IN (SELECT id FROM draw_batch WHERE competition_season_id = :cs)")
                .param("cs", cs).update();
        jdbc.sql("DELETE FROM draw_batch WHERE competition_season_id = :cs").param("cs", cs).update();
        jdbc.sql("DELETE FROM round_date WHERE league_round_id IN (SELECT id FROM league_round WHERE competition_season_id = :cs)")
                .param("cs", cs).update();
        for (String table : List.of("fixture", "timeslot", "competition_season_team", "league_round", "external_commitment",
                "blackout", "rest_rule", "team_pair_rule", "constraint_rule", "assign_group", "season_export_colour")) {
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

    @Test
    void importAndQueuedJobCommitTogetherAndWorkerOnlySeesCommittedData() throws Exception {
        assertThat(validator.validate(imported(original), 2, true))
                .filteredOn(issue -> "error".equals(issue.level()))
                .as("Imported fixture validation issues").isEmpty();
        var job = transaction().execute(status -> {
            var queued = jobs.startWithImport(original, workbook, 2);
            assertThat(queued.status()).isEqualTo("QUEUED");
            assertThat(jobCount()).isEqualTo(1);
            assertThat(setups.load(competitionId, SEASON).rowVersion()).isEqualTo(original.rowVersion() + 1);
            assertThat(ran.getCount()).isEqualTo(1);
            return queued;
        });
        assertWorker();
        assertThat(workerSnapshot.get().rowVersion()).isEqualTo(original.rowVersion() + 1);
        assertThat(workerDatabaseSnapshot.get()).isEqualTo(setups.load(competitionId, SEASON));
        assertThat(workerSnapshot.get().external()).hasSize(1);
        assertThat(jobs.job(UUID.fromString(job.id())).status()).isEqualTo("QUEUED");
        jobs.cancel(UUID.fromString(job.id()));
        assertThat(jobs.job(UUID.fromString(job.id())).status()).isEqualTo("CANCELLED");
        assertThat(jobs.logs(UUID.fromString(job.id()), 0)).isEmpty();
    }

    @Test
    void outerRollbackLeavesNeitherImportNorQueuedJobAndNeverDispatches() throws Exception {
        transaction().executeWithoutResult(status -> {
            jobs.startWithImport(original, workbook, 2);
            assertThat(jobCount()).isEqualTo(1);
            assertThat(ran.getCount()).isEqualTo(1);
            status.setRollbackOnly();
        });
        assertUnchangedAndNotDispatched();
    }

    @Test
    void anExplicitLegacyWorkbookIsActuallyParsedBeforeAtomicPersistence() throws Exception {
        var bridge = new LegacyExcelBridge(mapper);
        byte[] bytes = bridge.export(original, List.of(), null);
        when(excel.importPreview(any(), any(byte[].class)))
                .thenAnswer(call -> bridge.importPreview(call.getArgument(0), call.getArgument(1)));
        jobs.startWithImport(original, bytes, 2);
        assertWorker();
        verify(excel).importPreview(any(), eq(bytes));
        assertThat(setups.load(competitionId, SEASON).rowVersion()).isEqualTo(original.rowVersion() + 1);
        assertThat(jobCount()).isEqualTo(1);
    }

    @Test
    void failureAfterReplacementRollsBackBothTheImportAndInsertedJob() throws Exception {
        doAnswer(call -> { throw new DuplicateKeyException("Simulated queue read failure after insertion"); })
                .when(jobs).job(any(UUID.class));
        assertThatThrownBy(() -> jobs.startWithImport(original, workbook, 2)).isInstanceOf(DuplicateKeyException.class);
        assertUnchangedAndNotDispatched();
    }

    @Test
    void staleCasCannotOverwriteAChangedP1OrP2Snapshot() throws Exception {
        jdbc.sql("UPDATE competition_season SET row_version = row_version + 1 WHERE id = :cs").param("cs", cs).update();
        var changed = setups.load(competitionId, SEASON);
        assertThatThrownBy(() -> jobs.startWithImport(original, workbook, 2)).isInstanceOf(SetupExceptions.Conflict.class);
        verifyNoInteractions(excel);
        assertThat(setups.load(competitionId, SEASON)).isEqualTo(changed);
        assertThat(jobCount()).isZero();
        assertThat(ran.await(200, TimeUnit.MILLISECONDS)).isFalse();
    }

    @Test
    void runningP1BatchBlocksNormalAndImportStartsWithoutSavingAnything() throws Exception {
        String draw = UUID.randomUUID().toString();
        jdbc.sql("""
                INSERT INTO draw_batch (id, competition_season_id, status, requested, generated_count, tries, params)
                VALUES (:id, :cs, 'RUNNING', 1, 0, 0, '')
                """).param("id", draw).param("cs", cs).update();
        assertThatThrownBy(() -> jobs.start(competitionId, SEASON, 2)).isInstanceOf(SetupExceptions.Conflict.class);
        assertThatThrownBy(() -> jobs.startWithImport(original, workbook, 2)).isInstanceOf(SetupExceptions.Conflict.class);
        verifyNoInteractions(excel);
        assertUnchangedAndNotDispatched();
    }

    @Test
    void queuedP2JobBlocksImportBeforeItChangesConfiguration() throws Exception {
        jdbc.sql("INSERT INTO planning_job (id, competition_season_id, status, last_round_to_assign) VALUES (:id, :cs, 'QUEUED', 2)")
                .param("id", UUID.randomUUID()).param("cs", cs).update();
        assertThatThrownBy(() -> jobs.startWithImport(original, workbook, 2)).isInstanceOf(SetupExceptions.Conflict.class);
        verifyNoInteractions(excel);
        assertThat(setups.load(competitionId, SEASON)).isEqualTo(original);
        assertThat(jobCount()).isEqualTo(1);
        assertThat(ran.await(200, TimeUnit.MILLISECONDS)).isFalse();
    }

    @Test
    void lockedAndConfirmedFixturesCannotBeClearedByAnImport() throws Exception {
        for (boolean locked : List.of(false, true)) {
            jdbc.sql("UPDATE fixture SET locked = :locked, play_date = :date, play_hour = :hour WHERE competition_season_id = :cs AND round_no = 1")
                    .param("locked", locked).param("date", LocalDate.of(2098, 9, 20))
                    .param("hour", locked ? null : "18:00").param("cs", cs).update();
            jdbc.sql("UPDATE season_match_state SET play_date = :date, play_hour = :hour WHERE competition_season_id = :cs AND round_no = 1")
                    .param("date", LocalDate.of(2098, 9, 20)).param("hour", locked ? null : "18:00").param("cs", cs).update();
            var before = setups.load(competitionId, SEASON);
            when(excel.importPreview(any(), any(byte[].class))).thenReturn(imported(original));
            assertThatThrownBy(() -> jobs.startWithImport(before, workbook, 2))
                    .isInstanceOf(SetupExceptions.Conflict.class).hasMessageContaining("locked or confirmed");
            assertThat(setups.load(competitionId, SEASON)).isEqualTo(before);
            assertThat(jobCount()).isZero();
        }
        assertThat(ran.await(200, TimeUnit.MILLISECONDS)).isFalse();
    }

    @Test
    void invalidImportedConfigurationNeverWritesOrQueues() throws Exception {
        var broken = new SetupDtos.SeasonSetup(original.competitionId(), original.code(), original.season(), original.general(),
                List.of(), original.ramadan(), original.noLightSlot(), original.teams(), original.rounds(), original.fixtures(),
                original.external(), original.blackouts(), original.restDays(), original.notHomeSameDay(), original.derbies(), original.rules(),
                original.dependsOn(), original.includeDependency(), original.engine(), original.rowVersion(), original.updatedAt());
        when(excel.importPreview(any(), any(byte[].class))).thenReturn(broken);
        assertThatThrownBy(() -> jobs.startWithImport(original, workbook, 2)).isInstanceOf(SetupExceptions.Invalid.class);
        assertUnchangedAndNotDispatched();
    }

    @Test
    void malformedWorkbookRollsBackBeforeAnyJobCanBeQueued() throws Exception {
        when(excel.importPreview(any(), any(byte[].class))).thenThrow(new IllegalArgumentException("Invalid workbook"));
        assertThatThrownBy(() -> jobs.startWithImport(original, workbook, 2)).isInstanceOf(IllegalArgumentException.class);
        assertUnchangedAndNotDispatched();
    }

    @Test
    void workbookCannotReplaceTheP1PairingsEvenIfTheNewDrawWouldValidate() throws Exception {
        var fixtures = original.fixtures().stream().map(f ->
                new SetupDtos.Fixture(f.id(), f.round(), f.away(), f.home(), f.locked(), f.date(), f.hour())).toList();
        when(excel.importPreview(any(), any(byte[].class))).thenReturn(copy(original, fixtures, original.external()));
        assertThatThrownBy(() -> jobs.startWithImport(original, workbook, 2))
                .isInstanceOf(SetupExceptions.Conflict.class).hasMessageContaining("P1 draw");
        assertUnchangedAndNotDispatched();
    }

    @Test
    void normalStartAlsoWaitsForCommitAndRespectsOuterRollback() throws Exception {
        transaction().executeWithoutResult(status -> {
            jobs.start(competitionId, SEASON, 2);
            assertThat(ran.getCount()).isEqualTo(1);
            status.setRollbackOnly();
        });
        assertUnchangedAndNotDispatched();
        jobs.start(competitionId, SEASON, 2);
        assertWorker();
        assertThat(workerDatabaseSnapshot.get()).isEqualTo(original);
    }

    @Test
    void competingNormalStartCannotRejectAnAlreadySavedImport() throws Exception {
        var importing = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(excel.importPreview(any(), any(byte[].class))).thenAnswer(call -> {
            importing.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return imported(call.getArgument(0));
        });
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> jobs.startWithImport(original, workbook, 2));
            assertThat(importing.await(10, TimeUnit.SECONDS)).isTrue();
            var secondEntered = new CountDownLatch(1);
            var second = pool.submit(() -> {
                secondEntered.countDown();
                try {
                    jobs.start(competitionId, SEASON, 2);
                    return false;
                } catch (SetupExceptions.Conflict | ConcurrencyFailureException expected) {
                    return true;
                }
            });
            assertThat(secondEntered.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS).status()).isEqualTo("QUEUED");
            assertThat(second.get(15, TimeUnit.SECONDS)).isTrue();
            assertWorker();
            assertThat(setups.load(competitionId, SEASON).rowVersion()).isEqualTo(original.rowVersion() + 1);
            assertThat(jobCount()).isEqualTo(1);
        } finally {
            release.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void assertWorker() throws InterruptedException {
        assertThat(ran.await(10, TimeUnit.SECONDS)).isTrue();
        assertThat(workerError.get()).isNull();
    }

    private void assertUnchangedAndNotDispatched() throws InterruptedException {
        assertThat(setups.load(competitionId, SEASON)).isEqualTo(original);
        assertThat(jobCount()).isZero();
        assertThat(ran.await(200, TimeUnit.MILLISECONDS)).isFalse();
    }

    private int jobCount() {
        return jdbc.sql("SELECT COUNT(*) FROM planning_job WHERE competition_season_id = :cs").param("cs", cs).query(Integer.class).single();
    }

    private TransactionTemplate transaction() {
        var transaction = new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE);
        return transaction;
    }

    private SetupDtos.SeasonSetup imported(SetupDtos.SeasonSetup setup) {
        return copy(setup, setup.fixtures(), List.of(new SetupDtos.ExternalMatch(null, "CP", "1",
                LocalDate.of(2098, 8, 10), "18:00", home, away)));
    }

    private SetupDtos.SeasonSetup copy(SetupDtos.SeasonSetup s, List<SetupDtos.Fixture> fixtures, List<SetupDtos.ExternalMatch> external) {
        return new SetupDtos.SeasonSetup(s.competitionId(), s.code(), s.season(), s.general(), s.timeslots(), s.ramadan(), s.noLightSlot(),
                s.teams(), s.rounds(), fixtures, external, s.blackouts(), s.restDays(), s.notHomeSameDay(), s.derbies(), s.rules(),
                s.dependsOn(), s.includeDependency(), s.engine(), s.rowVersion(), s.updatedAt());
    }
}
