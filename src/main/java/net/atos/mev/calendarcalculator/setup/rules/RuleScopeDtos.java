package net.atos.mev.calendarcalculator.setup.rules;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import net.atos.mev.calendarcalculator.setup.SetupDtos;

/** Restricciones asignadas por ámbito: federación, grupo de coordinación (D1+dependientes), liga-temporada o partido. */
public final class RuleScopeDtos {

	private RuleScopeDtos() {
	}

	public static final String FEDERATION_DEFAULT = "FEDERATION_DEFAULT";
	public static final String COORDINATION_GROUP = "COORDINATION_GROUP";
	public static final String COMPETITION_SEASON = "COMPETITION_SEASON";
	public static final String MATCH = "MATCH";
	/** Del más general al más concreto. */
	public static final List<String> SCOPES = List.of(FEDERATION_DEFAULT, COORDINATION_GROUP, COMPETITION_SEASON, MATCH);

	/** Origen de las reglas configuradas directamente en la pantalla de la liga (tabla constraint_rule / pares). */
	public static final String LEAGUE_SETUP = "LEAGUE_SETUP";

	public static int rank(String scope) {
		return LEAGUE_SETUP.equals(scope) ? SCOPES.indexOf(COMPETITION_SEASON) : SCOPES.indexOf(scope);
	}

	public record Assignment(String id, String scopeType, String federationCode, String competitionId, String season,
			String ruleType, String stage, boolean mandatory, boolean allowException, int priority, boolean enabled,
			String exceptionOf, String a, String b, String team, String teams, String rounds, Integer minRounds,
			Integer intParam, LocalDate from, LocalDate to, LocalDate date, String note, long rowVersion, Instant createdAt,
			Instant updatedAt) {

		public boolean isException() {
			return exceptionOf != null && !exceptionOf.isBlank();
		}
	}

	public record AssignmentRequest(String scopeType, String competitionId, String season, String ruleType, Boolean mandatory,
			Boolean allowException, Integer priority, Boolean enabled, String exceptionOf, String a, String b, String team,
			String teams, String rounds, Integer minRounds, Integer intParam, LocalDate from, LocalDate to, LocalDate date,
			String note, Long rowVersion) {
	}

	/**
	 * Regla tal como la ve el motor para una liga-temporada.
	 * status: APPLIED, EXCEPTED, NOT_APPLICABLE, DUPLICATE, DISABLED, DISABLED_TYPE.
	 */
	public record EffectiveRule(String origin, String assignmentId, String originCompetitionId, String ruleType, String stage,
			boolean mandatory, boolean allowException, int priority, String status, String reason, SetupDtos.Rule rule) {

		public boolean applied() {
			return "APPLIED".equals(status);
		}
	}

	public record EffectiveRules(String federationCode, String competitionId, String season, List<EffectiveRule> rules) {
	}
}
