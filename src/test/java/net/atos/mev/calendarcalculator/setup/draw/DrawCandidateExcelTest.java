package net.atos.mev.calendarcalculator.setup.draw;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import net.atos.mev.calendarcalculator.fileio.AssignmentsExcelMorocco;

class DrawCandidateExcelTest {

	@ParameterizedTest
	@CsvSource({
			"BOTOLA_D1,D1,16,MoroccoBotolaD1.xlsx,10",
			"BOTOLA_D2,D2,16,MoroccoBotolaD2.xlsx,10",
			"CNPFF_D1,F1,12,MoroccoCNPFF1.xlsx,8",
			"CNPFF_D2,F2,12,MoroccoCNPFF2.xlsx,8"
	})
	void preservesTemplatesAndWritesEveryMatch(String id, String code, int positions, String file, int height) throws Exception {
		List<String[]> matches = matches(positions);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		new DrawCandidateExcel().write(new DrawDtos.DrawLeague(id, code, 1), "2027-28", 42, positions, matches, out);
		try (var in = getClass().getResourceAsStream("/exportTemplate/" + file);
				var original = WorkbookFactory.create(in);
				var book = WorkbookFactory.create(new ByteArrayInputStream(out.toByteArray()))) {
			assertEquals(1, book.getNumberOfSheets());
			assertEquals(2, book.getAllPictures().size());
			var sheet = book.getSheetAt(0);
			var old = original.getSheetAt(0);
			assertEquals(old.getNumMergedRegions(), sheet.getNumMergedRegions());
			for (int i = 0; i < old.getNumMergedRegions(); i++) {
				assertEquals(old.getMergedRegion(i), sheet.getMergedRegion(i));
			}
			assertEquals(original.getPrintArea(0), book.getPrintArea(0));
			assertEquals("0042", sheet.getRow(3).getCell(15).getStringCellValue());
			assertTrue(sheet.getRow(2).getCell(0).getStringCellValue().endsWith("2027-28"));
			int perRound = positions / 2;
			for (int i = 0; i < matches.size(); i++) {
				String[] match = matches.get(i);
				int round = Integer.parseInt(match[0]) - 1;
				int first = round % (positions - 1);
				int row = 6 + first / 5 * height + i % perRound;
				int col = first % 5 * 2 + (round >= positions - 1 ? 11 : 0);
				assertEquals(match[1], sheet.getRow(row).getCell(col).getStringCellValue());
				assertEquals(match[2], sheet.getRow(row).getCell(col + 1).getStringCellValue());
				assertEquals(old.getRow(row).getCell(col).getCellStyle().getIndex(),
						sheet.getRow(row).getCell(col).getCellStyle().getIndex());
			}
		}
	}

	@Test
	void sharedCoordinatesMatchBothHistoricalLayouts() {
		int[][] expectedBotola = {
				{6,0}, {6,2}, {6,4}, {6,6}, {6,8},
				{16,0}, {16,2}, {16,4}, {16,6}, {16,8},
				{26,0}, {26,2}, {26,4}, {26,6}, {26,8},
				{6,11}, {6,13}, {6,15}, {6,17}, {6,19},
				{16,11}, {16,13}, {16,15}, {16,17}, {16,19},
				{26,11}, {26,13}, {26,15}, {26,17}, {26,19}
		};
		int[][] expectedWomen = {
				{6,0}, {6,2}, {6,4}, {6,6}, {6,8},
				{14,0}, {14,2}, {14,4}, {14,6}, {14,8}, {22,0},
				{6,11}, {6,13}, {6,15}, {6,17}, {6,19},
				{14,11}, {14,13}, {14,15}, {14,17}, {14,19}, {22,11}
		};
		assertArrayEquals(expectedBotola, AssignmentsExcelMorocco.roundStarts(15, 10));
		assertArrayEquals(expectedWomen, AssignmentsExcelMorocco.roundStarts(11, 8));
	}

	@ParameterizedTest
	@CsvSource({"BOTOLA_D1,16,10", "CNPFF_D1,12,8"})
	void omitsByeAndCompactsEachRound(String id, int positions, int height) throws Exception {
		List<String[]> matches = matches(positions);
		for (String[] match : matches) {
			if ("T0".equals(match[1])) {
				match[1] = "BYE";
			}
			if ("T0".equals(match[2])) {
				match[2] = "BYE";
			}
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		new DrawCandidateExcel().write(new DrawDtos.DrawLeague(id, "X", 1), "2026-27", 1, positions, matches, out);
		try (var book = WorkbookFactory.create(new ByteArrayInputStream(out.toByteArray()))) {
			int[] counts = new int[2 * (positions - 1)];
			var sheet = book.getSheetAt(0);
			for (String[] match : matches) {
				if ("BYE".equals(match[1]) || "BYE".equals(match[2])) {
					continue;
				}
				int round = Integer.parseInt(match[0]) - 1;
				int first = round % (positions - 1);
				int row = 6 + first / 5 * height + counts[round]++;
				int col = first % 5 * 2 + (round >= positions - 1 ? 11 : 0);
				assertEquals(match[1], sheet.getRow(row).getCell(col).getStringCellValue());
				assertEquals(match[2], sheet.getRow(row).getCell(col + 1).getStringCellValue());
			}
			for (int round = 0; round < counts.length; round++) {
				assertEquals(positions / 2 - 1, counts[round]);
				int first = round % (positions - 1);
				int row = 6 + first / 5 * height + counts[round];
				int col = first % 5 * 2 + (round >= positions - 1 ? 11 : 0);
				assertEquals("", sheet.getRow(row).getCell(col).toString());
				assertEquals("", sheet.getRow(row).getCell(col + 1).toString());
			}
		}
	}

	@ParameterizedTest
	@CsvSource({"OTHER,D1,16", "BOTOLA_D1,D1,6", "CNPFF_D1,F1,16"})
	void explicitlyUsesTabularFormatWhenNoCompatibleTemplate(String id, String code, int positions) throws Exception {
		List<String[]> matches = matches(positions);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		new DrawCandidateExcel().write(new DrawDtos.DrawLeague(id, code, 1), "2027-28", 7, positions, matches, out);
		try (var book = WorkbookFactory.create(new ByteArrayInputStream(out.toByteArray()))) {
			var sheet = book.getSheetAt(0);
			assertTrue(sheet.getRow(0).getCell(0).getStringCellValue().contains("sin plantilla antigua compatible"));
			assertEquals("2027-28", sheet.getRow(1).getCell(1).getStringCellValue());
			assertEquals(7, sheet.getRow(2).getCell(1).getNumericCellValue());
			assertEquals(matches.size() + 4, sheet.getLastRowNum());
			for (int i = 0; i < matches.size(); i++) {
				var row = sheet.getRow(i + 5);
				assertEquals(Integer.parseInt(matches.get(i)[0]), row.getCell(0).getNumericCellValue());
				assertEquals(matches.get(i)[1], row.getCell(1).getStringCellValue());
				assertEquals(matches.get(i)[2], row.getCell(2).getStringCellValue());
			}
		}
	}

	@Test
	void rejectsFixturesOutsideTemplateInsteadOfTruncating() {
		List<String[]> matches = List.<String[]>of(new String[] {"31", "A", "B"});
		assertThrows(IllegalArgumentException.class, () -> new DrawCandidateExcel().write(
				new DrawDtos.DrawLeague("BOTOLA_D1", "D1", 1), "2026-27", 1, 16, matches, new ByteArrayOutputStream()));
	}

	private static List<String[]> matches(int positions) {
		List<String> teams = new ArrayList<>();
		for (int i = 0; i < positions; i++) {
			teams.add("T" + i);
		}
		List<String[]> first = new ArrayList<>();
		List<String[]> second = new ArrayList<>();
		for (int round = 1; round < positions; round++) {
			for (int m = 0; m < positions / 2; m++) {
				String home = teams.get(m);
				String away = teams.get(positions - 1 - m);
				first.add(new String[] {String.valueOf(round), home, away});
				second.add(new String[] {String.valueOf(round + positions - 1), away, home});
			}
			teams.add(1, teams.remove(teams.size() - 1));
		}
		first.addAll(second);
		return first;
	}
}
