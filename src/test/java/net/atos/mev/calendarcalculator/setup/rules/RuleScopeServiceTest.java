package net.atos.mev.calendarcalculator.setup.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.atos.mev.calendarcalculator.setup.SeasonSetupRepository;
import net.atos.mev.calendarcalculator.setup.SetupCatalogRepository;
import net.atos.mev.calendarcalculator.setup.SetupDtos;
import net.atos.mev.calendarcalculator.setup.SetupExceptions;

class RuleScopeServiceTest {

	private final RuleScopeRepository repo = mock(RuleScopeRepository.class);
	private final SetupCatalogRepository catalog = mock(SetupCatalogRepository.class);
	private final SeasonSetupRepository setups = mock(SeasonSetupRepository.class);
	private final RuleScopeService service = new RuleScopeService(repo, catalog, setups);

	private static SetupDtos.RuleTypeDef def(String type, String scope, List<String> scopes, SetupDtos.RuleParam... params) {
		return new SetupDtos.RuleTypeDef(type, type, "shield", scope, type + "Check", "", "", scopes, "SUPPORTED", null, null, true, true,
				List.of(params));
	}

	private static SetupDtos.RuleParam p(String key) {
		return new SetupDtos.RuleParam(key, key.equals("a") || key.equals("b") || key.equals("team") ? "team" : "text", key);
	}

	private static RuleScopeDtos.Assignment a(String id, String scope, String cid, String type, boolean mandatory, String exceptionOf,
			String ta, String tb, String teams, String rounds) {
		return new RuleScopeDtos.Assignment(id, scope, "FRMF", cid, cid == null ? null : "2026-27", type, "P1", mandatory, !mandatory, 0,
				true, exceptionOf, ta, tb, null, teams, rounds, null, null, null, null, null, null, 0, null, null);
	}

	private static SetupDtos.SeasonSetup setup(String cid, String code, String teams, List<List<String>> derbies) {
		List<SetupDtos.Team> t = List.of(teams.split(",")).stream().map(c -> new SetupDtos.Team(c, c, null, false, false)).toList();
		return new SetupDtos.SeasonSetup(cid, code, "2026-27", null, List.of(), null, null, t, List.of(), List.of(), List.of(), List.of(),
				Map.of(), List.of(), derbies, List.of(), null, false, null, 0, null);
	}

	@BeforeEach
	void catalog() {
		when(catalog.federationCodeForCompetition(anyString())).thenReturn("FRMF");
		List<String> all = List.of("FEDERATION_DEFAULT", "COMPETITION_SEASON", "COORDINATION_GROUP", "MATCH");
		when(catalog.ruleTypes("FRMF")).thenReturn(List.of(
				def("DerbyTeams", "DRAW", all, p("a"), p("b")),
				def("MatchNotInRound", "DRAW", all, p("a"), p("b"), p("rounds")),
				def("NotConsecutiveFarAway", "DRAW", List.of("FEDERATION_DEFAULT", "COMPETITION_SEASON"), p("teams")),
				def("VenueUnavailable", "PLANNING", List.of("COMPETITION_SEASON"), p("team"), p("from"), p("to"))));
	}

	@Test
	void resolvesInheritanceExceptionsFilteringAndDuplicates() {
		when(repo.parentCompetition("D2", "2026-27")).thenReturn(Optional.of("D1"));
		when(repo.candidatesFor(any(), any(), any(), any())).thenReturn(List.of(
				a("fed-derby", "FEDERATION_DEFAULT", null, "DerbyTeams", false, null, "RCA", "WAC", null, null),
				a("fed-derby2", "FEDERATION_DEFAULT", null, "DerbyTeams", false, null, "OCK", "USB", null, null),
				a("fed-far", "FEDERATION_DEFAULT", null, "NotConsecutiveFarAway", false, null, null, null, "HUSA,OCK,USB", null),
				a("grp-match", "COORDINATION_GROUP", "D1", "MatchNotInRound", false, null, "OCK", "USB", null, "1,16"),
				a("exc", "COMPETITION_SEASON", "D2", "NotConsecutiveFarAway", false, "fed-far", null, null, null, null)));
		SetupDtos.SeasonSetup d2 = setup("D2", "D2", "OCK,USB,MCO,IZK", List.of(List.of("USB", "OCK")));

		Map<String, String> status = service.effective(d2).rules().stream()
				.filter(r -> r.assignmentId() != null)
				.collect(Collectors.toMap(RuleScopeDtos.EffectiveRule::assignmentId, RuleScopeDtos.EffectiveRule::status));
		assertEquals("NOT_APPLICABLE", status.get("fed-derby"), "RCA/WAC do not play in D2");
		assertEquals("DUPLICATE", status.get("fed-derby2"), "same derby already configured in the league screen");
		assertEquals("EXCEPTED", status.get("fed-far"));
		assertEquals("APPLIED", status.get("grp-match"), "coordination group of D1 reaches its dependent D2");

		List<SetupDtos.Rule> engineRules = service.withScopedRules(d2).rules();
		assertEquals(1, engineRules.size());
		assertEquals("MatchNotInRoundCheck,OCK,USB,1,16", engineRules.get(0).definition());
	}

	@Test
	void filtersTeamListsToTheLeague() {
		when(repo.parentCompetition("D1", "2026-27")).thenReturn(Optional.empty());
		when(repo.candidatesFor(any(), any(), any(), any())).thenReturn(List.of(
				a("fed-far", "FEDERATION_DEFAULT", null, "NotConsecutiveFarAway", false, null, null, null, "HUSA,OCK,IRT", null)));
		List<SetupDtos.Rule> rules = service.withScopedRules(setup("D1", "D1", "HUSA,IRT,RCA,WAC", List.of())).rules();
		assertEquals("NotConsecutiveFarAwayCheck,HUSA,IRT", rules.get(0).definition());
	}

	@Test
	void resolvesFederationPairsAcrossTheDrawGroupWithoutSendingForeignTeamsToLocalChecks() {
		when(repo.parentCompetition("D1", "2026-27")).thenReturn(Optional.empty());
		when(repo.candidatesFor(any(), any(), any(), any())).thenReturn(List.of(
				a("cross", "FEDERATION_DEFAULT", null, "DerbyTeams", false, null, "RCA", "USB", null, null),
				a("unrelated", "FEDERATION_DEFAULT", null, "DerbyTeams", false, null, "WAC", "MCO", null, null)));
		SetupDtos.SeasonSetup league = setup("D1", "D1", "RCA,WAC,IRT,FUS", List.of());
		var group = service.effectiveForDraw(league, Set.of("RCA", "WAC", "IRT", "FUS", "USB"));
		assertTrue(group.rules().stream().anyMatch(r -> "cross".equals(r.assignmentId()) && r.applied()));
		assertTrue(group.rules().stream().anyMatch(r -> "unrelated".equals(r.assignmentId()) && "NOT_APPLICABLE".equals(r.status())));
		assertTrue(service.withScopedRules(league).rules().isEmpty());
		when(repo.candidatesFor(any(), any(), any(), any())).thenReturn(List.of(
				a("cross", "FEDERATION_DEFAULT", null, "DerbyTeams", false, null, "RCA", "USB", null, null),
				a("exception", "COMPETITION_SEASON", "D1", "DerbyTeams", false, "cross", null, null, null, null)));
		assertTrue(service.effectiveForDraw(league, Set.of("RCA", "USB")).rules().stream()
				.anyMatch(r -> "cross".equals(r.assignmentId()) && "EXCEPTED".equals(r.status())));
	}

	@Test
	void rejectsExceptionsOfMandatoryRulesAndScopesNotAllowed() {
		when(repo.require("mand")).thenReturn(a("mand", "FEDERATION_DEFAULT", null, "DerbyTeams", true, null, "RCA", "WAC", null, null));
		when(repo.competitionSeasonExists("D1", "2026-27")).thenReturn(true);
		assertThrows(SetupExceptions.Conflict.class, () -> service.create("FRMF", new RuleScopeDtos.AssignmentRequest("COMPETITION_SEASON",
				"D1", "2026-27", null, null, null, null, null, "mand", null, null, null, null, null, null, null, null, null, null, null, null)));
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.create("FRMF",
				new RuleScopeDtos.AssignmentRequest("MATCH", "D1", "2026-27", "NotConsecutiveFarAway", null, null, null, null, null, "RCA",
						"WAC", null, "RCA", null, null, null, null, null, null, null, null)));
		assertTrue(e.getMessage().contains("cannot be assigned at MATCH"));
	}
}
