package net.atos.mev.calendarcalculator.setup.draw;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.Team;
import net.atos.mev.calendarcalculator.Tournament;
import net.atos.mev.calendarcalculator.assignpositions.AssignPositions;
import net.atos.mev.calendarcalculator.checks.Check;
import net.atos.mev.calendarcalculator.checks.CheckProcessor;
import net.atos.mev.calendarcalculator.checks.DerbyTeamsCheck;
import net.atos.mev.calendarcalculator.checks.ForcePositionsCheck;
import net.atos.mev.calendarcalculator.generatepositions.TwoColumnsGenPosAlg;

/**
 * Sorteo (P1) del motor histórico sin ficheros: genera plantillas de posiciones con
 * {@link TwoColumnsGenPosAlg}, asigna equipos a posiciones respetando los checks con
 * {@link AssignPositions} y enlaza las ligas dependientes (equipos que comparten estadio:
 * el equipo de la liga dependiente ocupa la posición complementaria, como hacía
 * {@code AssignPositions.linkStadiumSharingTeams}).
 */
public final class DrawEngine {

	/** Prefijos de definición que son checks del sorteo (CheckProcessor.createCheck). */
	public static final Set<String> DRAW_CHECKS = Set.of("DerbyTeamsCheck", "LimitHomeTogetherCheck", "PlayTogetherTeamsCheck",
			"MatchNotInRoundCheck", "NotConsecutiveFarAwayCheck", "LimitAgainstTeamsInRoundsCheck", "LimitAgainstTeamsInRoundsHomeCheck",
			"SeparateMatchesCheck", "ForcePositionsCheck");

	public static boolean isCheckDefinition(String definition) {
		return definition != null && definition.split(",", 2)[0].trim().endsWith("Check");
	}

	public record League(long competitionSeasonId, String code, List<String[]> teams, List<String> checkDefinitions,
			List<List<String>> derbies) {
	}

	/** Equipo de la liga principal y equipo de la dependiente que no pueden jugar en casa a la vez. */
	public record Link(String parentTeam, String childTeam) {
	}

	public record RequiredPair(long firstLeague, String firstTeam, long secondLeague, String secondTeam) {
	}

	public record Params(int candidates, int maxTries, long timeoutMillis, long maxAssignNodes, int[] forceRepetitions) {
	}

	public record LeagueResult(long competitionSeasonId, String pattern, List<String> positionTeams) {
	}

	public record Candidate(int no, List<LeagueResult> leagues) {
	}

	public interface Listener {
		void candidate(Candidate candidate, int tries);

		void info(String message);

		boolean cancelled();
	}

	public record Summary(int found, int tries, boolean timedOut) {
	}

	private record Forced(Team team, ForcePositionsCheck check, Team parent, boolean complement) {
	}

	private DrawEngine() {
	}

	/** Comprueba la entrada y devuelve los problemas (vacía si se puede sortear). */
	public static List<String> validate(League main, List<League> dependents, Map<Long, List<Link>> links) {
		return validate(main, dependents, links, List.of());
	}

	public static List<String> validate(League main, List<League> dependents, Map<Long, List<Link>> links,
			List<RequiredPair> requiredPairs) {
		List<String> problems = new ArrayList<>();
		for (League league : all(main, dependents)) {
			int n = league.teams().size();
			if (n < 4 || n % 2 != 0 || n > 98) {
				problems.add(league.code() + ": the draw needs an even number of teams between 4 and 98, it has " + n);
				continue;
			}
			environment(league, problems);
			List<String[]> localEdges = derbyPairs(league).stream()
					.map(p -> new String[] {node(league, p.get(0)), node(league, p.get(1))}).toList();
			String conflict = parityConflict(localEdges);
			if (conflict != null) {
				problems.add(league.code() + ": incompatible home/away pairs: " + conflict
						+ ". Review the pairs; none will be ignored.");
			}
		}
		for (League child : dependents) {
			if (child.teams().size() != main.teams().size() && !links.getOrDefault(child.competitionSeasonId(), List.of()).isEmpty()) {
				problems.add(child.code() + ": stadium links with " + main.code() + " need the same number of teams ("
						+ child.teams().size() + " vs " + main.teams().size() + ")");
			}
		}
		Map<Long, League> byId = new HashMap<>();
		all(main, dependents).forEach(l -> byId.put(l.competitionSeasonId(), l));
		List<String[]> edges = new ArrayList<>();
		for (League league : all(main, dependents)) {
			for (List<String> p : derbyPairs(league)) {
				edges.add(new String[] {node(league, p.get(0)), node(league, p.get(1))});
			}
		}
		if (!requiredPairs.isEmpty()) {
			Map<Long, List<Link>> compatible = compatibleLinks(main, dependents, links, new ArrayList<>());
			for (League child : dependents) {
				for (Link link : compatible.getOrDefault(child.competitionSeasonId(), List.of())) {
					edges.add(new String[] {node(main, link.parentTeam()), node(child, link.childTeam())});
				}
			}
		}
		for (RequiredPair pair : requiredPairs) {
			League first = byId.get(pair.firstLeague());
			League second = byId.get(pair.secondLeague());
			if (first == null || second == null) {
				problems.add("Include the related competitions to enforce home/away pair "
						+ pair.firstTeam() + " / " + pair.secondTeam() + ". No rule was ignored.");
			} else if (first.teams().stream().noneMatch(t -> t[0].equals(pair.firstTeam()))
					|| second.teams().stream().noneMatch(t -> t[0].equals(pair.secondTeam()))) {
				problems.add("Unknown team in home/away pair " + pair.firstTeam() + " / " + pair.secondTeam());
			} else if (first.teams().size() != second.teams().size()) {
				problems.add("Home/away pair " + pair.firstTeam() + " / " + pair.secondTeam()
						+ " requires coordinated competitions with the same number of teams in the current P1 engine.");
			} else {
				edges.add(new String[] {node(first, pair.firstTeam()), node(second, pair.secondTeam())});
			}
		}
		if (!requiredPairs.isEmpty()) {
			String conflict = parityConflict(edges);
			if (conflict != null) {
				problems.add("Incompatible federation home/away pairs: " + conflict
						+ ". Review the pairs or their exceptions; none will be ignored.");
			}
		}
		return problems;
	}

	/**
	 * Quita los enlaces de estadio incompatibles con los derbis. Un derbi o un enlace obliga a dos
	 * equipos a tener secuencias casa/fuera opuestas; dentro de una misma liga solo existe una
	 * secuencia opuesta (la posición complementaria), así que en cada grupo conectado puede haber
	 * como mucho un equipo por liga y por paridad. Los enlaces que lo rompen se descartan.
	 */
	public static Map<Long, List<Link>> compatibleLinks(League main, List<League> dependents, Map<Long, List<Link>> links,
			List<String> dropped) {
		List<String[]> edges = new ArrayList<>();
		for (League league : all(main, dependents)) {
			for (List<String> d : derbyPairs(league)) {
				edges.add(new String[] { node(league, d.get(0)), node(league, d.get(1)) });
			}
		}
		Map<Long, List<Link>> out = new HashMap<>();
		for (League child : dependents) {
			List<Link> ok = new ArrayList<>();
			for (Link link : links.getOrDefault(child.competitionSeasonId(), List.of())) {
				edges.add(new String[] { node(main, link.parentTeam()), node(child, link.childTeam()) });
				String conflict = parityConflict(edges);
				if (conflict == null) {
					ok.add(link);
				} else {
					edges.remove(edges.size() - 1);
					dropped.add("Stadium link ignored: " + link.parentTeam() + " / " + link.childTeam() + " (" + conflict + ")");
				}
			}
			out.put(child.competitionSeasonId(), ok);
		}
		return out;
	}

	private static String node(League league, String team) {
		return league.code() + ":" + team;
	}

	private static List<List<String>> derbyPairs(League league) {
		List<List<String>> out = new ArrayList<>();
		league.derbies().stream().filter(d -> d.size() == 2).forEach(out::add);
		for (String def : league.checkDefinitions()) {
			String[] p = def.split(",");
			if (p.length >= 3 && p[0].trim().equals("DerbyTeamsCheck")) {
				out.add(List.of(p[1].trim(), p[2].trim()));
			}
		}
		// si los derbis emparejan a todos menos dos equipos, esos dos quedan en posiciones complementarias
		Set<String> paired = new HashSet<>();
		out.forEach(paired::addAll);
		List<String> rest = league.teams().stream().map(t -> t[0]).filter(c -> !paired.contains(c)).toList();
		if (rest.size() == 2 && paired.size() == league.teams().size() - 2) {
			out.add(List.of(rest.get(0), rest.get(1)));
		}
		return out;
	}

	/** Devuelve una descripción del conflicto o null si las restricciones de oposición son compatibles. */
	private static String parityConflict(List<String[]> edges) {
		Map<String, List<String[]>> adj = new HashMap<>();
		for (String[] e : edges) {
			adj.computeIfAbsent(e[0], k -> new ArrayList<>()).add(e);
			adj.computeIfAbsent(e[1], k -> new ArrayList<>()).add(e);
		}
		Map<String, Integer> parity = new HashMap<>();
		for (String start : adj.keySet()) {
			if (parity.containsKey(start)) {
				continue;
			}
			parity.put(start, 0);
			List<String> stack = new ArrayList<>(List.of(start));
			Map<String, String> slot = new HashMap<>();
			while (!stack.isEmpty()) {
				String cur = stack.remove(stack.size() - 1);
				String key = cur.substring(0, cur.indexOf(':')) + "#" + parity.get(cur);
				String other = slot.putIfAbsent(key, cur);
				if (other != null && !other.equals(cur)) {
					return other.substring(other.indexOf(':') + 1) + " and " + cur.substring(cur.indexOf(':') + 1)
							+ " would need the same home/away sequence";
				}
				for (String[] e : adj.get(cur)) {
					String next = e[0].equals(cur) ? e[1] : e[0];
					int p = 1 - parity.get(cur);
					Integer known = parity.putIfAbsent(next, p);
					if (known == null) {
						stack.add(next);
					} else if (known != p) {
						return "odd cycle through " + next.substring(next.indexOf(':') + 1);
					}
				}
			}
		}
		return null;
	}

	public static Summary run(League main, List<League> dependents, Map<Long, List<Link>> allLinks, Params params, Listener listener) {
		return run(main, dependents, allLinks, List.of(), params, listener);
	}

	public static Summary run(League main, List<League> dependents, Map<Long, List<Link>> allLinks,
			List<RequiredPair> requiredPairs, Params params, Listener listener) {
		List<String> validation = validate(main, dependents, allLinks, requiredPairs);
		if (!validation.isEmpty()) {
			throw new IllegalArgumentException(String.join("; ", validation));
		}
		long deadline = System.currentTimeMillis() + params.timeoutMillis();
		List<String> droppedLinks = new ArrayList<>();
		Map<Long, List<Link>> links = compatibleLinks(main, dependents, allLinks, droppedLinks);
		droppedLinks.forEach(listener::info);
		List<String> problems = new ArrayList<>();
		Environment mainEnv = environment(main, problems);
		Map<Long, Environment> envs = new HashMap<>();
		Map<Long, List<Forced>> linkForced = new HashMap<>();
		for (League child : dependents) {
			Environment env = environment(child, problems);
			envs.put(child.competitionSeasonId(), env);
			List<Forced> forced = new ArrayList<>();
			Set<Team> taken = new HashSet<>();
			Set<Team> usedParents = new HashSet<>();
			List<Check> checks = new ArrayList<>(List.of(env.checks));
			for (Link link : links.getOrDefault(child.competitionSeasonId(), List.of())) {
				Team parent = mainEnv.searchTeamByCode(link.parentTeam());
				Team team = env.searchTeamByCode(link.childTeam());
				if (parent == null || team == null || taken.contains(team) || usedParents.contains(parent)) {
					listener.info("Stadium link ignored: " + link.parentTeam() + " / " + link.childTeam());
					continue;
				}
				forced.add(new Forced(team, new ForcePositionsCheck(team, 0), parent, true));
				taken.add(team);
				usedParents.add(parent);
			}
			// el rival de derbi de un equipo enlazado solo cabe en la posición complementaria
			for (Forced f : new ArrayList<>(forced)) {
				for (List<String> d : derbyPairs(child)) {
					String partner = d.get(0).equals(f.team().getCode()) ? d.get(1) : d.get(1).equals(f.team().getCode()) ? d.get(0) : null;
					Team y = partner == null ? null : env.searchTeamByCode(partner);
					if (y != null && taken.add(y)) {
						forced.add(new Forced(y, new ForcePositionsCheck(y, 0), f.parent(), false));
					}
				}
			}
			forced.forEach(f -> checks.add(f.check()));
			env.checks = checks.toArray(new Check[0]);
			env.nChecks = env.checks.length;
			List<Team> order = new ArrayList<>(forced.stream().map(Forced::team).toList());
			for (Team t : env.teams) {
				if (!order.contains(t)) {
					order.add(t);
				}
			}
			env.teams = order.toArray(new Team[0]);
			linkForced.put(child.competitionSeasonId(), forced);
		}
		if (!problems.isEmpty()) {
			throw new IllegalArgumentException(String.join("; ", problems));
		}

		SecureRandom random = new SecureRandom();
		Map<Integer, List<String>> pools = new HashMap<>();
		long poolBudget = Math.max(5_000, params.timeoutMillis() * 2 / 5);
		for (League league : all(main, dependents)) {
			int n = league.teams().size();
			if (pools.containsKey(n)) {
				continue;
			}
			Environment env = league == main ? mainEnv : envs.get(league.competitionSeasonId());
			int[] reps = params.forceRepetitions() != null && params.forceRepetitions().length == n - 1 ? params.forceRepetitions()
					: TwoColumnsGenPosAlg.defaultForceRepetitions(n);
			int target = Math.max(200, params.candidates());
			Set<String> pool = new LinkedHashSet<>();
			long poolDeadline = System.currentTimeMillis() + poolBudget / (dependents.size() + 1);
			int emptyRuns = 0;
			while (pool.size() < target && System.currentTimeMillis() < poolDeadline && !listener.cancelled() && emptyRuns < 5) {
				int before = pool.size();
				pool.addAll(TwoColumnsGenPosAlg.generatePatterns(env, reps, target - pool.size(),
						Math.max(500, poolDeadline - System.currentTimeMillis()), listener::cancelled));
				emptyRuns = pool.size() == before ? emptyRuns + 1 : 0;
			}
			if (pool.isEmpty()) {
				throw new IllegalStateException("No position pattern could be generated for " + n + " teams");
			}
			listener.info("Position patterns for " + n + " teams: " + pool.size());
			pools.put(n, new ArrayList<>(pool));
		}

		AssignPositions mainAssign = AssignPositions.inMemory(mainEnv, params.maxAssignNodes());
		Map<Long, AssignPositions> childAssign = new HashMap<>();
		dependents.forEach(child -> childAssign.put(child.competitionSeasonId(),
				AssignPositions.inMemory(envs.get(child.competitionSeasonId()), params.maxAssignNodes())));
		Set<String> seen = new HashSet<>();
		int found = 0;
		int tries = 0;
		int mainFails = 0;
		int collisions = 0;
		int childFails = 0;
		boolean timedOut = false;
		List<String> mainPool = pools.get(main.teams().size());
		while (found < params.candidates() && tries < params.maxTries() && !listener.cancelled()) {
			if (System.currentTimeMillis() > deadline) {
				timedOut = true;
				break;
			}
			tries++;
			Tournament mainTour = Tournament.readFromLine(mainEnv, mainPool.get(random.nextInt(mainPool.size())));
			if (!mainAssign.assignPositions(mainTour)) {
				mainFails++;
				continue;
			}
			List<LeagueResult> results = new ArrayList<>();
			results.add(result(main, mainTour));
			boolean allOk = true;
			for (League child : dependents) {
				long id = child.competitionSeasonId();
				Set<Integer> used = new HashSet<>();
				boolean collision = false;
				for (Forced f : linkForced.get(id)) {
					int parentPos = mainTour.mapPosToTeams.getKeyOfTeam(f.parent());
					int pos = !f.complement() ? parentPos : parentPos % 2 == 0 ? parentPos - 1 : parentPos + 1;
					f.check().setRound(pos);
					collision |= !used.add(pos);
				}
				if (collision) {
					collisions++;
					allOk = false;
					break;
				}
				List<String> childPool = pools.get(child.teams().size());
				boolean ok = false;
				for (int attempt = 0; attempt < 20 && !ok; attempt++) {
					Tournament childTour = Tournament.readFromLine(envs.get(id), childPool.get(random.nextInt(childPool.size())));
					if (childAssign.get(id).assignPositions(childTour)) {
						results.add(result(child, childTour));
						ok = true;
					}
				}
				if (!ok) {
					childFails++;
					allOk = false;
					break;
				}
			}
			if (!allOk) {
				continue;
			}
			if (!satisfiesPairs(results, requiredPairs)) {
				continue;
			}
			StringBuilder signature = new StringBuilder();
			results.forEach(r -> signature.append(r.pattern()).append(';').append(r.positionTeams()).append('|'));
			if (!seen.add(signature.toString())) {
				continue;
			}
			found++;
			listener.candidate(new Candidate(found, results), tries);
		}
		if (found < params.candidates()) {
			listener.info("Rejected tries: " + mainFails + " main league, " + collisions + " stadium link collisions, " + childFails
					+ " dependent league");
		}
		return new Summary(found, tries, timedOut);
	}

	private static boolean satisfiesPairs(List<LeagueResult> results, List<RequiredPair> pairs) {
		if (pairs.isEmpty()) {
			return true;
		}
		Map<Long, Map<String, Set<String>>> homeRounds = new HashMap<>();
		for (LeagueResult result : results) {
			Map<String, Set<String>> rounds = new HashMap<>();
			for (String[] match : fixtures(result.pattern(), result.positionTeams())) {
				rounds.computeIfAbsent(match[1], k -> new HashSet<>()).add(match[0]);
			}
			homeRounds.put(result.competitionSeasonId(), rounds);
		}
		for (RequiredPair pair : pairs) {
			Set<String> first = homeRounds.get(pair.firstLeague()).get(pair.firstTeam());
			Set<String> second = homeRounds.get(pair.secondLeague()).get(pair.secondTeam());
			if (first == null || second == null || first.stream().anyMatch(second::contains)) {
				return false;
			}
		}
		return true;
	}

	/** Partidos (ida y vuelta) de una plantilla: {jornada, local, visitante}; la vuelta invierte localía. */
	public static List<String[]> fixtures(String pattern, List<String> positionTeams) {
		int n = positionTeams.size();
		int perRound = n / 2;
		int rounds = n - 1;
		if (pattern.length() != 4 * rounds * perRound) {
			throw new IllegalArgumentException("Pattern length " + pattern.length() + " does not match " + n + " teams");
		}
		List<String[]> first = new ArrayList<>();
		List<String[]> second = new ArrayList<>();
		int idx = 0;
		for (int r = 1; r <= rounds; r++) {
			for (int m = 0; m < perRound; m++) {
				String home = positionTeams.get(Integer.parseInt(pattern.substring(idx, idx + 2)) - 1);
				String away = positionTeams.get(Integer.parseInt(pattern.substring(idx + 2, idx + 4)) - 1);
				idx += 4;
				first.add(new String[] { String.valueOf(r), home, away });
				second.add(new String[] { String.valueOf(r + rounds), away, home });
			}
		}
		first.addAll(second);
		return first;
	}

	private static LeagueResult result(League league, Tournament tour) {
		List<String> teams = new ArrayList<>();
		for (int pos = 1; pos <= league.teams().size(); pos++) {
			teams.add(tour.mapPosToTeams.get(pos).getCode());
		}
		return new LeagueResult(league.competitionSeasonId(), tour.toStringOneLine(), teams);
	}

	private static Environment environment(League league, List<String> problems) {
		Environment env = Environment.inMemory(league.code(), league.code(), league.teams());
		List<Check> checks = new ArrayList<>();
		for (String def : league.checkDefinitions()) {
			String type = def.split(",", 2)[0].trim();
			if (!DRAW_CHECKS.contains(type)) {
				problems.add(league.code() + ": unsupported draw rule '" + def + "' (no draw handler registered)");
				continue;
			}
			Check check = CheckProcessor.createCheck(env, def);
			if (check == null) {
				problems.add(league.code() + ": invalid draw rule '" + def + "' (unknown team or wrong parameters)");
			} else {
				checks.add(check);
			}
		}
		Map<Integer, String> forcedByPosition = new HashMap<>();
		Map<String, Integer> positionByForcedTeam = new HashMap<>();
		for (Check check : checks) {
			if (check instanceof ForcePositionsCheck forced) {
				String team = forced.getTeam1().getCode();
				int position = forced.getRound();
				Integer previousPosition = positionByForcedTeam.putIfAbsent(team, position);
				String previousTeam = forcedByPosition.putIfAbsent(position, team);
				if (previousPosition != null && previousPosition != position) {
					problems.add(league.code() + ": team " + team + " is forced to positions " + previousPosition + " and " + position);
				}
				if (previousTeam != null && !previousTeam.equals(team)) {
					problems.add(league.code() + ": teams " + previousTeam + " and " + team + " are both forced to position " + position);
				}
			}
		}
		for (List<String> pair : league.derbies() == null ? List.<List<String>>of() : league.derbies()) {
			if (pair.size() == 2) {
				Team a = env.searchTeamByCode(pair.get(0));
				Team b = env.searchTeamByCode(pair.get(1));
				if (a != null && b != null) {
					checks.add(new DerbyTeamsCheck(a, b));
				}
			}
		}
		env.checks = checks.toArray(new Check[0]);
		env.nChecks = env.checks.length;
		return env;
	}

	private static List<League> all(League main, List<League> dependents) {
		List<League> all = new ArrayList<>();
		all.add(main);
		all.addAll(dependents);
		return all;
	}
}
