package net.atos.mev.calendarcalculator.engine;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.AdditionalMatchers.aryEq;
import static org.mockito.Mockito.*;
import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import net.atos.mev.calendarcalculator.schedules.PlanningParameters;
import net.atos.mev.calendarcalculator.setup.DatabaseEngineConfiguration;
import net.atos.mev.calendarcalculator.setup.GenerationRepository;
import net.atos.mev.calendarcalculator.setup.SeasonSetupRepository;
import net.atos.mev.calendarcalculator.setup.SetupDtos;
import net.atos.mev.calendarcalculator.setup.SetupExceptions;
import net.atos.mev.calendarcalculator.setup.jobs.PlanningJobService;

class LegacyCalendarEngineTest {
    private final DatabaseEngineConfiguration configuration = mock(DatabaseEngineConfiguration.class);
    private final SeasonSetupRepository setups = mock(SeasonSetupRepository.class);
    private final PlanningJobService jobs = mock(PlanningJobService.class);
    private final GenerationRepository generations = mock(GenerationRepository.class);
    private final SetupDtos.SeasonSetup current = mock(SetupDtos.SeasonSetup.class);
    private LegacyCalendarEngine engine;
    private Properties properties;

    @BeforeEach
    void initialize() {
        engine = new LegacyCalendarEngine(configuration, setups, jobs, generations, "results");
        properties = new Properties();
        properties.setProperty("env", "DB:BOTOLA_D1@2026-27");
        properties.setProperty("ScheduleMoroccoAlg.lastRoundToAssign", "4");
        when(configuration.setup("DB:BOTOLA_D1@2026-27", null)).thenReturn(current);
        when(current.competitionId()).thenReturn("BOTOLA_D1");
        when(current.season()).thenReturn("2026-27");
        when(setups.competitionSeasonId("BOTOLA_D1", "2026-27")).thenReturn(3L);
        var planning = mock(DatabaseEngineConfiguration.Planning.class);
        var parameters = mock(PlanningParameters.class);
        when(parameters.outputDatesExcel()).thenReturn("fullCalendar.xlsx");
        when(planning.parameters()).thenReturn(parameters);
        when(configuration.planning(current, 4)).thenReturn(planning);
    }

    @Test
    void delegatesToModernJobsAndReturnsThePersistedVersionWithoutRunningAnotherPlanner() {
        when(jobs.start("BOTOLA_D1", "2026-27", 4)).thenReturn(completedJob());
        persistedVersion();
        assertThat(engine.run(properties, null)).containsExactly((byte) 9, (byte) 8);
        verify(jobs).start("BOTOLA_D1", "2026-27", 4);
        verify(generations).file(7L, "fullCalendar.xlsx");
        verify(jobs, never()).startWithImport(any(), any(), any());
        verify(setups, never()).replace(any());
    }

    @Test
    void explicitExcelUsesTheAtomicImportAndStartEntryPoint() {
        when(jobs.startWithImport(eq(current), any(byte[].class), eq(4))).thenReturn(completedJob());
        persistedVersion();
        assertThat(engine.run(properties, new ByteArrayInputStream(new byte[] {1, 2})))
                .containsExactly((byte) 9, (byte) 8);
        verify(jobs).startWithImport(eq(current), aryEq(new byte[] {1, 2}), eq(4));
        verify(jobs, never()).start(any(), any(), any());
        verify(setups, never()).replace(any());
    }

    @Test
    void propagatesAtomicImportConflictWithoutFallingBackToAnUnprotectedStart() {
        when(jobs.startWithImport(eq(current), any(byte[].class), eq(4)))
                .thenThrow(new SetupExceptions.Conflict("Configuration changed"));
        assertThatThrownBy(() -> engine.run(properties, new ByteArrayInputStream(new byte[] {1})))
                .isInstanceOf(SetupExceptions.Conflict.class);
        verify(jobs, never()).start(any(), any(), any());
        verify(setups, never()).replace(any());
    }

    @Test
    void rejectsOutputPathsOutsideTheConfiguredArtifactsRootBeforeImportOrPlanning() {
        properties.setProperty("resultsFolder", "../outside");
        assertThatThrownBy(() -> engine.run(properties, new ByteArrayInputStream(new byte[] {1})))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jobs);
    }

    private SetupDtos.PlanningJob completedJob() {
        return new SetupDtos.PlanningJob(UUID.randomUUID().toString(), "BOTOLA_D1", "2026-27", "SUCCEEDED",
                4, null, null, null, null, 7L, 1);
    }

    private void persistedVersion() {
        when(generations.versions(3L)).thenReturn(List.of(new SetupDtos.Version(7L, 1, "DRAFT", 4, "P2",
                null, null, null, List.of(new SetupDtos.FileInfo(1L, "fullCalendar.xlsx", "application/octet-stream", 2)))));
        when(generations.file(7L, "fullCalendar.xlsx")).thenReturn(Optional.of(
                new GenerationRepository.StoredFile("fullCalendar.xlsx", "application/octet-stream", new byte[] {9, 8})));
    }
}
