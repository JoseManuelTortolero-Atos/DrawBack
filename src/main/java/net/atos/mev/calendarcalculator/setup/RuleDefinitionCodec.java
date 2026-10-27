package net.atos.mev.calendarcalculator.setup;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class RuleDefinitionCodec {

	private RuleDefinitionCodec() {
	}

	static SetupDtos.Rule hydrate(Long id, String storedType, String a, String b, String team, String teams, String rounds,
			Integer minRounds, Integer intParam, LocalDate from, LocalDate to, LocalDate date, String definition, String note) {
		if (definition == null || definition.isBlank()) {
			return new SetupDtos.Rule(id, storedType, a, b, team, teams, rounds, minRounds, intParam, from, to, date, definition, note);
		}
		String[] parts = definition.split(",");
		if (parts.length == 0) {
			return new SetupDtos.Rule(id, storedType, a, b, team, teams, rounds, minRounds, intParam, from, to, date, definition, note);
		}
		return switch (parts[0]) {
		case "DerbyTeamsCheck" -> new SetupDtos.Rule(id, "DerbyTeams", value(parts, 1, a), value(parts, 2, b), team, teams, rounds,
				minRounds, intParam, from, to, date, definition, note);
		case "MatchNotInRoundCheck" -> new SetupDtos.Rule(id, "MatchNotInRound", value(parts, 1, a), value(parts, 2, b), team, teams,
				join(parts, 3, rounds), minRounds, intParam, from, to, date, definition, note);
		case "SeparateMatchesCheck" -> new SetupDtos.Rule(id, "SeparateMatches", a, b, team, join(parts, 2, teams), rounds,
				intAt(parts, 1, minRounds), intParam, from, to, date, definition, note);
		case "LimitHomeTogetherCheck" -> new SetupDtos.Rule(id, "LimitHomeTogether", value(parts, 1, a), value(parts, 2, b), team, teams,
				rounds, minRounds, intAt(parts, 3, intParam), from, to, date, definition, note);
		case "PlayTogetherTeamsCheck" -> new SetupDtos.Rule(id, "PlayTogether", value(parts, 1, a), value(parts, 2, b), team, teams, rounds,
				minRounds, intParam, from, to, date, definition, note);
		case "NotConsecutiveFarAwayCheck" -> new SetupDtos.Rule(id, "NotConsecutiveFarAway", a, b, team, join(parts, 1, teams), rounds,
				minRounds, intParam, from, to, date, definition, note);
		case "LimitAgainstTeamsInRoundsCheck" -> hydrateLimitAgainst(id, "LimitAgainstTeamsInRounds", parts, from, to, date, definition, note);
		case "LimitAgainstTeamsInRoundsHomeCheck" -> hydrateLimitAgainst(id, "LimitAgainstTeamsInRoundsHome", parts, from, to, date, definition, note);
		case "ForcePositionsCheck" -> new SetupDtos.Rule(id, "ForcePositions", a, b, value(parts, 1, team), teams, rounds, minRounds,
				intAt(parts, 2, intParam), from, to, date, definition, note);
		case "HalfMatchesAtHomeCheck" -> new SetupDtos.Rule(id, "HalfMatchesAtHome", a, b, team, teams, rounds, minRounds, intParam,
				from, to, date, definition, note);
		default -> new SetupDtos.Rule(id, storedType, a, b, team, teams, rounds, minRounds, intParam, from, to, date, definition, note);
		};
	}

	private static SetupDtos.Rule hydrateLimitAgainst(Long id, String type, String[] parts, LocalDate from, LocalDate to, LocalDate date,
			String definition, String note) {
		Integer limit = intAt(parts, 1, null);
		Integer amount = intAt(parts, 2, 0);
		List<String> teamList = new ArrayList<>();
		for (int index = 0; index < amount && 3 + index < parts.length; index++) {
			teamList.add(parts[3 + index]);
		}
		int roundsStart = 3 + amount;
		return new SetupDtos.Rule(id, type, null, null, null, String.join(",", teamList), join(parts, roundsStart, null), null, limit,
				from, to, date, definition, note);
	}

	public static String toDefinition(SetupDtos.Rule rule) {
		return switch (rule.type()) {
		case "DerbyTeams" -> joinRequired("DerbyTeamsCheck", rule.a(), rule.b());
		case "MatchNotInRound" -> joinRequired("MatchNotInRoundCheck", rule.a(), rule.b(), splitCsv(rule.rounds()));
		case "SeparateMatches" -> joinRequired("SeparateMatchesCheck", requiredInt(rule.minRounds()), splitCsv(rule.teams()));
		case "LimitHomeTogether" -> joinRequired("LimitHomeTogetherCheck", rule.a(), rule.b(), requiredInt(rule.intParam()));
		case "PlayTogether" -> joinRequired("PlayTogetherTeamsCheck", rule.a(), rule.b());
		case "NotConsecutiveFarAway" -> joinRequired("NotConsecutiveFarAwayCheck", (Object) splitCsv(rule.teams()));
		case "LimitAgainstTeamsInRounds" -> joinRequired("LimitAgainstTeamsInRoundsCheck", requiredInt(rule.intParam()),
				String.valueOf(splitCsv(rule.teams()).length), splitCsv(rule.teams()), splitCsv(rule.rounds()));
		case "LimitAgainstTeamsInRoundsHome" -> joinRequired("LimitAgainstTeamsInRoundsHomeCheck", requiredInt(rule.intParam()),
				String.valueOf(splitCsv(rule.teams()).length), splitCsv(rule.teams()), splitCsv(rule.rounds()));
		case "ForcePositions" -> joinRequired("ForcePositionsCheck", rule.team(), requiredInt(rule.intParam()));
		default -> rule.definition();
		};
	}

	private static String[] splitCsv(String value) {
		if (value == null || value.isBlank()) {
			return new String[0];
		}
		return Arrays.stream(value.split(",")).map(String::trim).filter(part -> !part.isEmpty()).toArray(String[]::new);
	}

	private static String requiredInt(Integer value) {
		if (value == null) {
			throw new IllegalArgumentException("Missing integer parameter for rule definition");
		}
		return String.valueOf(value);
	}

	private static String joinRequired(String prefix, Object... parts) {
		List<String> out = new ArrayList<>();
		out.add(prefix);
		for (Object part : parts) {
			if (part instanceof String[] array) {
				out.addAll(List.of(array));
			} else if (part != null) {
				String value = String.valueOf(part).trim();
				if (!value.isEmpty()) {
					out.add(value);
				}
			}
		}
		return String.join(",", out);
	}

	private static String value(String[] parts, int index, String fallback) {
		return index < parts.length ? parts[index] : fallback;
	}

	private static Integer intAt(String[] parts, int index, Integer fallback) {
		return index < parts.length && !parts[index].isBlank() ? Integer.valueOf(parts[index]) : fallback;
	}

	private static String join(String[] parts, int start, String fallback) {
		if (start >= parts.length) {
			return fallback;
		}
		return String.join(",", Arrays.copyOfRange(parts, start, parts.length));
	}
}