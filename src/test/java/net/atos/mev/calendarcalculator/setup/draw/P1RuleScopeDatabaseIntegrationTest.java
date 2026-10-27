package net.atos.mev.calendarcalculator.setup.draw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.Tournament;
import net.atos.mev.calendarcalculator.checks.Check;
import net.atos.mev.calendarcalculator.checks.CheckProcessor;
import net.atos.mev.calendarcalculator.setup.MasterDataController;
import net.atos.mev.calendarcalculator.setup.MasterDataService;
import net.atos.mev.calendarcalculator.setup.SetupCatalogRepository;
import net.atos.mev.calendarcalculator.setup.SetupDtos;
import net.atos.mev.calendarcalculator.setup.SetupExceptions;
import net.atos.mev.calendarcalculator.setup.rules.RuleScopeDtos;
import net.atos.mev.calendarcalculator.setup.rules.RuleScopeRepository;
import net.atos.mev.calendarcalculator.setup.rules.RuleScopeService;

/**
 * Matriz P1 contra BD real: las asignaciones persistidas llegan al motor de sorteo solo en su ámbito
 * (federación, temporada, competición, grupo de coordinación) y su efecto se comprueba en los candidatos generados.
 */
@SpringBootTest
@Transactional
class P1RuleScopeDatabaseIntegrationTest {

	private static final String S1 = "2041-42";
	private static final String S2 = "2042-43";
	private static final List<String> X = List.of("XQ1", "XQ2", "XQ3", "XQ4", "XQ5", "XQ6");
	private static final List<String> Y = List.of("YQ1", "YQ2", "YQ3", "YQ4", "YQ5", "YQ6");

	@Autowired DrawBatchService draws;
	@Autowired RuleScopeService rules;
	@Autowired RuleScopeRepository ruleRepo;
	@Autowired SetupCatalogRepository catalog;
	@Autowired MasterDataService masters;
	@Autowired JdbcClient jdbc;

	@BeforeEach
	void fixture() {
		league("PQA", "DEMO", S1, null, X);
		league("PQA", "DEMO", S2, null, X);
		league("PQB", "DEMO", S1, "PQA", Y);
		league("PQC", "FRMF", S1, null, X);
	}

	private void league(String id, String federation, String season, String dependsOn, List<String> teams) {
		if (jdbc.sql("SELECT COUNT(*) FROM competition WHERE id = :id").param("id", id).query(Integer.class).single() == 0) {
			jdbc.sql("""
					INSERT INTO competition (id, code, name, storage_dir, output_suffix, sort_order, federation_code)
					VALUES (:id, :id, :id, 'p1-scope-test', '', 998, :f)""").param("id", id).param("f", federation).update();
		}
		masters.createSeason(id, season, new MasterDataController.CreateSeason(LocalDate.of(2041, 8, 1), LocalDate.of(2042, 6, 30),
				LocalDate.of(2041, 9, 1), LocalDate.of(2042, 1, 1)));
		long cs = jdbc.sql("SELECT id FROM competition_season WHERE competition_id = :c AND season_id = :s")
				.param("c", id).param("s", season).query(Long.class).single();
		if (dependsOn != null) {
			jdbc.sql("UPDATE competition_season SET depends_on_competition_id = :p WHERE id = :cs").param("p", dependsOn).param("cs", cs).update();
		}
		for (int i = 0; i < teams.size(); i++) {
			String code = teams.get(i);
			if (jdbc.sql("SELECT COUNT(*) FROM team WHERE code = :c").param("c", code).query(Integer.class).single() == 0) {
				jdbc.sql("INSERT INTO team (code, name) VALUES (:c, :c)").param("c", code).update();
			}
			jdbc.sql("""
					INSERT INTO competition_season_team (competition_season_id, team_code, list_order, display_name, no_light, optimize_times)
					VALUES (:cs, :t, :o, :t, :nl, :ot)""").param("cs", cs).param("t", code).param("o", i).param("nl", false).param("ot", true).update();
		}
	}

	private static RuleScopeDtos.AssignmentRequest request(String scope, String competition, String season, String type, boolean mandatory,
			boolean enabled, String exceptionOf, String a, String b, String team, String teams, String rounds, Integer minRounds,
			Integer intParam) {
		return new RuleScopeDtos.AssignmentRequest(scope, competition, season, type, mandatory, null, null, enabled, exceptionOf, a, b, team,
				teams, rounds, minRounds, intParam, null, null, null, null, null);
	}

	private static RuleScopeDtos.AssignmentRequest matchNotInRound(String scope, String competition, String season, boolean enabled) {
		return request(scope, competition, season, "MatchNotInRound", false, enabled, null, "XQ1", "XQ2", null, null, "1,6", null, null);
	}

	private List<String> checks(String competition, String season) {
		return draws.input(competition, season, false).main().checkDefinitions();
	}

	private static final Map<String, RuleScopeDtos.AssignmentRequest> P1 = Map.of(
			"DerbyTeamsCheck,XQ1,XQ2", request("COMPETITION_SEASON", "PQA", S1, "DerbyTeams", false, true, null, "XQ1", "XQ2", null, null, null, null, null),
			"MatchNotInRoundCheck,XQ1,XQ2,1,6", matchNotInRound("COMPETITION_SEASON", "PQA", S1, true),
			"SeparateMatchesCheck,1,XQ1,XQ2,XQ3", request("COMPETITION_SEASON", "PQA", S1, "SeparateMatches", false, true, null, null, null, null, "XQ1,XQ2,XQ3", null, 1, null),
			"LimitHomeTogetherCheck,XQ1,XQ2,0", request("COMPETITION_SEASON", "PQA", S1, "LimitHomeTogether", false, true, null, "XQ1", "XQ2", null, null, null, null, 0),
			"PlayTogetherTeamsCheck,XQ1,XQ2", request("COMPETITION_SEASON", "PQA", S1, "PlayTogether", false, true, null, "XQ1", "XQ2", null, null, null, null, null),
			"NotConsecutiveFarAwayCheck,XQ1,XQ2,XQ3", request("COMPETITION_SEASON", "PQA", S1, "NotConsecutiveFarAway", false, true, null, null, null, null, "XQ1,XQ2,XQ3", null, null, null),
			"LimitAgainstTeamsInRoundsCheck,0,2,XQ1,XQ2,1,6", request("COMPETITION_SEASON", "PQA", S1, "LimitAgainstTeamsInRounds", false, true, null, null, null, null, "XQ1,XQ2", "1,6", null, 0),
			"LimitAgainstTeamsInRoundsHomeCheck,0,2,XQ1,XQ2,1,6", request("COMPETITION_SEASON", "PQA", S1, "LimitAgainstTeamsInRoundsHome", false, true, null, null, null, null, "XQ1,XQ2", "1,6", null, 0),
			"ForcePositionsCheck,XQ1,1", request("COMPETITION_SEASON", "PQA", S1, "ForcePositions", false, true, null, null, null, "XQ1", null, null, null, 1));

	@Test
	void everyPersistedP1TypeReachesOnlyItsCompetitionSeasonAndShapesTheGeneratedDraw() {
		assertEquals(9, P1.size());
		assertEquals(9, DrawEngine.DRAW_CHECKS.size());
		for (var entry : P1.entrySet()) {
			String definition = entry.getKey();
			var saved = rules.create("DEMO", entry.getValue());
			assertTrue(checks("PQA", S1).contains(definition), "missing in PQA/" + S1 + ": " + definition);
			assertFalse(checks("PQA", S2).contains(definition), "leaked to another season: " + definition);
			assertFalse(checks("PQC", S1).contains(definition), "leaked to another federation: " + definition);
			assertFalse(checks("PQB", S1).contains(definition), "leaked to another competition: " + definition);

			DrawBatchService.Input input = draws.input("PQA", S1, false);
			assertTrue(DrawEngine.validate(input.main(), input.dependents(), input.links(), input.requiredPairs()).isEmpty(), definition);
			List<DrawEngine.Candidate> found = new ArrayList<>();
			DrawEngine.Summary summary = DrawEngine.run(input.main(), List.of(), Map.of(), new DrawEngine.Params(3, 200_000, 20_000, 200_000, null),
					listener(found));
			assertEquals(3, summary.found(), definition);
			for (DrawEngine.Candidate candidate : found) {
				assertTrue(checkPasses(definition, candidate.leagues().getFirst()), "generated draw violates " + definition);
			}
			// desactivada: deja de llegar al motor y vuelve al activarla
			rules.update(saved.id(), disabledCopy(entry.getValue(), false, saved.rowVersion()));
			assertFalse(checks("PQA", S1).contains(definition), "disabled assignment must not reach the engine: " + definition);
			assertEquals("DISABLED", rules.effective("PQA", S1).rules().stream().filter(r -> saved.id().equals(r.assignmentId()))
					.findFirst().orElseThrow().status());
			rules.delete(saved.id());
		}
	}

	private static RuleScopeDtos.AssignmentRequest disabledCopy(RuleScopeDtos.AssignmentRequest r, boolean enabled, long rowVersion) {
		return new RuleScopeDtos.AssignmentRequest(r.scopeType(), r.competitionId(), r.season(), r.ruleType(), r.mandatory(),
				r.allowException(), r.priority(), enabled, r.exceptionOf(), r.a(), r.b(), r.team(), r.teams(), r.rounds(), r.minRounds(),
				r.intParam(), r.from(), r.to(), r.date(), r.note(), rowVersion);
	}

	@Test
	void federationDefaultAppliesToItsFederationAcrossSeasonsButNotToOtherFederations() {
		rules.create("DEMO", matchNotInRound("FEDERATION_DEFAULT", null, null, true));
		String definition = "MatchNotInRoundCheck,XQ1,XQ2,1,6";
		assertTrue(checks("PQA", S1).contains(definition));
		assertTrue(checks("PQA", S2).contains(definition), "federation default is not tied to a season");
		assertFalse(checks("PQC", S1).contains(definition), "FRMF competition shares the teams but not the federation");
		assertTrue(checks("PQB", S1).isEmpty(), "teams of the rule are not in PQB");
		rules.create("FRMF", matchNotInRound("FEDERATION_DEFAULT", null, null, true));
		assertTrue(checks("PQC", S1).contains(definition));
	}

	@Test
	void competitionSeasonAndCoordinationGroupScopesAreIsolated() {
		rules.create("DEMO", request("COORDINATION_GROUP", "PQA", S1, "MatchNotInRound", false, true, null, "YQ1", "YQ2", null, null, "2", null, null));
		assertTrue(checks("PQB", S1).contains("MatchNotInRoundCheck,YQ1,YQ2,2"), "group of the parent reaches its dependent");
		assertTrue(checks("PQA", S1).isEmpty(), "teams not in the parent are filtered");
		assertTrue(checks("PQC", S1).isEmpty());
		rules.create("DEMO", request("COMPETITION_SEASON", "PQB", S1, "MatchNotInRound", false, true, null, "YQ3", "YQ4", null, null, "3", null, null));
		assertTrue(checks("PQB", S1).contains("MatchNotInRoundCheck,YQ3,YQ4,3"));
		assertFalse(checks("PQA", S1).contains("MatchNotInRoundCheck,YQ3,YQ4,3"));
		assertThrows(IllegalArgumentException.class, () -> rules.create("DEMO", request("COMPETITION_SEASON", "PQC", S1, "MatchNotInRound",
				false, true, null, "XQ1", "XQ2", null, null, "1", null, null)), "competition of another federation");
		assertThrows(SetupExceptions.NotFound.class, () -> rules.create("DEMO", matchNotInRound("COMPETITION_SEASON", "PQA", "2099-00", true)));
	}

	@Test
	void duplicatesLoadedDirectlyInTheDatabaseAreAppliedOnce() {
		rules.create("DEMO", matchNotInRound("FEDERATION_DEFAULT", null, null, true));
		rules.create("DEMO", matchNotInRound("COMPETITION_SEASON", "PQA", S1, true));
		var twin = rules.create("DEMO", matchNotInRound("COMPETITION_SEASON", "PQA", S1, true));
		ruleRepo.insert(new RuleScopeDtos.Assignment(null, "COMPETITION_SEASON", "DEMO", "PQA", S1, "MatchNotInRound", null, false, true, 0,
				true, null, "XQ1", "XQ2", null, null, "1,6", null, null, null, null, null, null, 0, null, null));
		var effective = rules.effective("PQA", S1).rules().stream().filter(r -> "MatchNotInRound".equals(r.ruleType())).toList();
		assertEquals(4, effective.size());
		assertEquals(1, effective.stream().filter(RuleScopeDtos.EffectiveRule::applied).count());
		assertEquals(3, effective.stream().filter(r -> "DUPLICATE".equals(r.status())).count());
		assertEquals(1, checks("PQA", S1).stream().filter("MatchNotInRoundCheck,XQ1,XQ2,1,6"::equals).count());
		assertTrue(twin.id() != null);
	}

	@Test
	void exceptionsRemoveOnlyTheirSeasonAndMandatoryRulesCannotBeExcepted() {
		var base = rules.create("DEMO", matchNotInRound("FEDERATION_DEFAULT", null, null, true));
		rules.create("DEMO", request("COMPETITION_SEASON", "PQA", S1, null, false, true, base.id(), null, null, null, null, null, null, null));
		String definition = "MatchNotInRoundCheck,XQ1,XQ2,1,6";
		assertFalse(checks("PQA", S1).contains(definition), "excepted in its season");
		assertTrue(checks("PQA", S2).contains(definition), "exception does not leak to another season");
		var mandatory = rules.create("DEMO", request("FEDERATION_DEFAULT", null, null, "DerbyTeams", true, true, null, "XQ3", "XQ4", null, null, null, null, null));
		assertThrows(SetupExceptions.Conflict.class, () -> rules.create("DEMO",
				request("COMPETITION_SEASON", "PQA", S1, null, false, true, mandatory.id(), null, null, null, null, null, null, null)));
		assertTrue(checks("PQA", S1).contains("DerbyTeamsCheck,XQ3,XQ4"));
		assertTrue(mandatory.mandatory());
	}

	@Test
	void disabledCatalogTypeStopsReachingTheEngineAndCannotBeAssigned() {
		rules.create("DEMO", matchNotInRound("COMPETITION_SEASON", "PQA", S1, true));
		assertEquals(1, checks("PQA", S1).size());
		catalog.updateRuleTypeStatus("DEMO", "MatchNotInRound", false);
		assertTrue(checks("PQA", S1).isEmpty());
		assertEquals("DISABLED_TYPE", rules.effective("PQA", S1).rules().getFirst().status());
		assertThrows(IllegalArgumentException.class, () -> rules.create("DEMO", matchNotInRound("COMPETITION_SEASON", "PQA", S1, true)));
	}

	@Test
	void unsupportedAndMalformedRulesAreRejectedExplicitly() {
		for (String type : List.of("HalfMatchesAtHome", "LEGACY_CHECK", "NoSuchRule")) {
			assertThrows(IllegalArgumentException.class, () -> rules.create("FRMF", request("COMPETITION_SEASON", "PQC", S1, type, false, true,
					null, "XQ1", "XQ2", null, null, null, null, null)), type);
		}
		var self = rules.create("DEMO", request("COMPETITION_SEASON", "PQA", S1, "MatchNotInRound",
				false, true, null, "XQ1", "XQ1", null, null, "1", null, null));
		var selfPair = draws.validate("PQA", S1, false);
		assertTrue(selfPair.stream().anyMatch(i -> "error".equals(i.level())), "same team twice must fail validation: " + selfPair);
		rules.delete(self.id());
		assertThrows(IllegalArgumentException.class, () -> rules.create("DEMO", request("COMPETITION_SEASON", "PQA", S1, "MatchNotInRound",
				false, true, null, "XQ1", "XQ2", null, null, "11", null, null)), "round beyond 2*(n-1)");
		assertThrows(IllegalArgumentException.class, () -> rules.create("DEMO", request("COMPETITION_SEASON", "PQA", S1, "MatchNotInRound",
				false, true, null, "XQ1", "NOPE", null, null, "1", null, null)), "unknown team");
		assertThrows(IllegalArgumentException.class, () -> rules.create("DEMO", request("COMPETITION_SEASON", "PQA", S1, "VenueUnavailable",
				false, true, null, null, null, "XQ1", null, null, null, null)), "P2 rule without dates");
		assertThrows(IllegalArgumentException.class, () -> rules.create("DEMO", request("MATCH", "PQA", S1, "NotConsecutiveFarAway", false, true,
				null, "XQ1", "XQ2", null, "XQ1,XQ2", null, null, null)), "type not allowed at MATCH scope");

		jdbc.sql("""
				INSERT INTO constraint_rule (competition_season_id, list_order, rule_type, definition)
				SELECT cs.id, 99, 'LEGACY_CHECK', 'HalfMatchesAtHomeCheck' FROM competition_season cs
				 WHERE cs.competition_id = 'PQA' AND cs.season_id = :s""").param("s", S1).update();
		var issues = draws.validate("PQA", S1, false);
		assertTrue(issues.stream().anyMatch(i -> "error".equals(i.level()) && i.msg().contains("unsupported draw rule")), issues.toString());
		DrawBatchService.Input input = draws.input("PQA", S1, false);
		assertThrows(IllegalArgumentException.class, () -> DrawEngine.run(input.main(), List.of(), Map.of(),
				new DrawEngine.Params(1, 1000, 1000, 1000, null), listener(new ArrayList<>())));
	}

	@Test
	void contradictoryScopedRulesFailValidationInsteadOfBeingDropped() {
		rules.create("DEMO", request("COMPETITION_SEASON", "PQA", S1, "ForcePositions", false, true, null, null, null, "XQ1", null, null, null, 2));
		rules.create("DEMO", request("COMPETITION_SEASON", "PQA", S1, "ForcePositions", false, true, null, null, null, "XQ2", null, null, null, 2));
		assertThrows(IllegalArgumentException.class, () -> rules.create("DEMO",
				request("FEDERATION_DEFAULT", null, null, "ForcePositions", false, true, null, null, null, "XQ3", null, null, null, 2)));
		var issues = draws.validate("PQA", S1, false);
		assertTrue(issues.stream().anyMatch(i -> i.msg().contains("both forced to position 2")), issues.toString());
	}

	private static DrawEngine.Listener listener(List<DrawEngine.Candidate> found) {
		return new DrawEngine.Listener() {
			public void candidate(DrawEngine.Candidate candidate, int tries) {
				found.add(candidate);
			}

			public void info(String message) {
			}

			public boolean cancelled() {
				return false;
			}
		};
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
}
