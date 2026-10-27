package net.atos.mev.calendarcalculator.schedules;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Properties;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import net.atos.mev.calendarcalculator.Match;

class SchCalendarExcelFixturePreservationTest {

    @Test
    void shouldPreserveOfficialHomeAwayWhenHourIsEmpty() throws Exception {
        Properties properties =
            ReferenceProperties.loadFromConfigFile(
                "schBotolaD1/SchMoroccoD1.properties"
            );

        SchEnvironment schEnv =
            ReferenceProperties.scheduling(properties);

        Match officialMatch =
            schEnv.tour.rounds[0].matches[0];

        String expectedHome =
            officialMatch.getTeam1().getCode();

        String expectedAway =
            officialMatch.getTeam2().getCode();

        ByteArrayOutputStream inputExcel =
            new ByteArrayOutputStream();

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Calendar");

            Row header = sheet.createRow(0);
            String[] headers = {
                "Competition",
                "Round",
                "Weekday",
                "Date",
                "Hour",
                "Domicile",
                "Exterieur"
            };

            for (int i = 0; i < headers.length; i++) {
                header.createCell(i).setCellValue(headers[i]);
            }

            Row row = sheet.createRow(1);

            row.createCell(0).setCellValue(schEnv.env.getCode());
            row.createCell(1).setCellValue("1");
            row.createCell(2).setCellValue("Sat");
            row.createCell(3).setCellValue("26/09/2026");

            // La hora todavía NO está decidida.
            row.createCell(4).setCellValue("");

            // Estos equipos proceden del sorteo oficial.
            // El algoritmo NO puede cambiarlos.
            row.createCell(5).setCellValue(expectedHome);
            row.createCell(6).setCellValue(expectedAway);

            workbook.write(inputExcel);
        }

        SchCalendar calendar =
            SchCalendarExcelIO.loadFromExcel(
                new ByteArrayInputStream(inputExcel.toByteArray()),
                schEnv
            );

        Matchday matchday =
            calendar.getMatchdays()[
                calendar.dateNumber("26/09/2026")
            ];

        assertThat(matchday.getMatches())
            .as("El partido oficial no puede desaparecer por tener la hora vacía")
            .hasSize(1);

        SchMatch loadedMatch =
            matchday.getMatches().get(0);

        assertThat(
            loadedMatch.getTheMatch().getTeam1().getCode()
        ).isEqualTo(expectedHome);

        assertThat(
            loadedMatch.getTheMatch().getTeam2().getCode()
        ).isEqualTo(expectedAway);

        assertThat(loadedMatch.getTime())
            .as("Hora vacía debe convertirse en hora pendiente")
            .isEqualTo(ScheduleMoroccoAlg.EMPTYTIMESLOT);

        assertThat(loadedMatch.isConfirmed())
            .isFalse();
    }
}
