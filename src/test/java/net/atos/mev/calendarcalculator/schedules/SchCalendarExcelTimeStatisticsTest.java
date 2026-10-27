package net.atos.mev.calendarcalculator.schedules;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


class SchCalendarExcelTimeStatisticsTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldCountTimeForBothHomeAndAwayTeams() throws Exception {
        Properties properties =
            ReferenceProperties.loadFromConfigFile(
                "schBotolaD2/SchMoroccoD2.properties"
            );

        SchEnvironment schEnv =
            ReferenceProperties.scheduling(properties);

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

            Row match = sheet.createRow(1);
            match.createCell(0).setCellValue("D2");
            match.createCell(1).setCellValue("1");
            match.createCell(2).setCellValue("Sat");
            match.createCell(3).setCellValue("26/09/2026");
            match.createCell(4).setCellValue("18:00");
            match.createCell(5).setCellValue("CAK");
            match.createCell(6).setCellValue("IZK");

            workbook.write(inputExcel);
        }

        SchCalendar calendar =
            SchCalendarExcelIO.loadFromExcel(
                new ByteArrayInputStream(
                    inputExcel.toByteArray()
                ),
                schEnv
            );

        Path output =
            tempDir.resolve("statisticsTimeD2.csv");

        SchCalendarExcelIO.timeStatisticsToCSV(
            calendar,
            output.toString()
        );

        List<String> lines =
            Files.readAllLines(output);

        assertThat(lines)
            .contains("Team,18:00");

        assertThat(lines)
            .contains("CAK,1");

        assertThat(lines)
            .contains("IZK,1");

        int totalAppearances =
            lines.stream()
                .skip(2)
                .map(line -> line.split(","))
                .mapToInt(fields -> Integer.parseInt(fields[1]))
                .sum();

        assertThat(totalAppearances)
            .isEqualTo(2);
    }
}
