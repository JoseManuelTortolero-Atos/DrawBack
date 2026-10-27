package net.atos.mev.calendarcalculator.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import net.atos.mev.calendarcalculator.service.CalendarGenerationService;
import net.atos.mev.calendarcalculator.service.CompetitionCatalogService;
import net.atos.mev.calendarcalculator.service.CompetitionExcelStorageService;
import net.atos.mev.calendarcalculator.service.GeneratedCalendarStorageService;
import net.atos.mev.calendarcalculator.service.GenerationLogStoreService;
import net.atos.mev.calendarcalculator.service.dto.CompetitionDTO;
import net.atos.mev.calendarcalculator.service.dto.CompetitionExcelFileDTO;
import net.atos.mev.calendarcalculator.service.dto.GeneratedCalendarFileDTO;
import net.atos.mev.calendarcalculator.service.dto.GenerationExecutionResult;
import net.atos.mev.calendarcalculator.service.dto.GenerationLogsDTO;

@WebMvcTest(CalendarController.class)
@TestPropertySource(properties = "calendar.legacy-files.enabled=false")
class CalendarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CalendarGenerationService calendarGenerationService;

    @MockitoBean
    private CompetitionCatalogService competitionCatalogService;

    @MockitoBean
    private CompetitionExcelStorageService competitionExcelStorageService;

    @MockitoBean
    private GeneratedCalendarStorageService generatedCalendarStorageService;

    @MockitoBean
    private GenerationLogStoreService generationLogStoreService;

    @Test
    void shouldListExcelsForCompetitionAndSeason() throws Exception {
        CompetitionDTO competition = new CompetitionDTO("BOTOLA_D1", "Botola Pro (1a Division)", "DB:BOTOLA_D1", true);
        when(competitionCatalogService.getCompetitionById("BOTOLA_D1")).thenReturn(Optional.of(competition));
        when(competitionExcelStorageService.listExcelFiles("BOTOLA_D1", "2025-26")).thenReturn(List.of(
            new CompetitionExcelFileDTO("CalendarD1-v0.xlsx", 123L, Instant.parse("2026-02-11T11:00:00Z")),
            new CompetitionExcelFileDTO("CalendarD1-v1.xlsx", 124L, Instant.parse("2026-02-11T12:00:00Z"))
        ));

        mockMvc.perform(get("/api/calendar/competitions/BOTOLA_D1/seasons/2025-26/excels"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].fileName").value("CalendarD1-v0.xlsx"))
            .andExpect(jsonPath("$[1].fileName").value("CalendarD1-v1.xlsx"));
    }

    @Test
    void rejectsModernJobConflictsRatherThanBypassingTheLock() throws Exception {
        when(competitionCatalogService.getCompetitionById("BOTOLA_D1"))
                .thenReturn(Optional.of(new CompetitionDTO("BOTOLA_D1", "Botola", "DB:BOTOLA_D1", true)));
        when(calendarGenerationService.generateCalendarWithLogs("BOTOLA_D1", "2026-27", "CalendarD1-v1.xlsx", null))
                .thenThrow(new net.atos.mev.calendarcalculator.setup.SetupExceptions.Conflict("Job already running"));
        mockMvc.perform(post("/api/calendar/generate").param("competitionId", "BOTOLA_D1")
                .param("season", "2026-27").param("excelFileName", "CalendarD1-v1.xlsx"))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldUploadExcelForCompetitionAndSeason() throws Exception {
        CompetitionDTO competition = new CompetitionDTO("BOTOLA_D1", "Botola Pro (1a Division)", "DB:BOTOLA_D1", true);
        when(competitionCatalogService.getCompetitionById("BOTOLA_D1")).thenReturn(Optional.of(competition));

        MockMultipartFile excel = new MockMultipartFile(
            "excel",
            "CalendarD1-v1.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "dummy-base".getBytes()
        );
        when(competitionExcelStorageService.storeExcelFile(eq("BOTOLA_D1"), eq("2025-26"), any(), eq(true)))
            .thenReturn(new CompetitionExcelFileDTO("CalendarD1-v1.xlsx", 555L, Instant.parse("2026-02-11T12:10:00Z")));

        mockMvc.perform(
                multipart("/api/calendar/competitions/BOTOLA_D1/seasons/2025-26/excels")
                    .file(excel)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fileName").value("CalendarD1-v1.xlsx"));

        verify(competitionExcelStorageService).storeExcelFile(eq("BOTOLA_D1"), eq("2025-26"), any(), eq(true));
    }

    @Test
    void shouldReturnBadRequestWhenUploadNamePatternIsInvalid() throws Exception {
        CompetitionDTO competition = new CompetitionDTO("BOTOLA_D1", "Botola Pro (1a Division)", "DB:BOTOLA_D1", true);
        when(competitionCatalogService.getCompetitionById("BOTOLA_D1")).thenReturn(Optional.of(competition));

        MockMultipartFile excel = new MockMultipartFile(
            "excel",
            "CalendarD2-v1.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "dummy-base".getBytes()
        );
        when(competitionExcelStorageService.storeExcelFile(eq("BOTOLA_D1"), eq("2025-26"), any(), eq(true)))
            .thenThrow(new IllegalArgumentException(
                "Invalid file name for selected competition. Expected format (case-insensitive): CalendarD1-v<number>.xlsx"
            ));

        mockMvc.perform(
                multipart("/api/calendar/competitions/BOTOLA_D1/seasons/2025-26/excels")
                    .file(excel)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
            )
            .andExpect(status().isBadRequest());
    }

    @Test
    void shouldDeleteStoredExcelForCompetitionAndSeason() throws Exception {
        CompetitionDTO competition = new CompetitionDTO("BOTOLA_D1", "Botola Pro (1a Division)", "DB:BOTOLA_D1", true);
        when(competitionCatalogService.getCompetitionById("BOTOLA_D1")).thenReturn(Optional.of(competition));
        when(competitionExcelStorageService.deleteExcelFile("BOTOLA_D1", "2025-26", "CalendarD1-v0.xlsx"))
            .thenReturn(true);

        mockMvc.perform(delete("/api/calendar/competitions/BOTOLA_D1/seasons/2025-26/excels/CalendarD1-v0.xlsx"))
            .andExpect(status().isNoContent());

        verify(competitionExcelStorageService).deleteExcelFile("BOTOLA_D1", "2025-26", "CalendarD1-v0.xlsx");
    }

    @Test
    void shouldDownloadExcelForCompetitionAndSeason() throws Exception {
        CompetitionDTO competition = new CompetitionDTO("BOTOLA_D1", "Botola Pro (1a Division)", "DB:BOTOLA_D1", true);
        when(competitionCatalogService.getCompetitionById("BOTOLA_D1")).thenReturn(Optional.of(competition));
        when(competitionExcelStorageService.readExcelFile("BOTOLA_D1", "2025-26", "CalendarD1-v0.xlsx"))
            .thenReturn(Optional.of("excel-content".getBytes()));

        mockMvc.perform(get("/api/calendar/competitions/BOTOLA_D1/seasons/2025-26/excels/CalendarD1-v0.xlsx"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"CalendarD1-v0.xlsx\""));
    }

    @Test
    void shouldGenerateCalendarFromStoredExcel() throws Exception {
        CompetitionDTO competition = new CompetitionDTO("BOTOLA_D1", "Botola Pro (1a Division)", "DB:BOTOLA_D1", true);
        when(competitionCatalogService.getCompetitionById("BOTOLA_D1")).thenReturn(Optional.of(competition));
        when(calendarGenerationService.generateCalendarWithLogs("BOTOLA_D1", "2025-26", "CalendarD1-v1.xlsx", 5))
            .thenReturn(new GenerationExecutionResult("gen-123", "xlsx-bytes".getBytes()));

        mockMvc.perform(
                post("/api/calendar/generate")
                    .param("competitionId", "BOTOLA_D1")
                    .param("season", "2025-26")
                    .param("excelFileName", "CalendarD1-v1.xlsx")
                    .param("lastRoundToAssign", "5")
            )
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andExpect(header().string("X-Generation-Id", "gen-123"))
            .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("calendar-BOTOLA_D1-")));
    }

    @Test
    void shouldReturnGenerationLogs() throws Exception {
        when(generationLogStoreService.getLogs("gen-123"))
            .thenReturn(Optional.of(new GenerationLogsDTO(
                "gen-123",
                "SUCCESS",
                Instant.parse("2026-02-12T00:00:00Z"),
                Instant.parse("2026-02-12T00:01:00Z"),
                null,
                List.of("Row competition/date: D1,10/02/2026", "Row competition/date: D1,11/02/2026")
            )));

        mockMvc.perform(get("/api/calendar/generations/gen-123/logs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.generationId").value("gen-123"))
            .andExpect(jsonPath("$.status").value("SUCCESS"))
            .andExpect(jsonPath("$.lines[0]").value("Row competition/date: D1,10/02/2026"));
    }

    @Test
    void shouldListGeneratedFullCalendars() throws Exception {
        CompetitionDTO competition = new CompetitionDTO("BOTOLA_D1", "Botola Pro (1a Division)", "DB:BOTOLA_D1", true);
        when(competitionCatalogService.getCompetitionById("BOTOLA_D1")).thenReturn(Optional.of(competition));
        when(generatedCalendarStorageService.listGeneratedFullCalendars("BOTOLA_D1", "2025-26"))
            .thenReturn(List.of(
                new GeneratedCalendarFileDTO(
                    "fullCalendar.xlsx",
                    "v0",
                    "schBotolaD1/CalendarD1-v0",
                    1024L,
                    Instant.parse("2026-02-12T00:05:00Z"),
                    "download-1"
                )
            ));

        mockMvc.perform(get("/api/calendar/competitions/BOTOLA_D1/seasons/2025-26/generated-full-calendars"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].fileName").value("fullCalendar.xlsx"))
            .andExpect(jsonPath("$[0].version").value("v0"))
            .andExpect(jsonPath("$[0].downloadId").value("download-1"));
    }

    @Test
    void shouldDownloadGeneratedFullCalendar() throws Exception {
        CompetitionDTO competition = new CompetitionDTO("BOTOLA_D1", "Botola Pro (1a Division)", "DB:BOTOLA_D1", true);
        when(competitionCatalogService.getCompetitionById("BOTOLA_D1")).thenReturn(Optional.of(competition));
        when(generatedCalendarStorageService.readGeneratedFullCalendar("BOTOLA_D1", "2025-26", "download-1"))
            .thenReturn(Optional.of("full-calendar-xlsx".getBytes()));
        when(generatedCalendarStorageService.listGeneratedFullCalendars("BOTOLA_D1", "2025-26"))
            .thenReturn(List.of(
                new GeneratedCalendarFileDTO(
                    "fullCalendarD1.xlsx",
                    "v1",
                    "schBotolaD1/CalendarD1-v1",
                    1200L,
                    Instant.parse("2026-02-12T00:07:00Z"),
                    "download-1"
                )
            ));

        mockMvc.perform(get("/api/calendar/competitions/BOTOLA_D1/seasons/2025-26/generated-full-calendars/download-1"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"fullCalendarD1.xlsx\""));
    }

    @Test
    void shouldDownloadGeneratedZipWithZipContentType() throws Exception {
        CompetitionDTO competition = new CompetitionDTO("BOTOLA_D1", "Botola Pro (1a Division)", "DB:BOTOLA_D1", true);
        when(competitionCatalogService.getCompetitionById("BOTOLA_D1")).thenReturn(Optional.of(competition));
        when(generatedCalendarStorageService.readGeneratedFullCalendar("BOTOLA_D1", "2025-26", "download-zip"))
            .thenReturn(Optional.of(buildZipBytes()));
        when(generatedCalendarStorageService.listGeneratedFullCalendars("BOTOLA_D1", "2025-26"))
            .thenReturn(List.of(
                new GeneratedCalendarFileDTO(
                    "D1 MEN 2025-26 rounds to 8.zip",
                    "v1",
                    "schBotolaD1-v1",
                    1500L,
                    Instant.parse("2026-02-12T00:08:00Z"),
                    "download-zip"
                )
            ));

        mockMvc.perform(get("/api/calendar/competitions/BOTOLA_D1/seasons/2025-26/generated-full-calendars/download-zip"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/zip"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"D1 MEN 2025-26 rounds to 8.zip\""));
    }

    private byte[] buildZipBytes() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutput = new ZipOutputStream(output)) {
            zipOutput.putNextEntry(new ZipEntry("sample.txt"));
            zipOutput.write("sample".getBytes());
            zipOutput.closeEntry();
            zipOutput.finish();
        }
        return output.toByteArray();
    }
}
