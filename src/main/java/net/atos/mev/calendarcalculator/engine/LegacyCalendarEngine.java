package net.atos.mev.calendarcalculator.engine;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import net.atos.mev.calendarcalculator.setup.DatabaseEngineConfiguration;
import net.atos.mev.calendarcalculator.setup.GenerationRepository;
import net.atos.mev.calendarcalculator.setup.SeasonSetupRepository;
import net.atos.mev.calendarcalculator.setup.jobs.PlanningJobService;

/**
 * Synchronous compatibility entry point to the same persisted planning jobs used by
 * the setup API. No second planner, file configuration, or unvalidated Excel execution.
 */
@Component
public class LegacyCalendarEngine implements CalendarEngine {
    private final DatabaseEngineConfiguration configuration;
    private final SeasonSetupRepository setups;
    private final PlanningJobService jobs;
    private final GenerationRepository generations;
    private final Path resultsBase;

    public LegacyCalendarEngine(DatabaseEngineConfiguration configuration, SeasonSetupRepository setups,
            PlanningJobService jobs, GenerationRepository generations,
            @Value("${calendar.results.base-dir:./results}") String resultsBase) {
        this.configuration = configuration;
        this.setups = setups;
        this.jobs = jobs;
        this.generations = generations;
        this.resultsBase = Path.of(resultsBase).toAbsolutePath().normalize();
    }

    @Override
    public byte[] run(Properties properties, InputStream inputStream) {
        if (properties == null) {
            throw new IllegalArgumentException("Database configuration is required");
        }
        String selector = properties.getProperty("database.competitionId");
        if (selector == null) {
            selector = properties.getProperty("file");
        }
        if (selector == null) {
            selector = properties.getProperty("env");
        }
        var selected = configuration.setup(selector, properties.getProperty("database.season"));
        String lastValue = properties.getProperty("ScheduleMoroccoAlg.lastRoundToAssign");
        Integer last = lastValue == null || lastValue.isBlank() ? null : Integer.valueOf(lastValue);
        Path output = outputDirectory(properties.getProperty("resultsFolder"));
        try {
            var job = inputStream == null
                    ? jobs.start(selected.competitionId(), selected.season(), last)
                    : jobs.startWithImport(selected, inputStream.readAllBytes(), last);
            UUID jobId = UUID.fromString(job.id());
            int fromSeq = 0;
            while ("QUEUED".equals(job.status()) || "RUNNING".equals(job.status())) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    jobs.cancel(jobId);
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Calendar generation interrupted", e);
                }
                job = jobs.job(jobId);
                for (var line : jobs.logs(jobId, fromSeq)) {
                    org.slf4j.LoggerFactory.getLogger(LegacyCalendarEngine.class)
                            .info("[{}] {}", line.level(), line.message());
                    fromSeq = line.seq();
                }
            }
            if (!"SUCCEEDED".equals(job.status()) || job.versionId() == null) {
                throw new IllegalStateException("Planning job " + job.id() + ": " + job.status() + ". " + job.errorMessage());
            }
            long versionId = job.versionId();
            var version = generations.versions(setups.competitionSeasonId(selected.competitionId(), selected.season()))
                    .stream().filter(v -> v.id() == versionId).findFirst()
                    .orElseThrow(() -> new IllegalStateException("Generated version not found: " + versionId));
            String calendarName = configuration.planning(selected, last).parameters().outputDatesExcel();
            byte[] calendar = null;
            for (var file : version.files()) {
                var stored = generations.file(versionId, file.name())
                        .orElseThrow(() -> new IllegalStateException("Missing generated file: " + file.name()));
                if (file.name().equals(calendarName)) {
                    calendar = stored.content();
                }
                if (output != null) {
                    Path target = output.resolve(file.name()).normalize();
                    if (!target.startsWith(output)) {
                        throw new IllegalArgumentException("Invalid generated file name: " + file.name());
                    }
                    Files.createDirectories(target.getParent());
                    Files.write(target, stored.content());
                }
            }
            if (calendar == null) {
                throw new IllegalStateException("Generated calendar Excel not found in version " + versionId);
            }
            return calendar;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot read/write calendar artifacts", e);
        }
    }

    private Path outputDirectory(String folder) {
        if (folder == null || folder.isBlank()) {
            return null;
        }
        String normalized = folder.replace('\\', '/');
        Path path = Path.of(normalized);
        if (!path.isAbsolute()) {
            path = normalized.startsWith("results/")
                    ? resultsBase.resolve(normalized.substring("results/".length())) : resultsBase.resolve(path);
        }
        path = path.toAbsolutePath().normalize();
        if (!path.startsWith(resultsBase)) {
            throw new IllegalArgumentException("Results folder must be inside calendar.results.base-dir");
        }
        return path;
    }
}
