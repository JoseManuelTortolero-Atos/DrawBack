package net.atos.mev.calendarcalculator.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import net.atos.mev.calendarcalculator.config.CalendarCompetitionsProperties.CompetitionConfig;
import net.atos.mev.calendarcalculator.setup.DatabaseEngineConfiguration;
import net.atos.mev.calendarcalculator.setup.SetupDtos.SeasonSetup;
import net.atos.mev.calendarcalculator.service.dto.CompetitionDTO;

class CompetitionExcelStorageServiceTest {
    private Path root;
    private CompetitionCatalogService catalog;
    private DatabaseEngineConfiguration database;
    private CompetitionExcelStorageService storage;

    @BeforeEach
    void initialize() throws Exception {
        root = Files.createDirectories(Path.of("target", "compatibility-excel-tests", UUID.randomUUID().toString()));
        catalog = mock(CompetitionCatalogService.class);
        database = mock(DatabaseEngineConfiguration.class);
        storage = new CompetitionExcelStorageService(root.toString(), catalog, database);
        for (String id : new String[] {"BOTOLA_D1", "BOTOLA_D2", "CNPFF_D1", "CNPFF_D2"}) {
            var setup = mock(SeasonSetup.class);
            when(setup.competitionId()).thenReturn(id);
            when(setup.season()).thenReturn("2026-27");
            when(database.setup(eq(id), anyString())).thenReturn(setup);
            var config = new CompetitionConfig();
            config.setStorageDir("database-" + id);
            when(catalog.configuration(id)).thenReturn(config);
            when(catalog.getCompetitionById(id)).thenReturn(Optional.of(new CompetitionDTO(id, id, "DB:" + id, true)));
        }
    }

    @AfterEach
    void cleanup() throws Exception {
        try (var files = Files.walk(root)) {
            for (var path : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void initializationDoesNotSeedHistoricalWorkbooksOrSeasons() throws Exception {
        storage.initializeStorageStructure();
        try (var files = Files.list(root)) {
            assertThat(files.toList()).isEmpty();
        }
        verifyNoInteractions(database, catalog);
    }

    @Test
    void preservesUploadCaseInsensitiveDownloadAndDeleteUsingDatabaseLocation() {
        var upload = new MockMultipartFile("excel", "CalendarD1-v7.xlsx", "application/octet-stream", new byte[] {1, 2, 3});
        storage.storeExcelFile("BOTOLA_D1", "2026-27", upload, false);
        assertThat(root.resolve("database-BOTOLA_D1/2026-27/CalendarD1-v7.xlsx")).exists();
        assertThat(storage.readExcelFile("BOTOLA_D1", "2026-27", "CALENDARD1-V7.XLSX").orElseThrow())
                .containsExactly((byte) 1, (byte) 2, (byte) 3);
        assertThat(storage.listExcelFiles("BOTOLA_D1", "2026-27")).hasSize(1);
        assertThatThrownBy(() -> storage.storeExcelFile("BOTOLA_D1", "2026-27", upload, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(storage.deleteExcelFile("BOTOLA_D1", "2026-27", "calendard1-v7.xlsx")).isTrue();
        assertThat(storage.readExcelFile("BOTOLA_D1", "2026-27", "CalendarD1-v7.xlsx")).isEmpty();
    }

    @Test
    void preservesAllFourLegacyWorkbookFamiliesAndVersionPaths() {
        storage.validateExcelFileNameForCompetition("BOTOLA_D1", "CalendarD1-V1.XLSX");
        storage.validateExcelFileNameForCompetition("BOTOLA_D2", "CalendarD2-v2.xlsx");
        storage.validateExcelFileNameForCompetition("CNPFF_D1", "CalendarF1-v3.xlsx");
        storage.validateExcelFileNameForCompetition("CNPFF_D2", "CalendarF2-v4.xlsx");
        assertThat(storage.buildResultsFolder("CNPFF_D2", "2026-27", "CalendarF2-v4.xlsx"))
                .isEqualTo("results/2026-27/database-CNPFF_D2-v4");
        assertThatThrownBy(() -> storage.validateExcelFileNameForCompetition("CNPFF_D1", "CalendarD1-v1.xlsx"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsTraversalAndUnknownDatabaseSeasons() {
        assertThatThrownBy(() -> storage.readExcelFile("BOTOLA_D1", "2026-27", "../CalendarD1-v1.xlsx"))
                .isInstanceOf(IllegalArgumentException.class);
        when(database.setup("BOTOLA_D1", "unknown")).thenThrow(new IllegalArgumentException("Unknown season"));
        assertThatThrownBy(() -> storage.listExcelFiles("BOTOLA_D1", "unknown"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unknown season");
    }
}
