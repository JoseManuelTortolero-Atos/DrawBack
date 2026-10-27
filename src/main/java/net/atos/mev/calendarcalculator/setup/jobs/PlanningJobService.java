package net.atos.mev.calendarcalculator.setup.jobs;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import tools.jackson.databind.json.JsonMapper;

import ch.qos.logback.classic.LoggerContext;
import jakarta.annotation.PreDestroy;
import net.atos.mev.calendarcalculator.schedules.LegacyResultFiles;
import net.atos.mev.calendarcalculator.schedules.Matchday;
import net.atos.mev.calendarcalculator.schedules.OtherCompetitionDay;
import net.atos.mev.calendarcalculator.schedules.PlanningCancelledException;
import net.atos.mev.calendarcalculator.schedules.SchCalendar;
import net.atos.mev.calendarcalculator.schedules.SchMatch;
import net.atos.mev.calendarcalculator.schedules.ScheduleMoroccoAlg;
import net.atos.mev.calendarcalculator.schedules.SetupCalendarBuilder;
import net.atos.mev.calendarcalculator.setup.GenerationRepository;
import net.atos.mev.calendarcalculator.setup.LegacyExcelBridge;
import net.atos.mev.calendarcalculator.setup.MatchStateService;
import net.atos.mev.calendarcalculator.setup.SeasonSetupRepository;
import net.atos.mev.calendarcalculator.setup.SetupDtos;
import net.atos.mev.calendarcalculator.setup.rules.RuleScopeService;
import net.atos.mev.calendarcalculator.setup.SetupExceptions;
import net.atos.mev.calendarcalculator.setup.SetupToEngineMapper;
import net.atos.mev.calendarcalculator.setup.SetupValidator;

/**
 * Jobs de planificación asíncronos: valida, lanza el motor en un pool propio, guarda la versión
 * (partidos + ficheros) en BD, permite cancelar y corta por timeout (por defecto PT30M).
 * Un único job activo por competición y temporada (índice único parcial en planning_job).
 */
@Service
public class PlanningJobService {

    private static final Logger log = LoggerFactory.getLogger(PlanningJobService.class);

    private final JdbcClient jdbc;
    private final SeasonSetupRepository setups;
    private final GenerationRepository generations;
    private final SetupToEngineMapper mapper;
    private final SetupValidator validator;
    private final JsonMapper json;
    private final Duration timeout;
    private final ExecutorService pool;
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
    private final Map<UUID, ScheduleMoroccoAlg> running = new ConcurrentHashMap<>();
    private final Map<UUID, Future<?>> futures = new ConcurrentHashMap<>();
    private final JobLogAppender appender;
    private final RuleScopeService scopedRules;
    private final LegacyExcelBridge excel;
    private final MatchStateService matchStates;

    public PlanningJobService(JdbcClient jdbc, SeasonSetupRepository setups, GenerationRepository generations, SetupToEngineMapper mapper,
            SetupValidator validator, JsonMapper json, RuleScopeService scopedRules, LegacyExcelBridge excel, MatchStateService matchStates,
            @Value("${calendar.planning.timeout:PT30M}") Duration timeout,
            @Value("${calendar.planning.threads:2}") int threads) {
        this.jdbc = jdbc;
        this.setups = setups;
        this.generations = generations;
        this.mapper = mapper;
        this.validator = validator;
        this.json = json;
        this.scopedRules = scopedRules;
        this.excel = excel;
        this.matchStates = matchStates;
        this.timeout = timeout;
        this.pool = Executors.newFixedThreadPool(threads);
        this.appender = new JobLogAppender(jdbc);
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext ctx) {
            appender.setContext(ctx);
            appender.start();
            ctx.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).addAppender(appender);
        }
    }

    /** Jobs que quedaron QUEUED/RUNNING al parar la aplicación → FAILED. */
    @EventListener(ApplicationReadyEvent.class)
    public void failOrphans() {
        int n = jdbc.sql("""
                UPDATE planning_job SET status = 'FAILED', finished_at = CURRENT_TIMESTAMP, error_message = 'Interrupted: the application was restarted'
                 WHERE status IN ('QUEUED','RUNNING')""").update();
        if (n > 0) {
            log.warn("{} orphan planning job(s) marked as FAILED", n);
        }
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public SetupDtos.PlanningJob start(String competitionId, String season, Integer lastRoundToAssign) {
        return start(competitionId, season, null, lastRoundToAssign);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public SetupDtos.PlanningJob start(String competitionId, String season, Integer firstRoundToAssign,
            Integer lastRoundToAssign) {
        long cs = setups.competitionSeasonId(competitionId, season);
        lockForStart(cs, null);
        return enqueue(cs, setups.load(competitionId, season), firstRoundToAssign, lastRoundToAssign);
    }

    /**
     * The editable import and its QUEUED job commit together. The expected snapshot is
     * used only for identity/CAS; effective inherited rules must never be persisted.
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public SetupDtos.PlanningJob startWithImport(SetupDtos.SeasonSetup expected, byte[] workbook, Integer lastRoundToAssign) {
        return startWithImport(expected, workbook, null, lastRoundToAssign);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public SetupDtos.PlanningJob startWithImport(SetupDtos.SeasonSetup expected, byte[] workbook,
            Integer firstRoundToAssign, Integer lastRoundToAssign) {
        if (workbook == null || workbook.length == 0) {
            throw new IllegalArgumentException("Excel file is required");
        }
        long cs = setups.competitionSeasonId(expected.competitionId(), expected.season());
        lockForStart(cs, expected.rowVersion());
        var current = setups.load(expected.competitionId(), expected.season());
        var imported = excel.importPreview(current, workbook);
        if (!Objects.equals(current.competitionId(), imported.competitionId())
                || !Objects.equals(current.season(), imported.season()) || current.rowVersion() != imported.rowVersion()) {
            throw new IllegalArgumentException("Excel import changed the configuration identity or rowVersion");
        }
        var originalDraw = current.fixtures().stream().collect(java.util.stream.Collectors.groupingBy(
                f -> new FixtureKey(f.round(), f.home(), f.away()), java.util.stream.Collectors.counting()));
        var importedDraw = imported.fixtures().stream().collect(java.util.stream.Collectors.groupingBy(
                f -> new FixtureKey(f.round(), f.home(), f.away()), java.util.stream.Collectors.counting()));
        if (!originalDraw.equals(importedDraw)) {
            throw new SetupExceptions.Conflict("Excel import cannot change the P1 draw. Only fixture dates and times can be imported.");
        }
        for (var fixture : current.fixtures()) {
            boolean confirmed = fixture.date() != null && fixture.hour() != null && !fixture.hour().isBlank();
            if ((fixture.locked() || confirmed) && imported.fixtures().stream().noneMatch(f ->
                    fixture.round() == f.round() && Objects.equals(fixture.home(), f.home())
                            && Objects.equals(fixture.away(), f.away()) && (!fixture.locked() || f.locked())
                            && Objects.equals(fixture.date(), f.date()) && Objects.equals(fixture.hour(), f.hour()))) {
                throw new SetupExceptions.Conflict("Excel would change a locked or confirmed fixture: "
                        + fixture.home() + " - " + fixture.away());
            }
        }
        validate(imported, firstRoundToAssign, lastRoundToAssign);
        setups.replace(imported);
        // Insertion failure rolls the replacement back; no worker can observe uncommitted data.
        return enqueue(cs, setups.load(current.competitionId(), current.season()), firstRoundToAssign, lastRoundToAssign);
    }

    private void lockForStart(long cs, Integer expectedVersion) {
        String cas = expectedVersion == null ? "" : " AND row_version = :version";
        // Lock the parent before reading child job tables, in the same order for both entries.
        var update = jdbc.sql("UPDATE competition_season SET row_version = row_version WHERE id = :cs" + cas).param("cs", cs);
        if (expectedVersion != null) {
            update = update.param("version", expectedVersion);
        }
        if (update.update() != 1) {
            throw new SetupExceptions.Conflict("Configuration changed. Reload before importing the Excel.");
        }
        int planning = jdbc.sql("""
                SELECT COUNT(*) FROM planning_job
                 WHERE competition_season_id = :cs AND status IN ('QUEUED', 'RUNNING')
                """).param("cs", cs).query(Integer.class).single();
        int draw = jdbc.sql("""
                SELECT COUNT(*) FROM draw_batch b
                 WHERE b.status = 'RUNNING' AND
                   (b.competition_season_id = :cs OR EXISTS
                     (SELECT 1 FROM draw_batch_league l WHERE l.batch_id = b.id AND l.competition_season_id = :cs))
                """).param("cs", cs).query(Integer.class).single();
        if (planning > 0 || draw > 0) {
            throw new SetupExceptions.Conflict("A P1/P2 job is already active. Wait for it to finish.");
        }
    }

    private record FixtureKey(int round, String home, String away) { }

    private record Validated(SetupDtos.SeasonSetup setup, List<SetupDtos.GeneratedMatch> dependency,
            int first, int last, boolean rangeRequested) { }

    private Validated validate(SetupDtos.SeasonSetup snapshot, Integer lastRoundToAssign) {
        return validate(snapshot, null, lastRoundToAssign);
    }

    private Validated validate(SetupDtos.SeasonSetup snapshot, Integer firstRoundToAssign, Integer lastRoundToAssign) {
        // reglas propias de la liga + restricciones asignadas por federación / grupo / liga / partido
        SetupDtos.SeasonSetup setup = scopedRules.withScopedRules(snapshot);
        int first = firstRoundToAssign == null ? 1 : firstRoundToAssign;
        int last = lastRoundToAssign != null ? lastRoundToAssign : setup.general().lastRoundToAssign();
        List<SetupDtos.GeneratedMatch> dep = dependency(setup);
        boolean dependencyApproved = setup.dependsOn() == null || !setup.includeDependency() || dep != null;
        List<SetupDtos.ValidationIssue> issues = firstRoundToAssign == null
                ? validator.validate(setup, last, dependencyApproved)
                : validator.validate(setup, firstRoundToAssign, last, dependencyApproved);
        if (SetupValidator.hasErrors(issues)) {
            throw new SetupExceptions.Invalid("The configuration has errors", issues);
        }
        return new Validated(setup, dep, first, last, firstRoundToAssign != null);
    }

    private SetupDtos.PlanningJob enqueue(long cs, SetupDtos.SeasonSetup setup, Integer last) {
        return enqueue(cs, setup, null, last);
    }

    private SetupDtos.PlanningJob enqueue(long cs, SetupDtos.SeasonSetup setup, Integer first, Integer last) {
        return enqueueValidated(cs, validate(setup, first, last));
    }

    private SetupDtos.PlanningJob enqueueValidated(long cs, Validated validated) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Planning jobs must be started through a transactional service proxy");
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.sql("""
                    INSERT INTO planning_job (id, competition_season_id, status, first_round_to_assign, last_round_to_assign)
                    VALUES (:id, :cs, 'QUEUED', :first, :last)""")
                    .param("id", id).param("cs", cs).param("first", validated.first()).param("last", validated.last()).update();
        } catch (DuplicateKeyException e) {
            throw new SetupExceptions.Conflict("A planning job is already running for "
                    + validated.setup().competitionId() + " " + validated.setup().season());
        }
        var queued = job(id);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    return;
                }
                futures.put(id, pool.submit(() -> {
                    run(id, cs, validated.setup(), validated.dependency(),
                            validated.first(), validated.last(), validated.rangeRequested());
                }));
                watchdog.schedule(() -> timeout(id), timeout.toMillis(), TimeUnit.MILLISECONDS);
            }
        });
        return queued;
    }

    private List<SetupDtos.GeneratedMatch> dependency(SetupDtos.SeasonSetup setup) {
        if (setup.dependsOn() == null || !setup.includeDependency()) {
            return null;
        }
        return generations.approvedMatches(setup.dependsOn(), setup.season()).orElse(null);
    }

    void run(UUID id, long cs, SetupDtos.SeasonSetup setup, List<SetupDtos.GeneratedMatch> dep,
            int first, int last, boolean rangeRequested) {
        try {
            MDC.put(JobLogAppender.MDC_KEY, id.toString());
            boolean claimed = markRunning(id);
            if (!claimed) {
                return; // cancelado antes de empezar
            }
            log.info("Planning {} {} from round {} through {}", setup.competitionId(), setup.season(), first, last);
            String depCode = setup.dependsOn() == null ? null : setups.competitionCode(setup.dependsOn());
            if (dep != null) {
                log.info("Including {} matches of the approved calendar of {}", dep.size(), setup.dependsOn());
            }
            SetupDtos.SeasonSetup effectiveSetup = matchStates.protectFinalizedMatches(setup);
            if (rangeRequested) {
                effectiveSetup = resetUnprotectedRange(effectiveSetup, first, last);
            }
            SetupToEngineMapper.Planning planning = mapper.map(effectiveSetup, dep == null ? List.of() : dep,
                    depCode, first, last);
            SetupCalendarBuilder.Built built = SetupCalendarBuilder.build(planning.input(),
                    net.atos.mev.calendarcalculator.setup.P2Settings.colours(effectiveSetup));
            ScheduleMoroccoAlg alg = new ScheduleMoroccoAlg(built.environment(), planning.parameters());
            alg.setRoundListener((g, r, cal) -> log.info("Days assigned: group {}, round step {}", g + 1, r));
            running.put(id, alg);
            if (cancelRequested(id)) {
                alg.cancel();
            }
            SchCalendar result = alg.plan(built.calendar());
            log.info("Times assigned. Saving version");
            Map<String, byte[]> files = LegacyResultFiles.renderAll(result, built.environment(), planning.parameters());
            SetupDtos.Version v = matchStates.saveGenerated(cs, effectiveSetup.code(), first, last, id,
                    json.writeValueAsString(effectiveSetup), matches(result), files);
            finish(id, "SUCCEEDED", null, v.id());
            log.info("Version v{} created ({} files)", v.versionNo(), files.size());
        } catch (PlanningCancelledException e) {
            log.warn("Planning cancelled");
            finish(id, "CANCELLED", e.getMessage(), null);
        } catch (Exception e) {
            log.error("Planning failed: {}", e.getMessage());
            finish(id, "FAILED", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(), null);
        } finally {
            running.remove(id);
            futures.remove(id);
            appender.forget(id);
            MDC.remove(JobLogAppender.MDC_KEY);
        }
    }

    private static SetupDtos.SeasonSetup resetUnprotectedRange(SetupDtos.SeasonSetup setup, int first, int last) {
        List<SetupDtos.Fixture> fixtures = setup.fixtures().stream().map(fixture ->
                fixture.round() >= first && fixture.round() <= last && !fixture.locked()
                        ? new SetupDtos.Fixture(fixture.id(), fixture.round(), fixture.home(), fixture.away(),
                                false, null, null)
                        : fixture).toList();
        return new SetupDtos.SeasonSetup(setup.competitionId(), setup.code(), setup.season(), setup.general(), setup.timeslots(),
                setup.ramadan(), setup.noLightSlot(), setup.teams(), setup.rounds(), fixtures, setup.external(),
                setup.blackouts(), setup.restDays(), setup.notHomeSameDay(), setup.derbies(), setup.rules(),
                setup.dependsOn(), setup.includeDependency(), setup.engine(), setup.rowVersion(), setup.updatedAt());
    }

    /** Partidos de la liga y de otras competiciones, en el orden del calendario. */
    static List<SetupDtos.GeneratedMatch> matches(SchCalendar cal) {
        List<SetupDtos.GeneratedMatch> out = new ArrayList<>();
        for (Matchday md : cal.getMatchdays()) {
            for (SchMatch m : md.getMatches()) {
                out.add(new SetupDtos.GeneratedMatch(m.getCompetition(), m.getCompetitionRound(), m.getDate(), m.getTime(),
                        m.getTheMatch().getTeam1().getCode(), m.getTheMatch().getTeam2().getCode()));
            }
            for (OtherCompetitionDay o : md.getOtherMatches()) {
                String home = o.hasMatchDetails() ? o.getTeamHome() : String.join(",", o.getTeams());
                out.add(new SetupDtos.GeneratedMatch(o.getCompetition(), o.getCompetitionRound(), o.getDate(), o.getHour(), home,
                        o.hasMatchDetails() ? o.getTeamAway() : null));
            }
        }
        return out;
    }

    public SetupDtos.PlanningJob cancel(UUID id) {
        job(id);
        jdbc.sql("UPDATE planning_job SET cancel_requested = :cancel WHERE id = :id").param("cancel", true).param("id", id).update();
        ScheduleMoroccoAlg alg = running.get(id);
        if (alg != null) {
            alg.cancel();
        }
        jdbc.sql("UPDATE planning_job SET status = 'CANCELLED', finished_at = CURRENT_TIMESTAMP, error_message = 'Cancelled before start' WHERE id = :id AND status = 'QUEUED'")
                .param("id", id).update();
        return job(id);
    }

    private void timeout(UUID id) {
        ScheduleMoroccoAlg alg = running.get(id);
        if (alg != null) {
            MDC.put(JobLogAppender.MDC_KEY, id.toString());
            try {
                log.warn("Timeout of {} reached: cancelling", timeout);
            } finally {
                MDC.remove(JobLogAppender.MDC_KEY);
            }
            jdbc.sql("UPDATE planning_job SET error_message = :m WHERE id = :id").param("m", "Timeout " + timeout).param("id", id).update();
            alg.cancel();
        }
    }

    private boolean markRunning(UUID id) {
        return jdbc.sql("UPDATE planning_job SET status = 'RUNNING', started_at = CURRENT_TIMESTAMP WHERE id = :id AND status = 'QUEUED'")
                .param("id", id).update() == 1;
    }

    private boolean cancelRequested(UUID id) {
        return Boolean.TRUE.equals(jdbc.sql("SELECT cancel_requested FROM planning_job WHERE id = :id").param("id", id).query(Boolean.class).single());
    }

    private void finish(UUID id, String status, String error, Long versionId) {
        jdbc.sql("""
                UPDATE planning_job SET status = :st, finished_at = CURRENT_TIMESTAMP, error_message = COALESCE(error_message, :err), version_id = :v
                 WHERE id = :id AND status IN ('QUEUED','RUNNING')""")
                .param("st", status).param("err", error).param("v", versionId).param("id", id).update();
    }

    public SetupDtos.PlanningJob job(UUID id) {
        return jdbc.sql("""
                SELECT j.*, cs.competition_id, cs.season_id, v.version_no FROM planning_job j
                  JOIN competition_season cs ON cs.id = j.competition_season_id
                  LEFT JOIN generation_version v ON v.id = j.version_id WHERE j.id = :id""")
                .param("id", id)
                .query((rs, i) -> new SetupDtos.PlanningJob(rs.getString("id"), rs.getString("competition_id"), rs.getString("season_id"),
                        rs.getString("status"), rs.getInt("first_round_to_assign"), rs.getInt("last_round_to_assign"),
                        ts(rs.getTimestamp("created_at")),
                        ts(rs.getTimestamp("started_at")), ts(rs.getTimestamp("finished_at")), rs.getString("error_message"),
                        (Long) rs.getObject("version_id"), (Integer) rs.getObject("version_no")))
                .optional().orElseThrow(() -> new SetupExceptions.NotFound("Planning job " + id + " not found"));
    }

    public List<SetupDtos.JobLogLine> logs(UUID id, int fromSeq) {
        job(id);
        return jdbc.sql("SELECT seq, at, level, message FROM planning_job_log WHERE job_id = :id AND seq > :from ORDER BY seq")
                .param("id", id).param("from", fromSeq)
                .query((rs, i) -> new SetupDtos.JobLogLine(rs.getInt(1), ts(rs.getTimestamp(2)), rs.getString(3), rs.getString(4))).list();
    }

    @PreDestroy
    void shutdown() {
        running.values().forEach(ScheduleMoroccoAlg::cancel);
        pool.shutdownNow();
        watchdog.shutdownNow();
    }

    private static Instant ts(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
