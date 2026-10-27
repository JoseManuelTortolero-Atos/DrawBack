package net.atos.mev.calendarcalculator.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RuleDefinitionCodecTest {

	@Test
	void buildsCanonicalDefinitionsForTheNineEditableP1Types() {
		assertEquals("DerbyTeamsCheck,WAC,RCA", RuleDefinitionCodec.toDefinition(new SetupDtos.Rule(null, "DerbyTeams", "WAC", "RCA", null,
				null, null, null, null, null, null, null, null, null)));
		assertEquals("MatchNotInRoundCheck,WAC,RCA,1,10,15", RuleDefinitionCodec.toDefinition(new SetupDtos.Rule(null, "MatchNotInRound", "WAC",
				"RCA", null, null, "1,10,15", null, null, null, null, null, null, null)));
		assertEquals("SeparateMatchesCheck,2,RCA,WAC,RSB", RuleDefinitionCodec.toDefinition(new SetupDtos.Rule(null, "SeparateMatches", null,
				null, null, "RCA,WAC,RSB", null, 2, null, null, null, null, null, null)));
		assertEquals("LimitHomeTogetherCheck,FUS,FAR,3", RuleDefinitionCodec.toDefinition(new SetupDtos.Rule(null, "LimitHomeTogether", "FUS",
				"FAR", null, null, null, null, 3, null, null, null, null, null)));
		assertEquals("PlayTogetherTeamsCheck,RSB,MCO", RuleDefinitionCodec.toDefinition(new SetupDtos.Rule(null, "PlayTogether", "RSB", "MCO",
				null, null, null, null, null, null, null, null, null, null)));
		assertEquals("NotConsecutiveFarAwayCheck,RSB,MAS,HUSA,OCS", RuleDefinitionCodec.toDefinition(new SetupDtos.Rule(null,
				"NotConsecutiveFarAway", null, null, null, "RSB,MAS,HUSA,OCS", null, null, null, null, null, null, null, null)));
		assertEquals("LimitAgainstTeamsInRoundsCheck,1,3,RCA,WAC,RSB,3,4,8", RuleDefinitionCodec.toDefinition(new SetupDtos.Rule(null,
				"LimitAgainstTeamsInRounds", null, null, null, "RCA,WAC,RSB", "3,4,8", null, 1, null, null, null, null, null)));
		assertEquals("LimitAgainstTeamsInRoundsHomeCheck,2,2,FAR,UTS,5,6", RuleDefinitionCodec.toDefinition(new SetupDtos.Rule(null,
				"LimitAgainstTeamsInRoundsHome", null, null, null, "FAR,UTS", "5,6", null, 2, null, null, null, null, null)));
		assertEquals("ForcePositionsCheck,WAC,4", RuleDefinitionCodec.toDefinition(new SetupDtos.Rule(null, "ForcePositions", null, null, "WAC",
				null, null, null, 4, null, null, null, null, null)));
	}

	@Test
	void hydratesLegacyDefinitionsIntoStructuredRules() {
		SetupDtos.Rule limit = RuleDefinitionCodec.hydrate(1L, "LEGACY_CHECK", null, null, null, null, null, null, null, null, null, null,
				"LimitAgainstTeamsInRoundsHomeCheck,2,2,FAR,UTS,5,6", null);
		assertEquals("LimitAgainstTeamsInRoundsHome", limit.type());
		assertEquals("FAR,UTS", limit.teams());
		assertEquals("5,6", limit.rounds());
		assertEquals(2, limit.intParam());

		SetupDtos.Rule force = RuleDefinitionCodec.hydrate(2L, "LEGACY_CHECK", null, null, null, null, null, null, null, null, null, null,
				"ForcePositionsCheck,WAC,4", null);
		assertEquals("ForcePositions", force.type());
		assertEquals("WAC", force.team());
		assertEquals(4, force.intParam());
	}
}