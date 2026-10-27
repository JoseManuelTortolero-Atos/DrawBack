package net.atos.mev.calendarcalculator.service;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Properties;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import net.atos.mev.calendarcalculator.service.dto.CompetitionRuntimeOverridesDTO;
import net.atos.mev.calendarcalculator.service.dto.GenerationExecutionResult;

@Service
public class CalendarGenerationService {

    // The original log-store component is gated in a file outside this migration's scope.
    @org.springframework.context.annotation.Bean(name = "generationLogStoreService")
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            name = "calendar.legacy-files.enabled", havingValue = "false", matchIfMissing = true)
    static GenerationLogStoreService compatibilityGenerationLogs() {
        return new GenerationLogStoreService();
    }

    private static final String LOG_SCOPE = "net.atos.mev.calendarcalculator";
    private static final String RESULTS_PREFIX = "results/";

    private final CompetitionPropertiesService competitionPropertiesService;
    private final CompetitionExcelStorageService competitionExcelStorageService;
    private final GeneratedCalendarStorageService generatedCalendarStorageService;
    private final ScheduleFacade scheduleFacade;
    private final GenerationLogStoreService generationLogStoreService;

    @Value("${calendar.results.base-dir:./results}")
    private String resultsBaseDirectory = "./results";

    public CalendarGenerationService(
        CompetitionPropertiesService competitionPropertiesService,
        CompetitionExcelStorageService competitionExcelStorageService,
        GeneratedCalendarStorageService generatedCalendarStorageService,
        ScheduleFacade scheduleFacade,
        GenerationLogStoreService generationLogStoreService
    ) {
        this.competitionPropertiesService = competitionPropertiesService;
        this.competitionExcelStorageService = competitionExcelStorageService;
        this.generatedCalendarStorageService = generatedCalendarStorageService;
        this.scheduleFacade = scheduleFacade;
        this.generationLogStoreService = generationLogStoreService;
    }

    public GenerationExecutionResult generateCalendarWithLogs(
        String competitionId,
        String season,
        String excelFileName,
        Integer lastRoundToAssign
    ) {
        if (excelFileName == null || excelFileName.isBlank()) {
            throw new IllegalArgumentException("excelFileName is required");
        }

        String generationId = generationLogStoreService.startSession(competitionId, season, excelFileName);
        SessionLogAppender appender = null;
        Logger scopedLogger = null;
        try {
            LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
            scopedLogger = loggerContext.getLogger(LOG_SCOPE);
            appender = new SessionLogAppender(generationId, Thread.currentThread().getName(), generationLogStoreService);
            appender.setContext(loggerContext);
            appender.start();
            scopedLogger.addAppender(appender);

            byte[] generatedExcel = generateCalendar(competitionId, season, excelFileName, lastRoundToAssign);
            generationLogStoreService.markSuccess(generationId);
            return new GenerationExecutionResult(generationId, generatedExcel);
        } catch (IllegalArgumentException | net.atos.mev.calendarcalculator.setup.SetupExceptions.Conflict
                | net.atos.mev.calendarcalculator.setup.SetupExceptions.Invalid
                | net.atos.mev.calendarcalculator.setup.SetupExceptions.NotFound exception) {
            generationLogStoreService.markFailure(generationId, exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            Throwable root = exception;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            String rootMessage = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
            String errorMessage = "Error generating calendar for competitionId " + competitionId
                + ", season " + season
                + ", excelFileName " + excelFileName
                + ". Root cause: " + rootMessage;
            generationLogStoreService.markFailure(generationId, errorMessage);
            throw new GenerationFailedException(generationId, errorMessage, exception);
        } finally {
            if (scopedLogger != null && appender != null) {
                scopedLogger.detachAppender(appender);
                appender.stop();
            }
        }
    }

    public byte[] generateCalendar(
        String competitionId,
        String season,
        String excelFileName,
        Integer lastRoundToAssign
    ) {
        String dynamicResultsFolder = competitionExcelStorageService.buildResultsFolder(competitionId, season, excelFileName);
        String runtimeResultsFolder = resolveRuntimeResultsFolder(dynamicResultsFolder);

        CompetitionRuntimeOverridesDTO runtimeOverrides = new CompetitionRuntimeOverridesDTO(
            null,
            runtimeResultsFolder,
            lastRoundToAssign
        );

        Properties finalProperties = competitionPropertiesService.loadFinalProperties(competitionId, season, runtimeOverrides);

        byte[] excelBytes = competitionExcelStorageService.readExcelFile(competitionId, season, excelFileName)
            .orElseThrow(() -> new IllegalArgumentException(
                "Excel file not found for competitionId " + competitionId + ", season " + season + ": " + excelFileName
            ));

        ByteArrayInputStream inputStream = new ByteArrayInputStream(excelBytes);
        byte[] generatedExcel = scheduleFacade.run(finalProperties, inputStream);

        Integer effectiveLastRound = resolveEffectiveLastRound(lastRoundToAssign, finalProperties);
        generatedCalendarStorageService.createGenerationZip(competitionId, season, runtimeResultsFolder, effectiveLastRound);

        return generatedExcel;
    }

    private String resolveRuntimeResultsFolder(String generationResultsFolder) {
        if (!StringUtils.hasText(generationResultsFolder)) {
            throw new IllegalArgumentException("Generation results folder is required");
        }

        Path normalizedResultsBase = Path.of(resultsBaseDirectory).toAbsolutePath().normalize();
        String normalizedInput = generationResultsFolder.replace('\\', '/');
        Path resolved;
        if (Path.of(normalizedInput).isAbsolute()) {
            resolved = Path.of(normalizedInput).normalize();
        } else if (normalizedInput.startsWith(RESULTS_PREFIX)) {
            resolved = normalizedResultsBase.resolve(normalizedInput.substring(RESULTS_PREFIX.length())).normalize();
        } else {
            resolved = Path.of(".").toAbsolutePath().normalize().resolve(normalizedInput).normalize();
        }

        if (!resolved.startsWith(normalizedResultsBase)) {
            throw new IllegalArgumentException("Invalid generation results folder: " + generationResultsFolder);
        }
        return resolved.toString();
    }

    private Integer resolveEffectiveLastRound(Integer requestedLastRound, Properties finalProperties) {
        if (requestedLastRound != null) {
            return requestedLastRound;
        }

        String configuredLastRound = finalProperties.getProperty("ScheduleMoroccoAlg.lastRoundToAssign");
        if (configuredLastRound == null || configuredLastRound.isBlank()) {
            return null;
        }

        try {
            return Integer.valueOf(configuredLastRound.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static final class SessionLogAppender extends AppenderBase<ILoggingEvent> {
        private final String generationId;
        private final String threadName;
        private final GenerationLogStoreService generationLogStoreService;

        private SessionLogAppender(String generationId, String threadName, GenerationLogStoreService generationLogStoreService) {
            this.generationId = generationId;
            this.threadName = threadName;
            this.generationLogStoreService = generationLogStoreService;
        }

        @Override
        protected void append(ILoggingEvent event) {
            if (!threadName.equals(event.getThreadName())) {
                return;
            }
            if (event.getLoggerName() == null || !event.getLoggerName().startsWith(LOG_SCOPE)) {
                return;
            }

            String message = event.getFormattedMessage();
            if (message != null) {
                generationLogStoreService.append(generationId, message);
            }
        }
    }
}
