package net.atos.mev.calendarcalculator.setup.draw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.zip.ZipInputStream;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.simple.JdbcClient;

import net.atos.mev.calendarcalculator.setup.SeasonSetupRepository;
import net.atos.mev.calendarcalculator.setup.rules.RuleScopeService;

class DrawBatchZipTest {

	@TempDir
	Path temp;

	@Test
	void exportsOneThousandRealCandidatesWithMatchingXlsxAndCsv() throws Exception {
		checkZip(1000, List.of(new DrawDtos.DrawLeague("BOTOLA_D1", "D1", 1)));
	}

	@Test
	void exportsEachCandidateAndBothDependentLeagues() throws Exception {
		checkZip(2, List.of(new DrawDtos.DrawLeague("BOTOLA_D1", "D1", 1),
				new DrawDtos.DrawLeague("BOTOLA_D2", "D2", 2)));
	}

	private void checkZip(int count, List<DrawDtos.DrawLeague> leagues) throws Exception {
		List<String[]> teams = new ArrayList<>();
		for (int i = 0; i < 16; i++) {
			teams.add(new String[] {"T" + i, "Team " + i});
		}
		Map<Integer, DrawEngine.LeagueResult> generated = new HashMap<>();
		var summary = DrawEngine.run(new DrawEngine.League(1, "D1", teams, List.of(), List.of()), List.of(), Map.of(),
				new DrawEngine.Params(count, 100_000, 60_000, 200_000, null), new DrawEngine.Listener() {
					public void candidate(DrawEngine.Candidate candidate, int tries) {
						generated.put(candidate.no(), candidate.leagues().get(0));
					}

					public void info(String message) {
					}

					public boolean cancelled() {
						return false;
					}
				});
		assertEquals(count, summary.found());
		DrawBatchRepository repo = mock(DrawBatchRepository.class);
		when(repo.require("batch")).thenReturn(new DrawDtos.DrawBatch("batch", "BOTOLA_D1", "2027-28", "COMPLETED",
				count, count, summary.tries(), null, null, Instant.now(), Instant.now(), null, leagues));
		Map<String, String> names = new HashMap<>();
		teams.forEach(t -> names.put(t[0], t[1]));
		for (var league : leagues) {
			when(repo.teamNames(league.competitionSeasonId())).thenReturn(names);
		}
		doAnswer(invocation -> {
			BiConsumer<Integer, DrawBatchRepository.StoredLeague> consumer = invocation.getArgument(1);
			for (int no = 1; no <= count; no++) {
				var candidate = generated.get(no);
				for (var league : leagues) {
					consumer.accept(no, new DrawBatchRepository.StoredLeague(league.competitionSeasonId(),
							candidate.pattern(), candidate.positionTeams()));
				}
			}
			return null;
		}).when(repo).forEachCandidate(eq("batch"), any());
		var service = new DrawBatchService(repo, mock(SeasonSetupRepository.class), mock(JdbcClient.class), mock(RuleScopeService.class));
		Path file = temp.resolve("candidates.zip");
		long start = System.nanoTime();
		try (var out = Files.newOutputStream(file)) {
			service.writeZip("batch", out);
		} finally {
			service.shutdown();
		}
		int xlsxCount = 0;
		int csvCount = 0;
		int aggregateCount = 0;
		boolean readme = false;
		try (var zip = new ZipInputStream(Files.newInputStream(file), StandardCharsets.UTF_8)) {
			for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
				String name = entry.getName();
				byte[] bytes = zip.readAllBytes();
				if (name.equals("LEEME.txt")) {
					String text = new String(bytes, StandardCharsets.UTF_8);
					assertTrue(text.contains("XLSX y un CSV"));
					assertTrue(text.contains("sin fechas ni horarios P2"));
					readme = true;
				} else if (name.endsWith(".xlsx")) {
					xlsxCount++;
					int no = Integer.parseInt(name.split("/")[1]);
					List<String[]> expected = DrawEngine.fixtures(generated.get(no).pattern(), generated.get(no).positionTeams());
					try (var book = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
						assertEquals(2, book.getAllPictures().size());
						var sheet = book.getSheetAt(0);
						assertEquals(String.format("%04d", no), sheet.getRow(3).getCell(15).getStringCellValue());
						assertTrue(sheet.getRow(2).getCell(0).getStringCellValue().endsWith("2027-28"));
						assertEquals(240, expected.size());
						for (int i = 0; i < expected.size(); i++) {
							String[] m = expected.get(i);
							int r = Integer.parseInt(m[0]) - 1;
							int row = 6 + (r % 15) / 5 * 10 + i % 8;
							int col = (r % 15) % 5 * 2 + (r >= 15 ? 11 : 0);
							assertEquals(m[1], sheet.getRow(row).getCell(col).getStringCellValue(), name);
							assertEquals(m[2], sheet.getRow(row).getCell(col + 1).getStringCellValue(), name);
						}
					}
				} else if (name.startsWith("candidatos/") && name.endsWith(".csv")) {
					csvCount++;
					int no = Integer.parseInt(name.split("/")[1]);
					List<String[]> expected = DrawEngine.fixtures(generated.get(no).pattern(), generated.get(no).positionTeams());
					List<String> rows = new String(bytes, StandardCharsets.UTF_8).lines().toList();
					assertEquals(expected.size() + 2, rows.size());
					assertEquals("sep=,", rows.get(0));
					for (int i = 0; i < expected.size(); i++) {
						String[] m = expected.get(i);
						assertEquals(String.join(",", m) + "," + names.get(m[1]) + "," + names.get(m[2]), rows.get(i + 2));
					}
				} else if (name.endsWith("_todos.csv")) {
					aggregateCount++;
					assertEquals(count * 240L + 2, new String(bytes, StandardCharsets.UTF_8).lines().count());
				} else {
					throw new AssertionError("Unexpected ZIP entry: " + name);
				}
			}
		}
		assertTrue(readme);
		assertEquals(count * leagues.size(), xlsxCount);
		assertEquals(count * leagues.size(), csvCount);
		assertEquals(leagues.size(), aggregateCount);
		System.out.printf("P1 ZIP: %d XLSX + %d CSV, %d bytes, render + verification %.2f s%n",
				xlsxCount, csvCount, Files.size(file), (System.nanoTime() - start) / 1_000_000_000.0);
	}
}
