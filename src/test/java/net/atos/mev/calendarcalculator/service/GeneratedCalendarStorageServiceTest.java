package net.atos.mev.calendarcalculator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import net.atos.mev.calendarcalculator.config.CalendarCompetitionsProperties;
import net.atos.mev.calendarcalculator.service.dto.GeneratedCalendarFileDTO;

class GeneratedCalendarStorageServiceTest {
    private Path testDirectory;

    @BeforeEach
    void directory() throws IOException {
        testDirectory = Files.createDirectories(Path.of("target", "compatibility-result-tests", java.util.UUID.randomUUID().toString()));
    }

    @AfterEach
    void cleanup() throws IOException {
        try (var paths = Files.walk(testDirectory)) {
            for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void shouldCreateZipExcludingOutCsvAndExposeItForDownload() throws IOException {
        CalendarCompetitionsProperties.CompetitionConfig competitionConfig = new CalendarCompetitionsProperties.CompetitionConfig();
        competitionConfig.setName("Botola Pro (1a Division)");
        competitionConfig.setStorageDir("schBotolaD1");
        competitionConfig.setFile("DB:BOTOLA_D1");

        Path resultsBase = testDirectory.resolve("results");
        GeneratedCalendarStorageService storageService =
            new GeneratedCalendarStorageService(resultsBase.toString(), catalog(competitionConfig));

        Path generationDirectory = resultsBase.resolve("2025-26").resolve("schBotolaD1-v3");
        Files.createDirectories(generationDirectory);
        Files.writeString(generationDirectory.resolve("fullCalendar.xlsx"), "full-calendar");
        Files.writeString(generationDirectory.resolve("summaryPerTeam.xlsx"), "summary");
        Files.writeString(generationDirectory.resolve("statisticsTime.csv"), "stats-time");
        Files.writeString(generationDirectory.resolve("outSample.csv"), "should-be-excluded");

        GeneratedCalendarFileDTO createdZip = storageService.createGenerationZip(
            "BOTOLA_D1",
            "2025-26",
            "results/2025-26/schBotolaD1-v3",
            8
        );

        assertThat(createdZip.fileName()).isEqualTo("D1 MEN 2025-26 rounds to 8.zip");
        assertThat(createdZip.version()).isEqualTo("v3");

        List<GeneratedCalendarFileDTO> generatedFiles = storageService.listGeneratedFullCalendars("BOTOLA_D1", "2025-26");
        assertThat(generatedFiles)
            .extracting(GeneratedCalendarFileDTO::fileName)
            .contains("fullCalendar.xlsx", "D1 MEN 2025-26 rounds to 8.zip")
            .doesNotContain("outSample.csv");

        Optional<byte[]> zipBytes = storageService.readGeneratedFullCalendar("BOTOLA_D1", "2025-26", createdZip.downloadId());
        assertThat(zipBytes).isPresent();

        List<String> zipEntries = listZipEntries(zipBytes.orElseThrow());
        assertThat(zipEntries)
            .anyMatch(entry -> entry.endsWith("/fullCalendar.xlsx"))
            .anyMatch(entry -> entry.endsWith("/summaryPerTeam.xlsx"))
            .anyMatch(entry -> entry.endsWith("/statisticsTime.csv"))
            .noneMatch(entry -> entry.endsWith("/outSample.csv"));
    }

    @Test
    void shouldReturnSingleXlsxPerVersionPreferringVersionedFolder() throws IOException {
        CalendarCompetitionsProperties.CompetitionConfig competitionConfig = new CalendarCompetitionsProperties.CompetitionConfig();
        competitionConfig.setName("Botola Pro (1a Division)");
        competitionConfig.setStorageDir("schBotolaD1");
        competitionConfig.setFile("DB:BOTOLA_D1");

        Path resultsBase = testDirectory.resolve("results");
        GeneratedCalendarStorageService storageService =
            new GeneratedCalendarStorageService(resultsBase.toString(), catalog(competitionConfig));

        Path legacyFolder = resultsBase.resolve("2025-26").resolve("schBotolaD1").resolve("CalendarD1-v0");
        Path versionedFolder = resultsBase.resolve("2025-26").resolve("schBotolaD1-v0");
        Files.createDirectories(legacyFolder);
        Files.createDirectories(versionedFolder);

        Files.writeString(legacyFolder.resolve("fullCalendar.xlsx"), "legacy-xlsx");
        Files.writeString(versionedFolder.resolve("fullCalendar.xlsx"), "versioned-xlsx");
        Files.writeString(versionedFolder.resolve("D1 MEN 2025-26 rounds to 9.zip"), "zip-data");

        List<GeneratedCalendarFileDTO> generatedFiles = storageService.listGeneratedFullCalendars("BOTOLA_D1", "2025-26");

        assertThat(generatedFiles)
            .filteredOn(file -> file.fileName().toLowerCase().endsWith(".xlsx"))
            .hasSize(1);

        assertThat(generatedFiles)
            .filteredOn(file -> file.fileName().equals("fullCalendar.xlsx"))
            .singleElement()
            .extracting(GeneratedCalendarFileDTO::folder)
            .isEqualTo("schBotolaD1-v0");
    }

    private CompetitionCatalogService catalog(CalendarCompetitionsProperties.CompetitionConfig config) {
        var catalog = mock(CompetitionCatalogService.class);
        when(catalog.configuration("BOTOLA_D1")).thenReturn(config);
        return catalog;
    }

    private List<String> listZipEntries(byte[] zipBytes) throws IOException {
        List<String> entries = new ArrayList<>();
        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                entries.add(entry.getName());
            }
        }
        return entries;
    }
}
