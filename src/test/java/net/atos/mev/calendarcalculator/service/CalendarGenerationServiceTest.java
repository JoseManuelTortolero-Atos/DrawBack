package net.atos.mev.calendarcalculator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import net.atos.mev.calendarcalculator.service.dto.CompetitionRuntimeOverridesDTO;

@ExtendWith(MockitoExtension.class)
class CalendarGenerationServiceTest {

    @Test
    void loggingRemainsAvailableWithEitherLegacyFlagValueWithoutStartingADatabase() {
        for (boolean enabled : new boolean[] {false, true}) {
            new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                    .withUserConfiguration(CalendarGenerationService.class, GenerationLogStoreService.class)
                    .withPropertyValues("calendar.legacy-files.enabled=" + enabled)
                    .withBean(CompetitionPropertiesService.class, () -> competitionPropertiesService)
                    .withBean(CompetitionExcelStorageService.class, () -> competitionExcelStorageService)
                    .withBean(GeneratedCalendarStorageService.class, () -> generatedCalendarStorageService)
                    .withBean(ScheduleFacade.class, () -> scheduleFacade)
                    .run(context -> assertThat(context).hasSingleBean(GenerationLogStoreService.class));
        }
    }

    @Mock
    private CompetitionPropertiesService competitionPropertiesService;

    @Mock
    private CompetitionExcelStorageService competitionExcelStorageService;

    @Mock
    private GeneratedCalendarStorageService generatedCalendarStorageService;

    @Mock
    private ScheduleFacade scheduleFacade;

    @Mock
    private GenerationLogStoreService generationLogStoreService;

    @InjectMocks
    private CalendarGenerationService calendarGenerationService;

    @Test
    void shouldUseDynamicResultsFolderFromSeasonAndExcelVersion() {
        String expectedResultsFolder = Path.of("./results")
            .toAbsolutePath()
            .normalize()
            .resolve("2025-26")
            .resolve("schBotolaD1-v7")
            .toString();

        Properties resolvedProperties = new Properties();
        when(competitionExcelStorageService.buildResultsFolder("BOTOLA_D1", "2025-26", "CalendarD1-v7.xlsx"))
            .thenReturn("results/2025-26/schBotolaD1-v7");
        when(competitionPropertiesService.loadFinalProperties(eq("BOTOLA_D1"), eq("2025-26"), any(CompetitionRuntimeOverridesDTO.class)))
            .thenReturn(resolvedProperties);
        when(competitionExcelStorageService.readExcelFile("BOTOLA_D1", "2025-26", "CalendarD1-v7.xlsx"))
            .thenReturn(Optional.of(new byte[] {1, 2, 3}));
        when(scheduleFacade.run(eq(resolvedProperties), any(InputStream.class)))
            .thenReturn(new byte[] {9});

        byte[] generated = calendarGenerationService.generateCalendar("BOTOLA_D1", "2025-26", "CalendarD1-v7.xlsx", 4);

        assertThat(generated).containsExactly((byte) 9);

        ArgumentCaptor<CompetitionRuntimeOverridesDTO> runtimeOverridesCaptor =
            ArgumentCaptor.forClass(CompetitionRuntimeOverridesDTO.class);
        verify(competitionPropertiesService).loadFinalProperties(eq("BOTOLA_D1"), eq("2025-26"), runtimeOverridesCaptor.capture());

        CompetitionRuntimeOverridesDTO runtimeOverrides = runtimeOverridesCaptor.getValue();
        assertThat(runtimeOverrides.resultsFolder()).isEqualTo(expectedResultsFolder);
        assertThat(runtimeOverrides.lastRoundToAssign()).isEqualTo(4);
        assertThat(runtimeOverrides.datesFile()).isNull();

        verify(generatedCalendarStorageService).createGenerationZip("BOTOLA_D1", "2025-26", expectedResultsFolder, 4);
    }
}
