package net.atos.mev.calendarcalculator.compat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.setup.DatabaseEngineConfiguration;
import net.atos.mev.calendarcalculator.setup.GenerationRepository;
import net.atos.mev.calendarcalculator.setup.LegacyExcelBridge;
import net.atos.mev.calendarcalculator.setup.SeasonSetupRepository;
import net.atos.mev.calendarcalculator.setup.SetupCatalogRepository;
import net.atos.mev.calendarcalculator.setup.SetupConfig;
import net.atos.mev.calendarcalculator.setup.draw.DrawBatchRepository;
import net.atos.mev.calendarcalculator.setup.draw.DrawBatchService;
import net.atos.mev.calendarcalculator.setup.draw.DrawDtos;
import net.atos.mev.calendarcalculator.setup.jobs.PlanningJobService;
import net.atos.mev.calendarcalculator.setup.rules.RuleScopeRepository;
import net.atos.mev.calendarcalculator.setup.rules.RuleScopeService;

public final class DatabaseBatchSupport {
    private static final Logger LOG = LoggerFactory.getLogger(DatabaseBatchSupport.class);

    private DatabaseBatchSupport() { }

    public static String selector(String[] args) {
        if (args.length == 0 || args[0].isBlank()) {
            throw new IllegalArgumentException("A database selector is required: DB:COMPETITION@YYYY-YY");
        }
        return args[0];
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("legacy-batch")
    @EnableAutoConfiguration
    @Import({DatabaseEngineConfiguration.class, SetupConfig.class, SeasonSetupRepository.class, GenerationRepository.class,
            SetupCatalogRepository.class, RuleScopeRepository.class, RuleScopeService.class, DrawBatchRepository.class,
            LegacyExcelBridge.class})
    public static class BatchConfiguration { }

    private static org.springframework.context.ConfigurableApplicationContext open() {
        if ((System.getenv("DB_URL") == null || System.getenv("DB_URL").isBlank())
                && System.getProperty("spring.datasource.url") == null) {
            throw new IllegalArgumentException("Legacy batch now reads the database: set DB_URL and use DB:COMPETITION@YYYY-YY");
        }
        var application = new SpringApplication(BatchConfiguration.class);
        application.setAdditionalProfiles("legacy-batch");
        application.setWebApplicationType(WebApplicationType.NONE);
        return application.run("--spring.liquibase.drop-first=false",
                "--spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.yaml", "--calendar.demo.enabled=false");
    }

    public static <T> T withConfiguration(Function<DatabaseEngineConfiguration, T> action) {
        try (var context = open()) {
            return action.apply(context.getBean(DatabaseEngineConfiguration.class));
        }
    }

    public static Environment environment(String selector) {
        return withConfiguration(configuration -> configuration.environment(selector, null));
    }

    public static Properties properties(String selector) {
        return withConfiguration(configuration -> configuration.properties(selector, null, null));
    }

    private static <T> T withDraws(Function<DrawBatchService, T> action) {
        try (var context = open()) {
            var factory = context.getAutowireCapableBeanFactory();
            // Create after refresh: CLI startup must not mark the live server's jobs as orphaned.
            var draws = factory.createBean(DrawBatchService.class);
            try {
                return action.apply(draws);
            } finally {
                factory.destroyBean(draws);
            }
        }
    }

    public static void runDraw(String[] args) {
        String selector = args.length > 0 ? args[0] : "AssignPositions.properties";
        int count = args.length > 1 ? Integer.parseInt(args[1]) : 1000;
        String output = args.length > 2 ? args[2] : "./data/results";
        var setup = withConfiguration(configuration -> configuration.setup(selector, null));
        withDraws(draws -> {
            var batch = draws.start(setup.competitionId(), setup.season(), new DrawDtos.StartDrawRequest(count, 30, true, null));
            long deadline = System.nanoTime() + java.time.Duration.ofMinutes(31).toNanos();
            try {
                while ("RUNNING".equals(batch.status()) || "QUEUED".equals(batch.status())) {
                    if (System.nanoTime() > deadline) {
                        draws.cancel(batch.id());
                        throw new IllegalStateException("Draw batch deadline exceeded: " + batch.id());
                    }
                    Thread.sleep(250);
                    batch = draws.get(batch.id());
                }
            } catch (InterruptedException exception) {
                draws.cancel(batch.id());
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Draw batch interrupted: " + batch.id(), exception);
            }
            if (!"DONE".equals(batch.status())) throw new IllegalStateException("Draw " + batch.status() + ": " + batch.message());
            if (batch.generated() < batch.requested()) LOG.warn("Partial draw result: {}", batch.message());
            writeZip(draws, batch.id(), Path.of(output));
            return null;
        });
    }

    public static void exportDraw(String selector, String outputFolder) {
        withDraws(draws -> {
            String batchId;
            if (selector.matches("[0-9a-fA-F-]{36}")) {
                batchId = selector;
            } else {
                var setup = withConfiguration(configuration -> configuration.setup(selector, null));
                var batches = draws.list(setup.competitionId(), setup.season()).stream().filter(b -> b.generated() > 0 && !"RUNNING".equals(b.status())).toList();
                if (batches.size() != 1) throw new IllegalArgumentException("Specify a draw batch ID: expected exactly one exportable batch, found " + batches.size());
                batchId = batches.getFirst().id();
            }

            writeZip(draws, batchId, Path.of(outputFolder == null || outputFolder.isBlank() ? "./data/results" : outputFolder));
            return null;
        });
    }

    public static void runPlanning(String[] args) {
        String selector = args.length > 0 ? args[0] : "SchEnvironment.properties";
        Integer lastRound = args.length > 1 ? Integer.valueOf(args[1]) : null;
        Path output = Path.of(args.length > 2 ? args[2] : "./data/results");
        try (var context = open()) {
            var configuration = context.getBean(DatabaseEngineConfiguration.class);
            var setup = configuration.setup(selector, null);
            var factory = context.getAutowireCapableBeanFactory();
            var jobs = factory.createBean(PlanningJobService.class);
            try {
                var job = jobs.start(setup.competitionId(), setup.season(), lastRound);
                var id = java.util.UUID.fromString(job.id());
                long deadline = System.nanoTime() + java.time.Duration.parse(context.getEnvironment()
                        .getProperty("calendar.planning.timeout", "PT30M")).plusMinutes(1).toNanos();
                try {
                    while ("QUEUED".equals(job.status()) || "RUNNING".equals(job.status())) {
                        if (System.nanoTime() > deadline) {
                            jobs.cancel(id);
                            throw new IllegalStateException("Planning deadline exceeded: " + id);
                        }
                        Thread.sleep(250);
                        job = jobs.job(id);
                    }
                } catch (InterruptedException exception) {
                    jobs.cancel(id);
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Planning interrupted: " + id, exception);
                }
                if (!"SUCCEEDED".equals(job.status()) || job.versionId() == null) {
                    throw new IllegalStateException("Planning " + job.status() + ": " + job.errorMessage());
                }
                var generations = context.getBean(GenerationRepository.class);
                long versionId = job.versionId();
                var version = generations.versions(context.getBean(SeasonSetupRepository.class)
                        .competitionSeasonId(setup.competitionId(), setup.season())).stream()
                        .filter(v -> v.id() == versionId).findFirst().orElseThrow();
                Path folder = output.toAbsolutePath().normalize().resolve("planning-" + id);
                try {
                    Files.createDirectories(folder);
                    for (var file : version.files()) {
                        if (!Path.of(file.name()).getFileName().toString().equals(file.name())) {
                            throw new IllegalArgumentException("Invalid stored artifact name: " + file.name());
                        }
                        var stored = generations.file(versionId, file.name()).orElseThrow();
                        Files.write(folder.resolve(file.name()), stored.content(), StandardOpenOption.CREATE_NEW);
                    }
                } catch (IOException exception) {
                    throw new UncheckedIOException("Cannot export persisted planning version " + versionId, exception);
                }
                LOG.info("Persisted P2 version {} and exported artifacts to {}", versionId, folder);
            } finally {
                factory.destroyBean(jobs);
            }
        }
    }

    private static void writeZip(DrawBatchService draws, String id, Path folder) {
        try {
            folder = folder.resolve("draw-" + id);
            Files.createDirectories(folder);
            Path target = folder.resolve(draws.zipName(id));
            try (var output = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW)) {
                draws.writeZip(id, output);
            }
            LOG.info("Exported database draw batch to {}", target.toAbsolutePath());
        } catch (IOException exception) {
            throw new UncheckedIOException("Cannot export database draw batch " + id, exception);
        }
    }
}
