package net.atos.mev.calendarcalculator.setup.draw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.Tournament;
import net.atos.mev.calendarcalculator.checks.Check;
import net.atos.mev.calendarcalculator.checks.CheckProcessor;

class DrawEngineTest {

	private static List<String[]> teams(String csv) {
		List<String[]> out = new ArrayList<>();
		for (String c : csv.split(",")) {
			out.add(new String[] { c, c });
		}
		return out;
	}

	private static final DrawEngine.League F1 = new DrawEngine.League(3, "F1",
			teams("HANFF,ASFAR,FUS,WAC,RSB,UTS,AMLFF,SCC,HST,PFAM,ARAFF,USSB"), List.of(),
			List.of(List.of("WAC", "SCC"), List.of("ASFAR", "FUS"), List.of("HST", "UTS"), List.of("AMLFF", "HANFF"),
					List.of("RSB", "USSB"), List.of("PFAM", "ARAFF")));
	private static final DrawEngine.League F2 = new DrawEngine.League(4, "F2",
			teams("AGS,USYM,ANAFF,RCAZ,RCA,CRAH,ASSCHT,SCCM,CDSM,ALAMFF,ITFF,GINGA"), List.of(), List.of());

	private record Collector(List<DrawEngine.Candidate> found) implements DrawEngine.Listener {
		public void candidate(DrawEngine.Candidate candidate, int tries) {
			found.add(candidate);
		}

		public void info(String message) {
			System.out.println(message);
		}

		public boolean cancelled() {
			return false;
		}
	}

	@Test
	void generatesDistinctValidCandidatesWithDerbiesAndStadiumLinks() {
		Map<Long, List<DrawEngine.Link>> links = Map.of(4L,
				List.of(new DrawEngine.Link("AMLFF", "ALAMFF"), new DrawEngine.Link("RSB", "ITFF")));
		assertTrue(DrawEngine.validate(F1, List.of(F2), links).isEmpty());
		Collector c = new Collector(new ArrayList<>());
		DrawEngine.Summary summary = DrawEngine.run(F1, List.of(F2), links,
				new DrawEngine.Params(50, 100_000, 60_000, 200_000, null), c);
		assertEquals(50, summary.found());
		Set<String> distinct = new HashSet<>();
		for (DrawEngine.Candidate cand : c.found()) {
			assertEquals(2, cand.leagues().size());
			Map<Long, List<String[]>> fx = new HashMap<>();
			for (DrawEngine.LeagueResult r : cand.leagues()) {
				List<String[]> matches = DrawEngine.fixtures(r.pattern(), r.positionTeams());
				assertEquals(132, matches.size());
				Set<String> pairs = new HashSet<>();
				matches.forEach(m -> pairs.add(m[1] + "-" + m[2]));
				assertEquals(132, pairs.size(), "each ordered pairing once (double round robin)");
				fx.put(r.competitionSeasonId(), matches);
			}
			distinct.add(cand.leagues().get(0).pattern() + cand.leagues().get(0).positionTeams());
			for (List<String> derby : F1.derbies()) {
				for (int round = 1; round <= 22; round++) {
					assertNotEquals(home(fx.get(3L), round, derby.get(0)), home(fx.get(3L), round, derby.get(1)), "derby " + derby);
				}
			}
			for (DrawEngine.Link link : links.get(4L)) {
				for (int round = 1; round <= 22; round++) {
					assertNotEquals(home(fx.get(3L), round, link.parentTeam()), home(fx.get(4L), round, link.childTeam()), "link " + link);
				}
			}
		}
		assertTrue(distinct.size() > 1);
	}

	@Test
	void sixteenTeamsIsFastEnough() {
		DrawEngine.League d1 = new DrawEngine.League(1, "D1", teams("CODM,DHJ,FAR,FUS,HUSA,IRT,MAS,WST,RCA,RCAZ,RSB,UTS,WAC,MAT,KACM,USAT"),
				List.of("SeparateMatchesCheck,1,RCA,WAC,RSB,FAR,MAS", "MatchNotInRoundCheck,RCA,WAC,1,10,15,25"),
				List.of(List.of("WAC", "RCA"), List.of("FUS", "UTS"), List.of("WST", "FAR")));
		Collector c = new Collector(new ArrayList<>());
		long t0 = System.currentTimeMillis();
		DrawEngine.Summary summary = DrawEngine.run(d1, List.of(), Map.of(), new DrawEngine.Params(100, 100_000, 120_000, 200_000, null), c);
		System.out.println("16 teams: " + summary + " in " + (System.currentTimeMillis() - t0) + " ms");
		assertEquals(100, summary.found());
	}

	@Test
	void botolaDropsLinksThatContradictDerbiesAndFindsCandidates() {
		DrawEngine.League d1 = new DrawEngine.League(1, "D1", teams("CODM,DHJ,FAR,FUS,HUSA,IRT,MAS,WST,RCA,RCAZ,RSB,UTS,WAC,MAT,KACM,USAT"),
				List.of(), List.of(List.of("WAC", "RCA"), List.of("FUS", "UTS"), List.of("WST", "FAR"), List.of("HUSA", "USAT"),
						List.of("DHJ", "RCAZ"), List.of("IRT", "MAT"), List.of("MAS", "CODM")));
		DrawEngine.League d2 = new DrawEngine.League(2, "D2", teams("CAK,CJBG,JSM,KAC,MCO,OCS,USYM,SM,USMO,WAF,JSS,SCCM,IZK,OCK,OD,USB"),
				List.of(), List.of(List.of("USMO", "MCO"), List.of("KAC", "IZK"), List.of("OCK", "USB"), List.of("CJBG", "JSS"),
						List.of("OCS", "OD"), List.of("SM", "USYM"), List.of("CAK", "WAF")));
		List<DrawEngine.Link> links = new ArrayList<>();
		for (String l : "MAS/WAF,CODM/IZK,KACM/CJBG,RCA/JSS,WAC/SCCM,FAR/KAC,RSB/MCO,USAT/OD,FUS/SM,WST/USYM".split(",")) {
			links.add(new DrawEngine.Link(l.split("/")[0], l.split("/")[1]));
		}
		List<String> dropped = new ArrayList<>();
		Map<Long, List<DrawEngine.Link>> ok = DrawEngine.compatibleLinks(d1, List.of(d2), Map.of(2L, links), dropped);
		dropped.forEach(System.out::println);
		assertTrue(!dropped.isEmpty() && !ok.get(2L).isEmpty());
		Collector c = new Collector(new ArrayList<>());
		DrawEngine.Summary summary = DrawEngine.run(d1, List.of(d2), Map.of(2L, links),
				new DrawEngine.Params(10, 100_000, 60_000, 200_000, null), c);
		System.out.println("Botola D1+D2: " + summary + ", links kept " + ok.get(2L).size());
		assertEquals(10, summary.found());
	}

	@Test
	void invalidRuleIsReported() {
		DrawEngine.League bad = new DrawEngine.League(9, "X", teams("A,B,C,D"), List.of("DerbyTeamsCheck,A,ZZZ"), List.of());
		assertEquals(1, DrawEngine.validate(bad, List.of(), Map.of()).size());
	}

	@Test
	void unsupportedCheckDefinitionsAreNotDroppedSilently() {
		assertTrue(DrawEngine.isCheckDefinition("HalfMatchesAtHomeCheck"));
		assertFalse(DrawEngine.isCheckDefinition("Blackout"));
		DrawEngine.League unsupported = new DrawEngine.League(9, "X", teams("A,B,C,D"),
				List.of("HalfMatchesAtHomeCheck"), List.of());
		assertTrue(DrawEngine.validate(unsupported, List.of(), Map.of()).stream()
				.anyMatch(problem -> problem.contains("unsupported draw rule")));
	}

	@Test
	void allNinePersistableP1RulesHaveRegisteredParsers() {
		List<String> definitions = List.of("DerbyTeamsCheck,WAC,HANFF", "LimitHomeTogetherCheck,WAC,HANFF,2",
				"PlayTogetherTeamsCheck,WAC,HANFF", "MatchNotInRoundCheck,WAC,HANFF,1,12,22",
				"NotConsecutiveFarAwayCheck,WAC,HANFF", "LimitAgainstTeamsInRoundsCheck,1,2,WAC,HANFF,1,12",
				"LimitAgainstTeamsInRoundsHomeCheck,1,2,WAC,HANFF,1,12", "SeparateMatchesCheck,2,WAC,HANFF,RSB",
				"ForcePositionsCheck,WAC,4");
		for (String definition : definitions) {
			DrawEngine.League configured = new DrawEngine.League(9, "X", F1.teams(), List.of(definition), List.of());
			assertTrue(DrawEngine.validate(configured, List.of(), Map.of()).isEmpty(), definition);
		}
		assertEquals(9, DrawEngine.DRAW_CHECKS.size());
	}

	@Test
	void generatedCandidatesSatisfyEachActiveP1Rule() {
		List<String> definitions = List.of("DerbyTeamsCheck,A,B", "LimitHomeTogetherCheck,A,B,0", "PlayTogetherTeamsCheck,A,B",
				"MatchNotInRoundCheck,A,B,1,6", "NotConsecutiveFarAwayCheck,A,B,C",
				"LimitAgainstTeamsInRoundsCheck,0,2,A,B,1,6", "LimitAgainstTeamsInRoundsHomeCheck,0,2,A,B,1,6",
				"SeparateMatchesCheck,1,A,B,C", "ForcePositionsCheck,A,1");
		DrawEngine.League league = new DrawEngine.League(9, "X", teams("A,B,C,D,E,F"), List.of(), List.of());
		for (String definition : definitions) {
			DrawEngine.League configured = new DrawEngine.League(league.competitionSeasonId(), league.code(), league.teams(),
					List.of(definition), List.of());
			Collector collector = new Collector(new ArrayList<>());
			DrawEngine.Summary summary = DrawEngine.run(configured, List.of(), Map.of(),
					new DrawEngine.Params(1, 100_000, 10_000, 100_000, null), collector);
			assertEquals(1, summary.found(), definition);
			for (DrawEngine.Candidate candidate : collector.found()) {
				assertTrue(checkPasses(definition, candidate.leagues().getFirst()), definition);
			}
		}
	}

	@Test
	void malformedAndOutOfRangeP1DefinitionsFailValidationInsteadOfThrowing() {
		List<String> definitions = List.of("DerbyTeamsCheck,WAC,WAC", "LimitHomeTogetherCheck,WAC,RCA,-1",
				"PlayTogetherTeamsCheck,WAC", "MatchNotInRoundCheck,WAC,RCA,0", "MatchNotInRoundCheck,WAC,RCA,23",
				"NotConsecutiveFarAwayCheck,WAC,WAC", "LimitAgainstTeamsInRoundsCheck,1,999999999999,WAC,RCA,1",
				"LimitAgainstTeamsInRoundsHomeCheck,-1,2,WAC,RCA,1", "SeparateMatchesCheck,0,WAC,RCA",
				"SeparateMatchesCheck,12,WAC,RCA", "SeparateMatchesCheck,2,WAC,WAC", "ForcePositionsCheck,WAC,13");
		for (String definition : definitions) {
			DrawEngine.League configured = new DrawEngine.League(9, "X", F1.teams(), List.of(definition), List.of());
			assertTrue(DrawEngine.validate(configured, List.of(), Map.of()).stream()
					.anyMatch(problem -> problem.contains("invalid draw rule")), definition);
		}
	}

	@Test
	void contradictoryForcedPositionsAreRejectedBeforeDrawing() {
		DrawEngine.League configured = new DrawEngine.League(9, "X", F1.teams(),
				List.of("ForcePositionsCheck,WAC,4", "ForcePositionsCheck,HANFF,4"), List.of());
		assertTrue(DrawEngine.validate(configured, List.of(), Map.of()).stream()
				.anyMatch(problem -> problem.contains("both forced to position 4")));
	}

	@Test
	void federationPairsGenerateOneThousandCoordinatedCandidates() {
		DrawEngine.League first = new DrawEngine.League(10, "D1", teams("AA,BB,CC,DD,EE,FF"), List.of(), List.of());
		DrawEngine.League second = new DrawEngine.League(20, "D2", teams("GG,HH,II,JJ,KK,LL"), List.of(), List.of());
		List<DrawEngine.RequiredPair> pairs = List.of(new DrawEngine.RequiredPair(10, "AA", 20, "GG"));
		Collector collector = new Collector(new ArrayList<>());
		DrawEngine.Summary result = DrawEngine.run(first, List.of(second), Map.of(), pairs,
				new DrawEngine.Params(1000, 100_000, 60_000, 200_000, null), collector);
		assertEquals(1000, result.found());
		Set<String> signatures = new HashSet<>();
		for (DrawEngine.Candidate candidate : collector.found()) {
			List<String[]> firstMatches = DrawEngine.fixtures(candidate.leagues().get(0).pattern(), candidate.leagues().get(0).positionTeams());
			List<String[]> secondMatches = DrawEngine.fixtures(candidate.leagues().get(1).pattern(), candidate.leagues().get(1).positionTeams());
			for (int round = 1; round <= 10; round++) {
				assertTrue(!home(firstMatches, round, "AA") || !home(secondMatches, round, "GG"),
						"Federation pair cannot be home together in round " + round);
			}
			signatures.add(candidate.leagues().toString());
		}
		assertEquals(1000, signatures.size());
	}

	@Test
	void incompatibleHomePairsAreRejectedBeforeDrawingRatherThanIgnored() {
		DrawEngine.League triangle = new DrawEngine.League(10, "D1", teams("AA,BB,CC,DD,EE,FF"),
				List.of("DerbyTeamsCheck,AA,BB", "DerbyTeamsCheck,AA,CC", "DerbyTeamsCheck,BB,CC"), List.of());
		assertTrue(DrawEngine.validate(triangle, List.of(), Map.of()).stream().anyMatch(s -> s.contains("incompatible")));
		DrawEngine.League first = new DrawEngine.League(10, "D1", teams("AA,BB,CC,DD"), List.of(), List.of());
		DrawEngine.League second = new DrawEngine.League(20, "D2", teams("EE,FF,GG,HH"), List.of(), List.of());
		List<DrawEngine.RequiredPair> impossible = List.of(new DrawEngine.RequiredPair(10, "AA", 20, "EE"),
				new DrawEngine.RequiredPair(10, "BB", 20, "EE"));
		assertTrue(DrawEngine.validate(first, List.of(second), Map.of(), impossible).stream().anyMatch(s -> s.contains("none will be ignored")));
		assertThrows(IllegalArgumentException.class, () -> DrawEngine.run(first, List.of(second), Map.of(), impossible,
				new DrawEngine.Params(1, 10, 1000, 1000, null), new Collector(new ArrayList<>())));
	}

	@Test
	void federationPairsBetweenDependentLeaguesAreEnforced() {
		DrawEngine.League first = new DrawEngine.League(10, "D1", teams("AA,BB,CC,DD"), List.of(), List.of());
		DrawEngine.League second = new DrawEngine.League(20, "D2", teams("EE,FF,GG,HH"), List.of(), List.of());
		DrawEngine.League third = new DrawEngine.League(30, "D3", teams("II,JJ,KK,LL"), List.of(), List.of());
		List<DrawEngine.RequiredPair> pairs = List.of(new DrawEngine.RequiredPair(20, "EE", 30, "II"));
		Collector collector = new Collector(new ArrayList<>());
		DrawEngine.Summary result = DrawEngine.run(first, List.of(second, third), Map.of(), pairs,
				new DrawEngine.Params(10, 100_000, 30_000, 200_000, null), collector);
		assertEquals(10, result.found());
		for (DrawEngine.Candidate candidate : collector.found()) {
			List<String[]> secondMatches = DrawEngine.fixtures(candidate.leagues().get(1).pattern(), candidate.leagues().get(1).positionTeams());
			List<String[]> thirdMatches = DrawEngine.fixtures(candidate.leagues().get(2).pattern(), candidate.leagues().get(2).positionTeams());
			for (int round = 1; round <= 6; round++) {
				assertNotEquals(home(secondMatches, round, "EE"), home(thirdMatches, round, "II"));
			}
		}
		assertTrue(DrawEngine.validate(first, List.of(), Map.of(), pairs).stream().anyMatch(s -> s.contains("Include the related")));
		DrawEngine.League larger = new DrawEngine.League(20, "D2", teams("EE,FF,GG,HH,MM,NN"), List.of(), List.of());
		assertTrue(DrawEngine.validate(first, List.of(larger), Map.of(),
				List.of(new DrawEngine.RequiredPair(10, "AA", 20, "EE"))).stream().anyMatch(s -> s.contains("same number")));
	}

	private static boolean home(List<String[]> matches, int round, String team) {
		for (String[] m : matches) {
			if (m[0].equals(String.valueOf(round)) && m[1].equals(team)) {
				return true;
			}
		}
		return false;
	}

	private static boolean checkPasses(String definition, DrawEngine.LeagueResult result) {
		List<String[]> teamRows = result.positionTeams().stream().map(code -> new String[] { code, code }).toList();
		Environment env = Environment.inMemory("X", "X", teamRows);
		Tournament tournament = Tournament.readFromLine(env, result.pattern());
		for (int i = 0; i < result.positionTeams().size(); i++) {
			tournament.mapPosToTeams.put(i + 1, env.searchTeamByCode(result.positionTeams().get(i)));
		}
		Check check = CheckProcessor.createCheck(env, definition);
		return check != null && check.check(tournament);
	}

	@ParameterizedTest
	@ValueSource(strings = {"DerbyTeamsCheck,A,B", "LimitHomeTogetherCheck,A,B,0",
			"PlayTogetherTeamsCheck,A,B", "MatchNotInRoundCheck,A,B,1,6",
			"NotConsecutiveFarAwayCheck,A,B,C", "LimitAgainstTeamsInRoundsCheck,0,2,A,B,1,6",
			"LimitAgainstTeamsInRoundsHomeCheck,0,2,A,B,1,6", "SeparateMatchesCheck,2,A,B,C",
			"ForcePositionsCheck,A,1"})
	void eachHardRuleRemovesObservableCounterexamplesWithoutChangingTheAlgorithm(String definition) {
		List<String> codes = definition.startsWith("SeparateMatches") ? List.of("A", "B", "C", "D", "E", "F", "G", "H")
				: List.of("A", "B", "C", "D", "E", "F");
		DrawEngine.League disabled = new DrawEngine.League(9, "X", teams(String.join(",", codes)), List.of(), List.of());
		Collector baseline = new Collector(new ArrayList<>());
		assertEquals(1, DrawEngine.run(disabled, List.of(), Map.of(),
				new DrawEngine.Params(1, 100_000, 10_000, 100_000, null), baseline).found());
		DrawEngine.LeagueResult template = baseline.found().getFirst().leagues().getFirst();
		List<List<String>> permutations = new ArrayList<>();
		permute(new ArrayList<>(codes), 0, permutations);
		assertEquals(codes.size() == 6 ? 720 : 40320, permutations.size());
		assertTrue(permutations.stream().anyMatch(order ->
				!checkPasses(definition, new DrawEngine.LeagueResult(9, template.pattern(), order))),
				"The disabled rule must admit a counterexample: " + definition);
		DrawEngine.League enabled = new DrawEngine.League(9, "X", disabled.teams(),
				List.of(definition, definition), List.of());
		Collector constrained = new Collector(new ArrayList<>());
		assertEquals(10, DrawEngine.run(enabled, List.of(), Map.of(),
				new DrawEngine.Params(10, 100_000, 10_000, 100_000, null), constrained).found());
		assertTrue(constrained.found().stream().allMatch(candidate ->
				checkPasses(definition, candidate.leagues().getFirst())), definition);
	}

	private static void permute(List<String> order, int index, List<List<String>> output) {
		if (index == order.size()) {
			output.add(List.copyOf(order));
			return;
		}
		for (int i = index; i < order.size(); i++) {
			java.util.Collections.swap(order, index, i);
			permute(order, index + 1, output);
			java.util.Collections.swap(order, index, i);
		}
	}

	@Test
	void matchNotInRoundAcceptsSecondLegRounds() {
		// 12 equipos: jornada 12 = primera de la segunda vuelta (antes: ArrayIndexOutOfBounds)
		DrawEngine.League f1 = new DrawEngine.League(3, "F1", F1.teams(), List.of("MatchNotInRoundCheck,WAC,FUS,1,12,22"), F1.derbies());
		assertTrue(DrawEngine.validate(f1, List.of(), Map.of()).isEmpty());
		Collector c = new Collector(new ArrayList<>());
		DrawEngine.Summary summary = DrawEngine.run(f1, List.of(), Map.of(), new DrawEngine.Params(10, 100_000, 60_000, 200_000, null), c);
		assertEquals(10, summary.found());
		for (DrawEngine.Candidate cand : c.found()) {
			DrawEngine.LeagueResult r = cand.leagues().get(0);
			for (String[] m : DrawEngine.fixtures(r.pattern(), r.positionTeams())) {
				boolean pair = (m[1].equals("WAC") && m[2].equals("FUS")) || (m[1].equals("FUS") && m[2].equals("WAC"));
				int round = Integer.parseInt(m[0]);
				assertTrue(!pair || (round != 1 && round != 12 && round != 22), "WAC-FUS in forbidden round " + round);
			}
		}
	}
}
