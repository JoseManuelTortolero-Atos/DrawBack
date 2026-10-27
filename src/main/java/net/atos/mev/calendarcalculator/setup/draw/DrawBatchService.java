package net.atos.mev.calendarcalculator.setup.draw;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import net.atos.mev.calendarcalculator.setup.SeasonSetupRepository;
import net.atos.mev.calendarcalculator.setup.SetupDtos;
import net.atos.mev.calendarcalculator.setup.rules.RuleScopeService;
import net.atos.mev.calendarcalculator.setup.SetupExceptions;

/**
 * Camino 1 (emparejamientos / sorteo): genera N calendarios candidatos en segundo plano a partir
 * de la configuración en BD, los guarda, los entrega en ZIP y aplica el elegido como fixtures.
 */
@Service
public class DrawBatchService {

	private static final Logger log = LoggerFactory.getLogger(DrawBatchService.class);

	private final DrawBatchRepository repo;
	private final SeasonSetupRepository setups;
	private final JdbcClient jdbc;
	private final ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "draw-batch");
		t.setDaemon(true);
		return t;
	});
	private final Map<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

	private final RuleScopeService scopedRules;

	public DrawBatchService(DrawBatchRepository repo, SeasonSetupRepository setups, JdbcClient jdbc, RuleScopeService scopedRules) {
		this.repo = repo;
		this.scopedRules = scopedRules;
		this.setups = setups;
		this.jdbc = jdbc;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void failOrphans() {
		int n = repo.failOrphans();
		if (n > 0) {
			log.warn("{} orphan draw batch(es) marked as FAILED", n);
		}
	}

	@PreDestroy
	void shutdown() {
		cancelFlags.values().forEach(f -> f.set(true));
		pool.shutdownNow();
	}

	/** Entrada del motor construida desde BD (liga principal + dependientes + enlaces de estadio). */
	record Input(DrawEngine.League main, List<DrawEngine.League> dependents, Map<Long, List<DrawEngine.Link>> links,
			List<DrawDtos.DrawLeague> leagues, List<DrawEngine.RequiredPair> requiredPairs, List<String> problems) {
	}

	Input input(String competitionId, String season, boolean includeDependents) {
		SetupDtos.SeasonSetup storedMain = setups.load(competitionId, season);
		List<String> federationIds = jdbc.sql("""
				SELECT cs.competition_id FROM competition_season cs
				JOIN competition c ON c.id = cs.competition_id
				WHERE cs.season_id = :s AND c.federation_code =
				    (SELECT federation_code FROM competition WHERE id = :c)
				ORDER BY cs.competition_id""").param("s", season).param("c", competitionId).query(String.class).list();
		Map<Long, SetupDtos.SeasonSetup> available = new LinkedHashMap<>();
		Map<Long, Set<String>> pairKeys = new HashMap<>();
		Map<Long, List<String>> missing = new HashMap<>();
		Set<String> universe = new HashSet<>(jdbc.sql("SELECT code FROM team").query(String.class).list());
		for (String id : federationIds) {
			SetupDtos.SeasonSetup setup = id.equals(competitionId) ? storedMain : setups.load(id, season);
			long cs = setups.competitionSeasonId(id, season);
			available.put(cs, setup);
			Set<String> keys = new HashSet<>();
			for (var rule : scopedRules.effectiveForDraw(setup, universe).rules()) {
				if (rule.applied() && "DerbyTeams".equals(rule.ruleType())) {
					keys.add(pairKey(rule.rule().a(), rule.rule().b()));
				}
			}
			pairKeys.put(cs, keys);
		}
		long mainCs = setups.competitionSeasonId(competitionId, season);
		Set<DrawEngine.RequiredPair> allPairs = new LinkedHashSet<>();
		for (var first : available.entrySet()) {
			Set<String> firstTeams = codes(first.getValue());
			for (String key : pairKeys.get(first.getKey())) {
				String[] pair = key.split("/");
				if (!firstTeams.contains(pair[0]) && !firstTeams.contains(pair[1])) {
					continue;
				}
				if (firstTeams.contains(pair[0]) && firstTeams.contains(pair[1])) {
					continue;
				}
				String local = firstTeams.contains(pair[0]) ? pair[0] : pair[1];
				String other = local.equals(pair[0]) ? pair[1] : pair[0];
				boolean registered = false;
				for (var second : available.entrySet()) {
					if (first.getKey().equals(second.getKey()) || !codes(second.getValue()).contains(other)) {
						continue;
					}
					registered = true;
					if (!pairKeys.get(second.getKey()).contains(key)) {
						continue; // La liga del otro equipo ha exceptuado esta relacion.
					}
					allPairs.add(first.getKey() < second.getKey()
							? new DrawEngine.RequiredPair(first.getKey(), local, second.getKey(), other)
							: new DrawEngine.RequiredPair(second.getKey(), other, first.getKey(), local));
				}
				if (!registered) {
					missing.computeIfAbsent(first.getKey(), k -> new ArrayList<>()).add(
							"Home/away pair " + local + " / " + other + ": register " + other
							+ " in a competition of the same federation and season, or review the rule. No rule was ignored.");
				}
			}
		}
		Set<Long> selected = new LinkedHashSet<>(List.of(mainCs));
		if (includeDependents) {
			boolean changed;
			do {
				int before = selected.size();
				for (var entry : available.entrySet()) {
					if (available.entrySet().stream().anyMatch(parent -> selected.contains(parent.getKey())
							&& parent.getValue().competitionId().equals(entry.getValue().dependsOn()))) {
						selected.add(entry.getKey());
					}
				}
				for (DrawEngine.RequiredPair pair : allPairs) {
					if (selected.contains(pair.firstLeague()) || selected.contains(pair.secondLeague())) {
						selected.add(pair.firstLeague());
						selected.add(pair.secondLeague());
					}
				}
				changed = before != selected.size();
			} while (changed);
		}
		List<DrawEngine.RequiredPair> requiredPairs = allPairs.stream().filter(p -> selected.contains(p.firstLeague())
				|| selected.contains(p.secondLeague())).toList();
		List<String> problems = selected.stream().flatMap(cs -> missing.getOrDefault(cs, List.of()).stream()).toList();
		SetupDtos.SeasonSetup main = scopedRules.withScopedRules(storedMain);
		List<DrawDtos.DrawLeague> leagues = new ArrayList<>();
		leagues.add(new DrawDtos.DrawLeague(competitionId, main.code(), mainCs));
		List<DrawEngine.League> dependents = new ArrayList<>();
		Map<Long, List<DrawEngine.Link>> links = new HashMap<>();
		if (selected.size() > 1) {
			Set<String> mainTeams = codes(main);
			for (long childCs : selected) {
				if (childCs == mainCs) {
					continue;
				}
				SetupDtos.SeasonSetup child = scopedRules.withScopedRules(available.get(childCs));
				String childId = child.competitionId();
				leagues.add(new DrawDtos.DrawLeague(childId, child.code(), childCs));
				dependents.add(league(childCs, child));
				Set<String> childTeams = codes(child);
				Map<String, DrawEngine.Link> childLinks = new LinkedHashMap<>();
				List<List<String>> pairs = new ArrayList<>();
				pairs.addAll(main.notHomeSameDay() == null ? List.of() : main.notHomeSameDay());
				pairs.addAll(child.notHomeSameDay() == null ? List.of() : child.notHomeSameDay());
				for (List<String> pair : pairs) {
					if (pair.size() != 2) {
						continue;
					}
					if (mainTeams.contains(pair.get(0)) && childTeams.contains(pair.get(1))) {
						childLinks.putIfAbsent(pair.get(0) + "/" + pair.get(1), new DrawEngine.Link(pair.get(0), pair.get(1)));
					} else if (mainTeams.contains(pair.get(1)) && childTeams.contains(pair.get(0))) {
						childLinks.putIfAbsent(pair.get(1) + "/" + pair.get(0), new DrawEngine.Link(pair.get(1), pair.get(0)));
					}
				}
				links.put(childCs, new ArrayList<>(childLinks.values()));
			}
		}
		return new Input(league(mainCs, main), dependents, links, leagues, requiredPairs, problems);
	}

	private static String pairKey(String a, String b) {
		return a.compareTo(b) < 0 ? a + "/" + b : b + "/" + a;
	}

	private static Set<String> codes(SetupDtos.SeasonSetup s) {
		Set<String> out = new HashSet<>();
		s.teams().forEach(t -> out.add(t.code()));
		return out;
	}

	private static DrawEngine.League league(long cs, SetupDtos.SeasonSetup s) {
		List<String[]> teams = s.teams().stream().map(t -> new String[] { t.code(), t.name() == null ? t.code() : t.name() }).toList();
		List<String> checks = new ArrayList<>();
		for (SetupDtos.Rule rule : s.rules() == null ? List.<SetupDtos.Rule>of() : s.rules()) {
			String def = rule.definition();
			if (DrawEngine.isCheckDefinition(def)) {
				checks.add(def.trim());
			}
		}
		return new DrawEngine.League(cs, s.code(), teams, checks, s.derbies() == null ? List.of() : s.derbies());
	}

	/** Validación previa: equipos pares, reglas de sorteo correctas y enlaces de estadio coherentes. */
	public List<SetupDtos.ValidationIssue> validate(String competitionId, String season, boolean includeDependents) {
		Input in = input(competitionId, season, includeDependents);
		List<SetupDtos.ValidationIssue> issues = new ArrayList<>();
		in.problems().forEach(p -> issues.add(SetupDtos.ValidationIssue.error("draw", p)));
		DrawEngine.validate(in.main(), in.dependents(), in.links(), in.requiredPairs())
				.forEach(p -> issues.add(SetupDtos.ValidationIssue.error("draw", p)));
		List<String> dropped = new ArrayList<>();
		DrawEngine.compatibleLinks(in.main(), in.dependents(), in.links(), dropped);
		dropped.forEach(d -> issues.add(SetupDtos.ValidationIssue.warn("draw", d)));
		return issues;
	}

	public synchronized DrawDtos.DrawBatch start(String competitionId, String season, DrawDtos.StartDrawRequest req) {
		int candidates = requireCandidates(req == null ? null : req.candidates());
		int timeoutMinutes = req == null || req.timeoutMinutes() == null ? 30 : req.timeoutMinutes();
		boolean includeDependents = req == null || req.includeDependents() == null || req.includeDependents();
		if (timeoutMinutes < 1 || timeoutMinutes > 240) {
			throw new IllegalArgumentException("timeoutMinutes must be between 1 and 240");
		}
		Input in = input(competitionId, season, includeDependents);
		List<String> problems = new ArrayList<>(in.problems());
		problems.addAll(DrawEngine.validate(in.main(), in.dependents(), in.links(), in.requiredPairs()));
		if (!problems.isEmpty()) {
			throw new SetupExceptions.Invalid("The draw configuration has errors",
					problems.stream().map(p -> SetupDtos.ValidationIssue.error("draw", p)).toList());
		}
		int[] reps = null;
		if (req != null && req.forceRepetitions() != null && !req.forceRepetitions().isEmpty()) {
			reps = req.forceRepetitions().stream().mapToInt(Integer::intValue).toArray();
			int n = in.main().teams().size();
			long ones = req.forceRepetitions().stream().filter(v -> v == 1).count();
			if (reps.length != n - 1 || ones != n / 2 - 1 || req.forceRepetitions().stream().anyMatch(v -> v != 0 && v != 1)) {
				throw new IllegalArgumentException("forceRepetitions needs " + (n - 1) + " values 0/1 with " + (n / 2 - 1) + " ones");
			}
		}
		List<Long> ids = in.leagues().stream().map(DrawDtos.DrawLeague::competitionSeasonId).toList();
		if (repo.anyRunning(ids)) {
			throw new SetupExceptions.Conflict("A draw batch is already running for " + competitionId + " " + season);
		}
		String id = UUID.randomUUID().toString();
		String params = "candidates=" + candidates + ";timeoutMinutes=" + timeoutMinutes + ";includeDependents=" + includeDependents
				+ (reps == null ? "" : ";forceRepetitions=" + java.util.Arrays.toString(reps));
		repo.create(id, in.leagues().get(0).competitionSeasonId(), candidates, params, in.leagues());
		AtomicBoolean cancelled = new AtomicBoolean(false);
		cancelFlags.put(id, cancelled);
		int maxTries = (int) Math.min(Integer.MAX_VALUE, Math.max(100_000L, (long) candidates * 200));
		DrawEngine.Params engineParams = new DrawEngine.Params(candidates, maxTries, timeoutMinutes * 60_000L,
				200_000, reps);
		pool.submit(() -> run(id, in, engineParams, cancelled));
		return repo.require(id);
	}

	static int requireCandidates(Integer candidates) {
		if (candidates == null || candidates < 1) {
			throw new IllegalArgumentException("candidates is required and must be a positive integer");
		}
		return candidates;
	}

	private void run(String id, Input in, DrawEngine.Params params, AtomicBoolean cancelled) {
		List<String> infos = new ArrayList<>();
		int[] lastTries = { 0 };
		try {
			DrawEngine.Summary summary = DrawEngine.run(in.main(), in.dependents(), in.links(), in.requiredPairs(), params, new DrawEngine.Listener() {
				public void candidate(DrawEngine.Candidate candidate, int tries) {
					lastTries[0] = tries;
					repo.addCandidate(id, candidate, tries);
				}

				public void info(String message) {
					log.info("Draw {}: {}", id, message);
					infos.add(message);
				}

				public boolean cancelled() {
					return cancelled.get() || Thread.currentThread().isInterrupted();
				}
			});
			String detail = summary.found() + " of " + params.candidates() + " candidates in " + summary.tries() + " tries";
			if (cancelled.get()) {
				repo.finish(id, "CANCELLED", summary.tries(), "Cancelled: " + detail + infoSuffix(infos));
			} else if (summary.found() == 0) {
				repo.finish(id, "FAILED", summary.tries(), "No candidate satisfies the draw rules (" + detail + "). Relax the rules." + infoSuffix(infos));
			} else if (summary.found() < params.candidates()) {
				repo.finish(id, "DONE", summary.tries(), "Partial: " + detail + (summary.timedOut() ? " (time limit reached)" : "") + infoSuffix(infos));
			} else {
				repo.finish(id, "DONE", summary.tries(), detail + infoSuffix(infos));
			}
		} catch (RuntimeException e) {
			log.error("Draw batch {} failed", id, e);
			repo.finish(id, "FAILED", lastTries[0], e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
		} finally {
			cancelFlags.remove(id);
		}
	}

	private static String infoSuffix(List<String> infos) {
		List<String> ignored = infos.stream().filter(i -> i.startsWith("Stadium link ignored")).toList();
		return ignored.isEmpty() ? "" : ". " + String.join("; ", ignored);
	}

	public DrawDtos.DrawBatch cancel(String id) {
		DrawDtos.DrawBatch batch = repo.require(id);
		AtomicBoolean flag = cancelFlags.get(id);
		if (flag == null || !"RUNNING".equals(batch.status())) {
			throw new SetupExceptions.Conflict("Draw batch " + id + " is not running");
		}
		flag.set(true);
		return batch;
	}

	public List<DrawDtos.DrawBatch> list(String competitionId, String season) {
		return repo.list(setups.competitionSeasonId(competitionId, season), season);
	}

	public DrawDtos.DrawBatch get(String id) {
		return repo.require(id);
	}

	public DrawDtos.CandidatePage candidates(String id, int offset, int limit) {
		DrawDtos.DrawBatch batch = repo.require(id);
		long mainCs = batch.leagues().get(0).competitionSeasonId();
		int lim = Math.max(1, Math.min(limit, 500));
		int off = Math.max(0, offset);
		List<DrawDtos.CandidateSummary> items = repo.page(id, mainCs, off, lim).stream().map(e -> {
			DrawBatchRepository.StoredLeague l = e.getValue();
			int perRound = l.positionTeams().size() / 2;
			List<String> first = DrawEngine.fixtures(l.pattern(), l.positionTeams()).subList(0, perRound).stream()
					.map(m -> m[1] + "-" + m[2]).toList();
			return new DrawDtos.CandidateSummary(e.getKey(), java.util.Objects.equals(batch.chosenNo(), e.getKey()), String.join(", ", first));
		}).toList();
		return new DrawDtos.CandidatePage(repo.countCandidates(id, mainCs), off, lim, items);
	}

	public DrawDtos.Candidate candidate(String id, int no) {
		DrawDtos.DrawBatch batch = repo.require(id);
		List<DrawBatchRepository.StoredLeague> stored = repo.candidate(id, no);
		if (stored.isEmpty()) {
			throw new SetupExceptions.NotFound("Candidate " + no + " not found in draw batch " + id);
		}
		Map<Long, DrawDtos.DrawLeague> byCs = new HashMap<>();
		batch.leagues().forEach(l -> byCs.put(l.competitionSeasonId(), l));
		List<DrawDtos.CandidateLeague> leagues = stored.stream().map(s -> {
			DrawDtos.DrawLeague l = byCs.get(s.competitionSeasonId());
			List<DrawDtos.DrawMatch> matches = DrawEngine.fixtures(s.pattern(), s.positionTeams()).stream()
					.map(m -> new DrawDtos.DrawMatch(Integer.parseInt(m[0]), m[1], m[2])).toList();
			return new DrawDtos.CandidateLeague(l.competitionId(), l.code(), s.positionTeams(), matches);
		}).toList();
		return new DrawDtos.Candidate(no, batch.chosenNo() != null && batch.chosenNo() == no, leagues);
	}

	public DrawDtos.DrawBatch choose(String id, int no) {
		DrawDtos.DrawBatch batch = repo.require(id);
		if ("RUNNING".equals(batch.status())) {
			throw new SetupExceptions.Conflict("Wait until the draw batch finishes (or cancel it) before choosing a candidate");
		}
		repo.choose(id, no);
		return repo.require(id);
	}

	public int deleteDiscarded(String id) {
		DrawDtos.DrawBatch batch = repo.require(id);
		if (batch.chosenNo() == null) {
			throw new SetupExceptions.Conflict("Choose a candidate before deleting the discarded ones");
		}
		return repo.deleteDiscarded(id, batch.chosenNo());
	}

	public void delete(String id) {
		DrawDtos.DrawBatch batch = repo.require(id);
		if ("RUNNING".equals(batch.status())) {
			throw new SetupExceptions.Conflict("Cancel the draw batch before deleting it");
		}
		repo.delete(id);
	}

	public String zipName(String id) {
		DrawDtos.DrawBatch b = repo.require(id);
		String codes = String.join("-", b.leagues().stream().map(DrawDtos.DrawLeague::code).toList());
		return "Sorteo_" + codes + "_" + b.season() + "_" + b.generated() + "_candidatos.zip";
	}

	/**
	 * ZIP para el cliente: XLSX y CSV por candidato y liga, un CSV
	 * con todos los candidatos por liga y un LEEME.txt.
	 */
	public void writeZip(String id, OutputStream out) {
		DrawDtos.DrawBatch batch = repo.require(id);
		Map<Long, DrawDtos.DrawLeague> byCs = new HashMap<>();
		Map<Long, Map<String, String>> names = new HashMap<>();
		batch.leagues().forEach(l -> {
			byCs.put(l.competitionSeasonId(), l);
			names.put(l.competitionSeasonId(), repo.teamNames(l.competitionSeasonId()));
		});
		try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
			DrawCandidateExcel excel = new DrawCandidateExcel();
			Writer w = new OutputStreamWriter(zip, StandardCharsets.UTF_8);
			zip.putNextEntry(new ZipEntry("LEEME.txt"));
			w.write("Sorteo " + batch.competitionId() + " " + batch.season() + "\r\n");
			w.write("Lote: " + batch.id() + "\r\n");
			w.write("Ligas: " + String.join(", ", batch.leagues().stream().map(DrawDtos.DrawLeague::code).toList()) + "\r\n");
			w.write("Candidatos: " + batch.generated() + "\r\n");
			if (batch.chosenNo() != null) {
				w.write("Candidato elegido: " + batch.chosenNo() + "\r\n");
			}
			w.write("\r\nCada candidato N tiene un XLSX y un CSV por liga en candidatos/NNNN/. El numero de candidato es el que hay que\r\n");
			w.write("indicar para elegirlo. <LIGA>_todos.csv contiene todos los candidatos en un unico fichero (filtrable).\r\n");
			w.write("Los XLSX son emparejamientos P1, sin fechas ni horarios P2. No se realiza un nuevo sorteo al descargarlos.\r\n");
			w.write("Botola D1/D2 (16 posiciones) y CNPFF D1/D2 (12) conservan sus plantillas antiguas, omitiendo BYE.\r\n");
			w.write("Otras ligas o tamanos usan un XLSX identificado como formato tabular, sin plantilla antigua compatible.\r\n");
			w.flush();
			zip.closeEntry();
			Map<Long, StringBuilder> all = new LinkedHashMap<>();
			batch.leagues().forEach(l -> all.put(l.competitionSeasonId(), new StringBuilder("sep=,\r\nCandidate,Round,Home,Away,Home Name,Away Name\r\n")));
			repo.forEachCandidate(id, (no, league) -> {
				DrawDtos.DrawLeague l = byCs.get(league.competitionSeasonId());
				Map<String, String> teamNames = names.get(league.competitionSeasonId());
				StringBuilder csv = new StringBuilder("sep=,\r\nRound,Home,Away,Home Name,Away Name\r\n");
				List<String[]> matches = DrawEngine.fixtures(league.pattern(), league.positionTeams());
				for (String[] m : matches) {
					String line = m[0] + "," + m[1] + "," + m[2] + "," + csv(teamNames.getOrDefault(m[1], m[1])) + ","
							+ csv(teamNames.getOrDefault(m[2], m[2])) + "\r\n";
					csv.append(line);
					all.get(league.competitionSeasonId()).append(no).append(',').append(line);
				}
				try {
					zip.putNextEntry(new ZipEntry(String.format("candidatos/%04d/schedule_%s_%04d.csv", no, l.code(), no)));
					w.write(csv.toString());
					w.flush();
					zip.closeEntry();
					zip.putNextEntry(new ZipEntry(String.format("candidatos/%04d/calendar_%s_%04d.xlsx", no, l.code(), no)));
					excel.write(l, batch.season(), no, league.positionTeams().size(), matches, zip);
					zip.closeEntry();
				} catch (IOException e) {
					throw new UncheckedIOException(e);
				}
			});
			for (DrawDtos.DrawLeague l : batch.leagues()) {
				zip.putNextEntry(new ZipEntry(l.code() + "_todos.csv"));
				w.write(all.get(l.competitionSeasonId()).toString());
				w.flush();
				zip.closeEntry();
			}
			zip.finish();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String csv(String value) {
		return value.contains(",") || value.contains("\"") ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
	}
}
