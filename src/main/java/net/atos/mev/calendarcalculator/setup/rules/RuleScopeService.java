package net.atos.mev.calendarcalculator.setup.rules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import net.atos.mev.calendarcalculator.setup.RuleDefinitionCodec;
import net.atos.mev.calendarcalculator.setup.SeasonSetupRepository;
import net.atos.mev.calendarcalculator.setup.SetupCatalogRepository;
import net.atos.mev.calendarcalculator.setup.SetupDtos;
import net.atos.mev.calendarcalculator.setup.SetupExceptions;

/**
 * Gestión de restricciones por ámbito y resolución de las reglas efectivas de una liga-temporada.
 * Herencia: federación → grupo de coordinación (principal + dependientes) → liga-temporada → partido.
 * Una liga puede excluir una regla heredada (excepción) salvo que sea obligatoria o no admita excepciones.
 */
@Service
public class RuleScopeService {

	private static final Set<String> P2_TYPES = Set.of("VenueUnavailable", "FixedHomeDate");

	private final RuleScopeRepository repo;
	private final SetupCatalogRepository catalog;
	private final SeasonSetupRepository setups;

	public RuleScopeService(RuleScopeRepository repo, SetupCatalogRepository catalog, SeasonSetupRepository setups) {
		this.repo = repo;
		this.catalog = catalog;
		this.setups = setups;
	}

	public List<RuleScopeDtos.Assignment> list(String federationCode, String competitionId, String season, String scopeType) {
		return repo.list(federationCode, competitionId, season, scopeType);
	}

	@Transactional(isolation = Isolation.SERIALIZABLE)
	public RuleScopeDtos.Assignment create(String federationCode, RuleScopeDtos.AssignmentRequest req) {
		RuleScopeDtos.Assignment a = normalize(federationCode, req, null);
		return repo.require(repo.insert(a));
	}

	@Transactional(isolation = Isolation.SERIALIZABLE)
	public RuleScopeDtos.Assignment update(String id, RuleScopeDtos.AssignmentRequest req) {
		RuleScopeDtos.Assignment current = repo.require(id);
		if (req.rowVersion() == null) {
			throw new IllegalArgumentException("rowVersion is required");
		}
		RuleScopeDtos.Assignment a = normalize(current.federationCode(), req, id);
		repo.update(id, req.rowVersion(), a);
		return repo.require(id);
	}

	public int delete(String id) {
		return repo.delete(id);
	}

	// ---------------------------------------------------------------- validación

	private RuleScopeDtos.Assignment normalize(String federationCode, RuleScopeDtos.AssignmentRequest r, String selfId) {
		if (r == null) {
			throw new IllegalArgumentException("Body is required");
		}
		String scope = upper(r.scopeType());
		if (!RuleScopeDtos.SCOPES.contains(scope)) {
			throw new IllegalArgumentException("scopeType must be one of " + RuleScopeDtos.SCOPES);
		}
		String competitionId = blankToNull(r.competitionId());
		String season = blankToNull(r.season());
		if (RuleScopeDtos.FEDERATION_DEFAULT.equals(scope)) {
			competitionId = null;
			season = null;
		} else {
			if (competitionId == null || season == null) {
				throw new IllegalArgumentException(scope + " requires competitionId and season");
			}
			if (!repo.competitionSeasonExists(competitionId, season)) {
				throw new SetupExceptions.NotFound("Unknown competition/season " + competitionId + " " + season);
			}
			String compFed = catalog.federationCodeForCompetition(competitionId);
			if (!federationCode.equals(compFed)) {
				throw new IllegalArgumentException("Competition " + competitionId + " belongs to federation " + compFed);
			}
		}
		boolean mandatory = Boolean.TRUE.equals(r.mandatory());
		boolean allowException = !mandatory && (r.allowException() == null || r.allowException());
		int priority = r.priority() == null ? 0 : r.priority();
		boolean enabled = r.enabled() == null || r.enabled();
		String exceptionOf = blankToNull(r.exceptionOf());

		if (exceptionOf != null) {
			RuleScopeDtos.Assignment parent = repo.require(exceptionOf);
			if (exceptionOf.equals(selfId)) {
				throw new IllegalArgumentException("An assignment cannot be an exception of itself");
			}
			if (!federationCode.equals(parent.federationCode())) {
				throw new IllegalArgumentException("The excluded rule belongs to federation " + parent.federationCode());
			}
			if (parent.isException()) {
				throw new IllegalArgumentException("Cannot create an exception of another exception");
			}
			if (parent.mandatory() || !parent.allowException()) {
				throw new SetupExceptions.Conflict("Rule " + parent.ruleType() + " (" + parent.scopeType()
						+ ") is mandatory or does not allow exceptions");
			}
			if (!RuleScopeDtos.COMPETITION_SEASON.equals(scope) && !RuleScopeDtos.COORDINATION_GROUP.equals(scope)) {
				throw new IllegalArgumentException("Exceptions are declared at COMPETITION_SEASON or COORDINATION_GROUP scope");
			}
			if (RuleScopeDtos.rank(scope) <= RuleScopeDtos.rank(parent.scopeType())) {
				throw new IllegalArgumentException("An exception must be more specific than the rule it excludes ("
						+ parent.scopeType() + ")");
			}
			return new RuleScopeDtos.Assignment(null, scope, federationCode, competitionId, season, parent.ruleType(), null,
					false, false, priority, enabled, exceptionOf, null, null, null, null, null, null, null, null, null, null,
					trim(r.note()), 0, null, null);
		}

		String ruleType = blankToNull(r.ruleType());
		SetupDtos.RuleTypeDef def = ruleType == null ? null : catalogMap(federationCode).get(ruleType);
		if (def == null) {
			throw new IllegalArgumentException("Rule type " + ruleType + " does not exist in the catalog of " + federationCode);
		}
		if (!def.enabled()) {
			throw new IllegalArgumentException("Rule type " + ruleType + " is disabled for " + federationCode);
		}
		if (!def.allowedScopes().contains(scope)) {
			throw new IllegalArgumentException("Rule type " + ruleType + " cannot be assigned at " + scope + " scope (allowed: "
					+ def.allowedScopes() + ")");
		}
		RuleScopeDtos.Assignment a = new RuleScopeDtos.Assignment(null, scope, federationCode, competitionId, season, ruleType,
				null, mandatory, allowException, priority, enabled, null, code(r.a()), code(r.b()), code(r.team()),
				csv(r.teams(), true), csv(r.rounds(), false), r.minRounds(), r.intParam(), r.from(), r.to(), r.date(),
				trim(r.note()), 0, null, null);
		SetupDtos.Rule rule = toRule(a);
		if ("DerbyTeams".equals(ruleType) && a.a() != null && a.a().equals(a.b())) {
			throw new IllegalArgumentException("Home/away pairs require two different teams");
		}
		for (SetupDtos.RuleParam p : def.params()) {
			if (!hasValue(rule, p.key())) {
				throw new IllegalArgumentException(ruleType + ": missing value for " + p.label());
			}
		}
		if (RuleScopeDtos.MATCH.equals(scope)) {
			if (a.a() == null || a.b() == null || a.a().equals(a.b())) {
				throw new IllegalArgumentException("MATCH scope requires two different teams (Team A and Team B)");
			}
		}
		if (a.from() != null && a.to() != null && a.to().isBefore(a.from())) {
			throw new IllegalArgumentException("'to' date is before 'from' date");
		}
		if (a.rounds() != null) {
			int maxRound = Integer.MAX_VALUE;
			if (RuleScopeDtos.COMPETITION_SEASON.equals(scope) || RuleScopeDtos.MATCH.equals(scope)) {
				int n = repo.teamsOf(competitionId, season).size();
				maxRound = n > 1 ? 2 * (n - 1) : Integer.MAX_VALUE;
			}
			for (String round : a.rounds().split(",")) {
				int value;
				try {
					value = Integer.parseInt(round);
				} catch (NumberFormatException e) {
					throw new IllegalArgumentException("Invalid round number '" + round + "'");
				}
				if (value < 1 || value > maxRound) {
					throw new IllegalArgumentException("Round " + round + " out of range 1.." + maxRound);
				}
			}
		}
		if (!P2_TYPES.contains(ruleType)) {
			try {
				RuleDefinitionCodec.toDefinition(rule);
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException(ruleType + ": " + e.getMessage());
			}
		}
		Set<String> known = knownTeams(scope, competitionId, season);
		for (String t : teamsOf(rule)) {
			if (known == null ? !repo.teamExists(t) : !known.contains(t)) {
				throw new IllegalArgumentException("Team " + t + " is not part of " + (known == null ? "the team catalog"
						: competitionId + " " + season + (RuleScopeDtos.COORDINATION_GROUP.equals(scope) ? " (coordination group)" : "")));
			}
			if ("DerbyTeams".equals(ruleType) && RuleScopeDtos.FEDERATION_DEFAULT.equals(scope)
					&& !repo.teamAvailableToFederation(t, federationCode)) {
				throw new IllegalArgumentException("Team " + t + " is registered only in another federation");
			}
		}
		return a;
	}

	/** null = cualquier equipo del catálogo (ámbito federación). */
	private Set<String> knownTeams(String scope, String competitionId, String season) {
		if (RuleScopeDtos.FEDERATION_DEFAULT.equals(scope)) {
			return null;
		}
		Set<String> out = new HashSet<>(repo.teamsOf(competitionId, season));
		if (RuleScopeDtos.COORDINATION_GROUP.equals(scope)) {
			repo.coordinationGroup(competitionId, season).forEach(c -> out.addAll(repo.teamsOf(c, season)));
		}
		return out;
	}

	// ---------------------------------------------------------------- resolución

	public RuleScopeDtos.EffectiveRules effective(String competitionId, String season) {
		SetupDtos.SeasonSetup setup = setups.load(competitionId, season);
		String fed = catalog.federationCodeForCompetition(competitionId);
		return effectiveForDraw(setup, new HashSet<>(repo.federationTeams(fed, season)));
	}

	public RuleScopeDtos.EffectiveRules effective(SetupDtos.SeasonSetup s) {
		return effective(s, s.teams().stream().map(SetupDtos.Team::code).collect(Collectors.toSet()));
	}

	public RuleScopeDtos.EffectiveRules effectiveForDraw(SetupDtos.SeasonSetup s, Set<String> coordinatedTeams) {
		return effective(s, coordinatedTeams);
	}

	private RuleScopeDtos.EffectiveRules effective(SetupDtos.SeasonSetup s, Set<String> coordinatedTeams) {
		String fed = catalog.federationCodeForCompetition(s.competitionId());
		Map<String, SetupDtos.RuleTypeDef> defs = catalogMap(fed);
		Set<String> teams = s.teams().stream().map(SetupDtos.Team::code).collect(Collectors.toSet());
		String parent = repo.parentCompetition(s.competitionId(), s.season()).orElse(null);
		List<RuleScopeDtos.Assignment> candidates = repo.candidatesFor(fed, s.competitionId(), parent, s.season());

		List<RuleScopeDtos.EffectiveRule> out = new ArrayList<>();
		for (SetupDtos.Rule rule : s.rules() == null ? List.<SetupDtos.Rule>of() : s.rules()) {
			SetupDtos.RuleTypeDef def = defs.get(rule.type());
			out.add(new RuleScopeDtos.EffectiveRule(RuleScopeDtos.LEAGUE_SETUP, null, s.competitionId(), rule.type(), stage(def, rule.type()),
					false, false, 0, def != null && !def.enabled() ? "DISABLED_TYPE" : "APPLIED",
					"Configured in the league screen", rule));
		}
		for (List<String> pair : s.derbies() == null ? List.<List<String>>of() : s.derbies()) {
			if (pair.size() == 2) {
				SetupDtos.Rule rule = new SetupDtos.Rule(null, "DerbyTeams", pair.get(0), pair.get(1), null, null, null, null, null, null,
						null, null, "DerbyTeamsCheck," + pair.get(0) + "," + pair.get(1), "Derby pair");
				out.add(new RuleScopeDtos.EffectiveRule(RuleScopeDtos.LEAGUE_SETUP, null, s.competitionId(), "DerbyTeams", "P1", false,
						false, 0, "APPLIED", "Derby pair of the league screen", rule));
			}
		}

		Set<String> excepted = new HashSet<>();
		Map<String, RuleScopeDtos.Assignment> exceptionBy = new HashMap<>();
		for (RuleScopeDtos.Assignment a : candidates) {
			if (a.isException() && a.enabled() && appliesTo(a, s.competitionId(), parent)) {
				excepted.add(a.exceptionOf());
				exceptionBy.put(a.exceptionOf(), a);
			}
		}
		for (RuleScopeDtos.Assignment a : candidates) {
			if (a.isException() || !appliesTo(a, s.competitionId(), parent)) {
				continue;
			}
			SetupDtos.RuleTypeDef def = defs.get(a.ruleType());
			String status = "APPLIED";
			String reason = describe(a);
			SetupDtos.Rule rule = toRule(a);
			if (!a.enabled()) {
				status = "DISABLED";
				reason = "Assignment disabled";
			} else if (def == null || !def.enabled()) {
				status = "DISABLED_TYPE";
				reason = "Rule type disabled in the federation catalog";
			} else if (excepted.contains(a.id())) {
				status = "EXCEPTED";
				RuleScopeDtos.Assignment ex = exceptionBy.get(a.id());
				reason = "Excluded by " + ex.scopeType() + " " + ex.competitionId() + (ex.note() == null ? "" : ": " + ex.note());
			} else {
				Set<String> applicableTeams = "DerbyTeams".equals(rule.type()) ? coordinatedTeams : teams;
				SetupDtos.Rule filtered = filterToLeague(rule, applicableTeams);
				if ("DerbyTeams".equals(rule.type()) && !teams.contains(rule.a()) && !teams.contains(rule.b())) {
					filtered = null;
				}
				if (filtered == null) {
					status = "NOT_APPLICABLE";
					reason = "Teams of the rule are not in " + s.code();
				} else {
					rule = filtered;
					if ("DerbyTeams".equals(rule.type()) && (!teams.contains(rule.a()) || !teams.contains(rule.b()))) {
						reason += "; coordinated P1 pair across federation competitions";
					}
				}
			}
			out.add(new RuleScopeDtos.EffectiveRule(a.scopeType(), a.id(), a.competitionId(), a.ruleType(), stage(def, a.ruleType()),
					a.mandatory(), a.allowException(), a.priority(), status, reason, rule));
		}
		markDuplicates(out);
		out.sort(Comparator.comparing((RuleScopeDtos.EffectiveRule e) -> !e.applied())
				.thenComparing(RuleScopeDtos.EffectiveRule::stage, Comparator.nullsLast(Comparator.naturalOrder()))
				.thenComparing(Comparator.comparingInt(RuleScopeDtos.EffectiveRule::priority).reversed())
				.thenComparing(Comparator.comparingInt((RuleScopeDtos.EffectiveRule e) -> RuleScopeDtos.rank(e.origin())).reversed()));
		return new RuleScopeDtos.EffectiveRules(fed, s.competitionId(), s.season(), out);
	}

	/**
	 * Configuración que reciben los motores (P1 sorteo y P2 fechas/horarios): reglas propias de la liga
	 * más las reglas heredadas o asignadas por ámbito que aplican.
	 */
	public SetupDtos.SeasonSetup withScopedRules(SetupDtos.SeasonSetup s) {
		List<SetupDtos.Rule> rules = new ArrayList<>(s.rules() == null ? List.of() : s.rules());
		for (RuleScopeDtos.EffectiveRule e : effective(s).rules()) {
			if (e.applied() && e.assignmentId() != null) {
				rules.add(e.rule());
			}
		}
		return new SetupDtos.SeasonSetup(s.competitionId(), s.code(), s.season(), s.general(), s.timeslots(), s.ramadan(),
				s.noLightSlot(), s.teams(), s.rounds(), s.fixtures(), s.external(), s.blackouts(), s.restDays(), s.notHomeSameDay(),
				s.derbies(), rules, s.dependsOn(), s.includeDependency(), s.engine(), s.rowVersion(), s.updatedAt());
	}

	private static boolean appliesTo(RuleScopeDtos.Assignment a, String competitionId, String parent) {
		return switch (a.scopeType()) {
		case RuleScopeDtos.FEDERATION_DEFAULT -> true;
		case RuleScopeDtos.COORDINATION_GROUP -> competitionId.equals(a.competitionId()) || (parent != null && parent.equals(a.competitionId()));
		case RuleScopeDtos.COMPETITION_SEASON, RuleScopeDtos.MATCH -> competitionId.equals(a.competitionId());
		default -> false;
		};
	}

	/** Mismo tipo y parámetros aplicados desde varios ámbitos: se queda el más concreto (o el de mayor prioridad). */
	private static void markDuplicates(List<RuleScopeDtos.EffectiveRule> rules) {
		Map<String, Integer> best = new HashMap<>();
		for (int i = 0; i < rules.size(); i++) {
			RuleScopeDtos.EffectiveRule e = rules.get(i);
			if (!e.applied()) {
				continue;
			}
			String key = signature(e.rule());
			Integer prev = best.get(key);
			if (prev == null) {
				best.put(key, i);
				continue;
			}
			RuleScopeDtos.EffectiveRule p = rules.get(prev);
			boolean newOwn = RuleScopeDtos.LEAGUE_SETUP.equals(e.origin());
			boolean prevOwn = RuleScopeDtos.LEAGUE_SETUP.equals(p.origin());
			if (newOwn && prevOwn) {
				continue;
			}
			// lo configurado en la pantalla de la liga ya va en la configuración del motor: nunca se descarta
			boolean keepNew = newOwn || (!prevOwn && (RuleScopeDtos.rank(e.origin()) > RuleScopeDtos.rank(p.origin())
					|| (RuleScopeDtos.rank(e.origin()) == RuleScopeDtos.rank(p.origin()) && e.priority() > p.priority())));
			int loser = keepNew ? prev : i;
			int winner = keepNew ? i : prev;
			RuleScopeDtos.EffectiveRule l = rules.get(loser);
			rules.set(loser, new RuleScopeDtos.EffectiveRule(l.origin(), l.assignmentId(), l.originCompetitionId(), l.ruleType(), l.stage(),
					l.mandatory(), l.allowException(), l.priority(), "DUPLICATE",
					"Same rule already applied from " + rules.get(winner).origin(), l.rule()));
			best.put(key, winner);
		}
	}

	private static String signature(SetupDtos.Rule r) {
		String def = r.definition();
		if ("DerbyTeams".equals(r.type()) || "PlayTogether".equals(r.type())) {
			List<String> pair = new ArrayList<>(Arrays.asList(String.valueOf(r.a()), String.valueOf(r.b())));
			pair.sort(null);
			return r.type() + ":" + pair;
		}
		if (def != null && !def.isBlank()) {
			return def;
		}
		return String.join("|", r.type(), String.valueOf(r.team()), String.valueOf(r.from()), String.valueOf(r.to()),
				String.valueOf(r.date()));
	}

	/** Deja solo los equipos de la liga en las listas; null si falta algún equipo obligatorio o la lista queda vacía. */
	private static SetupDtos.Rule filterToLeague(SetupDtos.Rule r, Set<String> teams) {
		if ((r.a() != null && !teams.contains(r.a())) || (r.b() != null && !teams.contains(r.b()))
				|| (r.team() != null && !teams.contains(r.team()))) {
			return null;
		}
		String list = r.teams();
		if (list != null && !list.isBlank()) {
			List<String> kept = Arrays.stream(list.split(",")).map(String::trim).filter(teams::contains).toList();
			int min = "SeparateMatches".equals(r.type()) ? 2 : 1;
			if (kept.size() < min) {
				return null;
			}
			list = String.join(",", kept);
		}
		SetupDtos.Rule out = new SetupDtos.Rule(null, r.type(), r.a(), r.b(), r.team(), list, r.rounds(), r.minRounds(), r.intParam(),
				r.from(), r.to(), r.date(), null, r.note());
		return withDefinition(out);
	}

	static SetupDtos.Rule toRule(RuleScopeDtos.Assignment a) {
		return withDefinition(new SetupDtos.Rule(null, a.ruleType(), a.a(), a.b(), a.team(), a.teams(), a.rounds(), a.minRounds(),
				a.intParam(), a.from(), a.to(), a.date(), null, a.note()));
	}

	private static SetupDtos.Rule withDefinition(SetupDtos.Rule r) {
		if (P2_TYPES.contains(r.type())) {
			return r;
		}
		String def;
		try {
			def = RuleDefinitionCodec.toDefinition(r);
		} catch (IllegalArgumentException e) {
			def = null;
		}
		return new SetupDtos.Rule(r.id(), r.type(), r.a(), r.b(), r.team(), r.teams(), r.rounds(), r.minRounds(), r.intParam(),
				r.from(), r.to(), r.date(), def, r.note());
	}

	private static List<String> teamsOf(SetupDtos.Rule r) {
		Set<String> out = new LinkedHashSet<>();
		if (r.a() != null) {
			out.add(r.a());
		}
		if (r.b() != null) {
			out.add(r.b());
		}
		if (r.team() != null) {
			out.add(r.team());
		}
		if (r.teams() != null && !r.teams().isBlank()) {
			Arrays.stream(r.teams().split(",")).map(String::trim).filter(t -> !t.isEmpty()).forEach(out::add);
		}
		return new ArrayList<>(out);
	}

	private static boolean hasValue(SetupDtos.Rule r, String key) {
		Object v = switch (key) {
		case "a" -> r.a();
		case "b" -> r.b();
		case "team" -> r.team();
		case "teams" -> r.teams();
		case "rounds" -> r.rounds();
		case "minRounds" -> r.minRounds();
		case "intParam" -> r.intParam();
		case "from" -> r.from();
		case "to" -> r.to();
		case "date" -> r.date();
		default -> "n/a";
		};
		return v != null && !String.valueOf(v).isBlank();
	}

	private static String describe(RuleScopeDtos.Assignment a) {
		return switch (a.scopeType()) {
		case RuleScopeDtos.FEDERATION_DEFAULT -> "Inherited from federation " + a.federationCode();
		case RuleScopeDtos.COORDINATION_GROUP -> "Coordination group of " + a.competitionId();
		case RuleScopeDtos.MATCH -> "Match " + a.a() + " - " + a.b();
		default -> "Assigned to " + a.competitionId() + " " + a.season();
		};
	}

	private static String stage(SetupDtos.RuleTypeDef def, String type) {
		if (def != null) {
			return "PLANNING".equals(def.scope()) ? "P2" : "P1";
		}
		return P2_TYPES.contains(type) ? "P2" : "P1";
	}

	private Map<String, SetupDtos.RuleTypeDef> catalogMap(String federationCode) {
		return catalog.ruleTypes(federationCode).stream()
				.collect(Collectors.toMap(SetupDtos.RuleTypeDef::type, Function.identity(), (x, y) -> x));
	}

	private static String upper(String v) {
		return v == null ? null : v.trim().toUpperCase();
	}

	private static String blankToNull(String v) {
		return v == null || v.isBlank() ? null : v.trim();
	}

	private static String trim(String v) {
		return blankToNull(v);
	}

	private static String code(String v) {
		String t = blankToNull(v);
		return t == null ? null : t.toUpperCase();
	}

	private static String csv(String v, boolean upper) {
		if (v == null || v.isBlank()) {
			return null;
		}
		String joined = Arrays.stream(v.split(",")).map(String::trim).filter(x -> !x.isEmpty())
				.map(x -> upper ? x.toUpperCase() : x).collect(Collectors.joining(","));
		return joined.isEmpty() ? null : joined;
	}
}
