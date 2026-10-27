package net.atos.mev.calendarcalculator.schedules;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Iterator;
import java.util.Properties;

import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;


class SchCalendarExcelIOTest {

        @Test
        void shouldPreserveCompleteOtherCompetitionRowsWhenExportingCalendar() throws Exception {
                Properties properties = ReferenceProperties.loadFromConfigFile("schBotolaD2/SchMoroccoD2.properties");
                SchEnvironment schEnv = ReferenceProperties.scheduling(properties);

                ByteArrayOutputStream inputExcel = new ByteArrayOutputStream();
                try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                        Sheet sheet = workbook.createSheet("Calendar");
                        Row header = sheet.createRow(0);
                        String[] headers = { "Competition", "Round", "Weekday", "Date", "Hour", "Domicile", "Exterieur" };
                        for (int i = 0; i < headers.length; i++) {
                                header.createCell(i).setCellValue(headers[i]);
                        }

                        Row d2Row = sheet.createRow(1);
                        d2Row.createCell(0).setCellValue("D2");
                        d2Row.createCell(1).setCellValue("1");
                        d2Row.createCell(2).setCellValue("Sat");
                        d2Row.createCell(3).setCellValue("26/09/2026");
                        d2Row.createCell(4).setCellValue("");
                        d2Row.createCell(5).setCellValue("");
                        d2Row.createCell(6).setCellValue("");

                        Row d1Row = sheet.createRow(2);
                        d1Row.createCell(0).setCellValue("D1");
                        d1Row.createCell(1).setCellValue("1");
                        d1Row.createCell(2).setCellValue("Sat");
                        d1Row.createCell(3).setCellValue("26/09/2026");
                        d1Row.createCell(4).setCellValue("18:00");
                        d1Row.createCell(5).setCellValue("KACM");
                        d1Row.createCell(6).setCellValue("RCA");

                        workbook.write(inputExcel);
                }

                SchCalendar calendar = SchCalendarExcelIO.loadFromExcel(new ByteArrayInputStream(inputExcel.toByteArray()), schEnv);
                OtherCompetitionDay otherCompetitionDay = calendar.getMatchdays()[calendar.dateNumber("26/09/2026")].getOtherMatches().get(0);
                assertThat(otherCompetitionDay.getHour()).isEqualTo("18:00");
                assertThat(otherCompetitionDay.getTeamHome()).isEqualTo("KACM");
                assertThat(otherCompetitionDay.getTeamAway()).isEqualTo("RCA");
                assertThat(otherCompetitionDay.getTeams()).containsExactly("KACM", "RCA");

                ByteArrayOutputStream outputExcel = new ByteArrayOutputStream();
                SchCalendarExcelIO.matchdaysToExcel(calendar, new ByteArrayInputStream(inputExcel.toByteArray()), outputExcel, schEnv);

                try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(outputExcel.toByteArray()))) {
                        Sheet sheet = workbook.getSheetAt(0);
                        Iterator<Row> iterator = sheet.rowIterator();
                        iterator.next();
                        Row preservedD1Row = null;
                        while (iterator.hasNext()) {
                                Row row = iterator.next();
                                if ("D1".equals(row.getCell(0).getStringCellValue())) {
                                        preservedD1Row = row;
                                        break;
                                }
                        }

                        assertThat(preservedD1Row).isNotNull();
                        assertThat(preservedD1Row.getCell(1).getStringCellValue()).isEqualTo("1");
                        assertThat(preservedD1Row.getCell(3).getStringCellValue()).isEqualTo("26/09/2026");
                        assertThat(preservedD1Row.getCell(4).getStringCellValue()).isEqualTo("18:00");
                        assertThat(preservedD1Row.getCell(5).getStringCellValue()).isEqualTo("KACM");
                        assertThat(preservedD1Row.getCell(6).getStringCellValue()).isEqualTo("RCA");
                }
        }

        @Test
        void shouldPreserveTemplateRowsBeyondGeneratedCalendar() throws Exception {
                Properties properties = ReferenceProperties.loadFromConfigFile("schBotolaD2/SchMoroccoD2.properties");
                SchEnvironment schEnv = ReferenceProperties.scheduling(properties);

                byte[] templateBytes = createTemplateWithTrailingRows();
                SchCalendar calendar = SchCalendarExcelIO.loadFromExcel(new ByteArrayInputStream(templateBytes), schEnv);

                ByteArrayOutputStream outputExcel = new ByteArrayOutputStream();
                SchCalendarExcelIO.matchdaysToExcel(calendar, new ByteArrayInputStream(templateBytes), outputExcel, schEnv);

                try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(outputExcel.toByteArray()))) {
                        Sheet sheet = workbook.getSheetAt(0);
                        Row untouchedRow = sheet.getRow(3);

                        assertThat(untouchedRow).isNotNull();
                        assertThat(untouchedRow.getCell(7).getStringCellValue()).isEqualTo("TEMPLATE_NOTE");
                        assertThat(sheet.getLastRowNum()).isGreaterThanOrEqualTo(3);
                }
        }

        @Test
        void shouldPreserveExistingCellStyleWhenRewritingTemplateRows() throws Exception {
                Properties properties = ReferenceProperties.loadFromConfigFile("schBotolaD2/SchMoroccoD2.properties");
                SchEnvironment schEnv = ReferenceProperties.scheduling(properties);

                byte[] templateBytes = createStyledTemplate();
                SchCalendar calendar = SchCalendarExcelIO.loadFromExcel(new ByteArrayInputStream(templateBytes), schEnv);

                ByteArrayOutputStream outputExcel = new ByteArrayOutputStream();
                SchCalendarExcelIO.matchdaysToExcel(calendar, new ByteArrayInputStream(templateBytes), outputExcel, schEnv);

                try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(outputExcel.toByteArray()))) {
                        Sheet sheet = workbook.getSheetAt(0);
                        Row rewrittenRow = sheet.getRow(1);

                        assertThat(rewrittenRow.getCell(0).getStringCellValue()).isEqualTo("D2");
                        assertThat(rewrittenRow.getCell(0).getCellStyle().getFillForegroundColor()).isEqualTo(IndexedColors.YELLOW.getIndex());
                }
        }

        private static byte[] createTemplateWithTrailingRows() throws Exception {
                ByteArrayOutputStream inputExcel = new ByteArrayOutputStream();
                try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                        Sheet sheet = workbook.createSheet("Calendar");
                        Row header = sheet.createRow(0);
                        String[] headers = { "Competition", "Round", "Weekday", "Date", "Hour", "Domicile", "Exterieur", "Notes" };
                        for (int i = 0; i < headers.length; i++) {
                                header.createCell(i).setCellValue(headers[i]);
                        }

                        Row d2Row = sheet.createRow(1);
                        d2Row.createCell(0).setCellValue("D2");
                        d2Row.createCell(1).setCellValue("1");
                        d2Row.createCell(2).setCellValue("Sat");
                        d2Row.createCell(3).setCellValue("26/09/2026");
                        d2Row.createCell(4).setCellValue("");
                        d2Row.createCell(5).setCellValue("");
                        d2Row.createCell(6).setCellValue("");

                        Row otherCompetitionRow = sheet.createRow(2);
                        otherCompetitionRow.createCell(0).setCellValue("D1");
                        otherCompetitionRow.createCell(1).setCellValue("1");
                        otherCompetitionRow.createCell(2).setCellValue("Sat");
                        otherCompetitionRow.createCell(3).setCellValue("26/09/2026");
                        otherCompetitionRow.createCell(4).setCellValue("18:00");
                        otherCompetitionRow.createCell(5).setCellValue("KACM");
                        otherCompetitionRow.createCell(6).setCellValue("RCA");

                        Row untouchedRow = sheet.createRow(3);
                        untouchedRow.createCell(7).setCellValue("TEMPLATE_NOTE");

                        workbook.write(inputExcel);
                }
                return inputExcel.toByteArray();
        }

        private static byte[] createStyledTemplate() throws Exception {
                ByteArrayOutputStream inputExcel = new ByteArrayOutputStream();
                try (XSSFWorkbook workbook = new XSSFWorkbook()) {
                        Sheet sheet = workbook.createSheet("Calendar");
                        Row header = sheet.createRow(0);
                        String[] headers = { "Competition", "Round", "Weekday", "Date", "Hour", "Domicile", "Exterieur" };
                        for (int i = 0; i < headers.length; i++) {
                                header.createCell(i).setCellValue(headers[i]);
                        }

                        Row d2Row = sheet.createRow(1);
                        XSSFCellStyle style = workbook.createCellStyle();
                        style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
                        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                        for (int i = 0; i < 7; i++) {
                                d2Row.createCell(i).setCellStyle(style);
                        }
                        d2Row.getCell(0).setCellValue("D2");
                        d2Row.getCell(1).setCellValue("1");
                        d2Row.getCell(2).setCellValue("Sat");
                        d2Row.getCell(3).setCellValue("26/09/2026");
                        d2Row.getCell(4).setCellValue("");
                        d2Row.getCell(5).setCellValue("");
                        d2Row.getCell(6).setCellValue("");

                        workbook.write(inputExcel);
                }
                return inputExcel.toByteArray();
        }
}
