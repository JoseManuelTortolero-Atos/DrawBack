package net.atos.mev.calendarcalculator.setup.draw;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import net.atos.mev.calendarcalculator.setup.SetupExceptions;

/** Persistencia de lotes y candidatos de sorteo. SQL estándar (PostgreSQL y SQL Server). */
@Repository
public class DrawBatchRepository {

	private final JdbcClient jdbc;

	public DrawBatchRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public record StoredLeague(long competitionSeasonId, String pattern, List<String> positionTeams) {
	}

	@Transactional
	public void create(String id, long mainCs, int requested, String params, List<DrawDtos.DrawLeague> leagues) {
		jdbc.sql("""
				INSERT INTO draw_batch (id, competition_season_id, status, requested, generated_count, tries, params, created_at)
				VALUES (:id, :cs, 'RUNNING', :req, 0, 0, :params, CURRENT_TIMESTAMP)""")
				.param("id", id).param("cs", mainCs).param("req", requested).param("params", params).update();
		for (int i = 0; i < leagues.size(); i++) {
			DrawDtos.DrawLeague l = leagues.get(i);
			jdbc.sql("""
					INSERT INTO draw_batch_league (batch_id, competition_season_id, league_order, competition_id, code)
					VALUES (:id, :cs, :o, :cid, :code)""")
					.param("id", id).param("cs", l.competitionSeasonId()).param("o", i).param("cid", l.competitionId()).param("code", l.code())
					.update();
		}
	}

	@Transactional
	public void addCandidate(String batchId, DrawEngine.Candidate candidate, int tries) {
		for (DrawEngine.LeagueResult r : candidate.leagues()) {
			jdbc.sql("""
					INSERT INTO draw_candidate_league (batch_id, candidate_no, competition_season_id, pattern, position_teams)
					VALUES (:id, :no, :cs, :p, :t)""")
					.param("id", batchId).param("no", candidate.no()).param("cs", r.competitionSeasonId()).param("p", r.pattern())
					.param("t", String.join(",", r.positionTeams())).update();
		}
		jdbc.sql("UPDATE draw_batch SET generated_count = :n, tries = :t WHERE id = :id")
				.param("n", candidate.no()).param("t", tries).param("id", batchId).update();
	}

	public void finish(String batchId, String status, int tries, String message) {
		jdbc.sql("""
				UPDATE draw_batch SET status = :s, tries = CASE WHEN :t > tries THEN :t ELSE tries END, message = :m,
				       finished_at = CURRENT_TIMESTAMP WHERE id = :id""")
				.param("s", status).param("t", tries).param("m", truncate(message)).param("id", batchId).update();
	}

	public int failOrphans() {
		return jdbc.sql("""
				UPDATE draw_batch SET status = 'FAILED', finished_at = CURRENT_TIMESTAMP,
				       message = 'Interrupted: the application was restarted (generated candidates are kept)'
				 WHERE status = 'RUNNING'""").update();
	}

	public boolean anyRunning(List<Long> competitionSeasonIds) {
		return jdbc.sql("""
				SELECT COUNT(*) FROM draw_batch b JOIN draw_batch_league l ON l.batch_id = b.id
				 WHERE b.status = 'RUNNING' AND l.competition_season_id IN (:ids)""")
				.param("ids", competitionSeasonIds).query(Integer.class).single() > 0;
	}

	public List<DrawDtos.DrawBatch> list(long cs, String season) {
		List<String> ids = jdbc.sql("""
				SELECT b.id, b.created_at FROM draw_batch b
				 WHERE EXISTS (SELECT 1 FROM draw_batch_league l WHERE l.batch_id = b.id AND l.competition_season_id = :cs)
				 ORDER BY b.created_at DESC""")
				.param("cs", cs).query((rs, n) -> rs.getString("id")).list();
		return ids.stream().map(id -> get(id).orElseThrow()).toList();
	}

	public Optional<DrawDtos.DrawBatch> get(String id) {
		List<DrawDtos.DrawLeague> leagues = leagues(id);
		return jdbc.sql("""
				SELECT b.*, cs.competition_id AS main_competition, cs.season_id AS season_id FROM draw_batch b
				  JOIN competition_season cs ON cs.id = b.competition_season_id WHERE b.id = :id""")
				.param("id", id)
				.query((rs, n) -> new DrawDtos.DrawBatch(rs.getString("id"), rs.getString("main_competition"), rs.getString("season_id"),
						rs.getString("status"), rs.getInt("requested"), rs.getInt("generated_count"), rs.getInt("tries"), rs.getString("message"),
						rs.getObject("chosen_no") == null ? null : rs.getInt("chosen_no"), instant(rs.getTimestamp("created_at")),
						instant(rs.getTimestamp("finished_at")), instant(rs.getTimestamp("chosen_at")), leagues))
				.optional();
	}

	public DrawDtos.DrawBatch require(String id) {
		return get(id).orElseThrow(() -> new SetupExceptions.NotFound("Unknown draw batch " + id));
	}

	public List<DrawDtos.DrawLeague> leagues(String id) {
		return jdbc.sql("SELECT competition_id, code, competition_season_id FROM draw_batch_league WHERE batch_id = :id ORDER BY league_order")
				.param("id", id)
				.query((rs, n) -> new DrawDtos.DrawLeague(rs.getString("competition_id"), rs.getString("code"), rs.getLong("competition_season_id")))
				.list();
	}

	public int countCandidates(String id, long mainCs) {
		return jdbc.sql("SELECT COUNT(*) FROM draw_candidate_league WHERE batch_id = :id AND competition_season_id = :cs")
				.param("id", id).param("cs", mainCs).query(Integer.class).single();
	}

	/** Candidatos de la liga principal paginados: número → plantilla. */
	public List<Map.Entry<Integer, StoredLeague>> page(String id, long mainCs, int offset, int limit) {
		return jdbc.sql("""
				SELECT candidate_no, competition_season_id, pattern, position_teams FROM draw_candidate_league
				 WHERE batch_id = :id AND competition_season_id = :cs
				 ORDER BY candidate_no OFFSET :off ROWS FETCH NEXT :lim ROWS ONLY""")
				.param("id", id).param("cs", mainCs).param("off", offset).param("lim", limit)
				.query((rs, n) -> Map.entry(rs.getInt("candidate_no"), stored(rs.getLong("competition_season_id"), rs.getString("pattern"),
						rs.getString("position_teams"))))
				.list();
	}

	public List<StoredLeague> candidate(String id, int no) {
		return jdbc.sql("""
				SELECT c.competition_season_id, c.pattern, c.position_teams FROM draw_candidate_league c
				  JOIN draw_batch_league l ON l.batch_id = c.batch_id AND l.competition_season_id = c.competition_season_id
				 WHERE c.batch_id = :id AND c.candidate_no = :no ORDER BY l.league_order""")
				.param("id", id).param("no", no)
				.query((rs, n) -> stored(rs.getLong("competition_season_id"), rs.getString("pattern"), rs.getString("position_teams")))
				.list();
	}

	/** Recorre todos los candidatos en orden (para el ZIP) sin cargarlos todos en memoria. */
	public void forEachCandidate(String id, java.util.function.BiConsumer<Integer, StoredLeague> consumer) {
		jdbc.sql("""
				SELECT c.candidate_no, c.competition_season_id, c.pattern, c.position_teams FROM draw_candidate_league c
				  JOIN draw_batch_league l ON l.batch_id = c.batch_id AND l.competition_season_id = c.competition_season_id
				 WHERE c.batch_id = :id ORDER BY c.candidate_no, l.league_order""")
				.param("id", id)
				.query(rs -> {
					consumer.accept(rs.getInt("candidate_no"),
							stored(rs.getLong("competition_season_id"), rs.getString("pattern"), rs.getString("position_teams")));
				});
	}

	public Map<String, String> teamNames(long cs) {
		Map<String, String> names = new java.util.HashMap<>();
		jdbc.sql("""
				SELECT st.team_code, COALESCE(st.display_name, t.name) AS name FROM competition_season_team st
				  LEFT JOIN team t ON t.code = st.team_code WHERE st.competition_season_id = :cs""")
				.param("cs", cs).query(rs -> {
					names.put(rs.getString("team_code"), rs.getString("name"));
				});
		return names;
	}

	/**
	 * Inicia la competición con el candidato elegido: sustituye los emparejamientos (fixtures) de
	 * cada liga del candidato por los del sorteo (ida y vuelta, sin fecha ni hora).
	 */
	@Transactional(isolation = Isolation.SERIALIZABLE)
	public void choose(String id, int no) {
		if ("RUNNING".equals(require(id).status())) {
			throw new SetupExceptions.Conflict("Wait until the draw batch finishes before choosing a candidate");
		}
		List<StoredLeague> leagues = candidate(id, no).stream()
				.sorted(java.util.Comparator.comparingLong(StoredLeague::competitionSeasonId)).toList();
		if (leagues.isEmpty()) {
			throw new SetupExceptions.NotFound("Candidate " + no + " not found in draw batch " + id);
		}
		for (StoredLeague league : leagues) {
			jdbc.sql("UPDATE competition_season SET row_version = row_version + 1, updated_at = CURRENT_TIMESTAMP WHERE id = :cs")
					.param("cs", league.competitionSeasonId()).update();
		}
		for (StoredLeague league : leagues) {
			long cs = league.competitionSeasonId();
			int active = jdbc.sql("SELECT COUNT(*) FROM planning_job WHERE competition_season_id = :cs AND status IN ('QUEUED', 'RUNNING')")
					.param("cs", cs).query(Integer.class).single();
			int versions = jdbc.sql("SELECT COUNT(*) FROM generation_version WHERE competition_season_id = :cs")
					.param("cs", cs).query(Integer.class).single();
			int scheduled = jdbc.sql("""
					SELECT COUNT(*) FROM fixture WHERE competition_season_id = :cs
					AND (locked = :locked OR play_date IS NOT NULL OR play_hour IS NOT NULL)""")
					.param("cs", cs).param("locked", true).query(Integer.class).single();
			if (active > 0 || versions > 0 || scheduled > 0 || anyRunning(List.of(cs))) {
				throw new SetupExceptions.Conflict("P1 only initializes a competition before P2: active jobs, versions or scheduled fixtures prevent replacing the draw");
			}
		}
		for (StoredLeague league : leagues) {
			jdbc.sql("DELETE FROM fixture WHERE competition_season_id = :cs").param("cs", league.competitionSeasonId()).update();
			List<String[]> matches = DrawEngine.fixtures(league.pattern(), league.positionTeams());
			for (int i = 0; i < matches.size(); i++) {
				String[] m = matches.get(i);
				jdbc.sql("""
						INSERT INTO fixture (competition_season_id, round_no, list_order, home, away, locked)
						VALUES (:cs, :r, :o, :h, :a, :l)""")
						.param("cs", league.competitionSeasonId()).param("r", Integer.parseInt(m[0])).param("o", i).param("h", m[1])
						.param("a", m[2]).param("l", false).update();
			}
		}
		jdbc.sql("UPDATE draw_batch SET status = 'CHOSEN', chosen_no = :no, chosen_at = CURRENT_TIMESTAMP WHERE id = :id")
				.param("no", no).param("id", id).update();
	}

	public int deleteDiscarded(String id, int chosenNo) {
		int n = jdbc.sql("DELETE FROM draw_candidate_league WHERE batch_id = :id AND candidate_no <> :no")
				.param("id", id).param("no", chosenNo).update();
		jdbc.sql("UPDATE draw_batch SET message = :m WHERE id = :id")
				.param("m", "Discarded candidates deleted; only candidate " + chosenNo + " is kept").param("id", id).update();
		return n;
	}

	public void delete(String id) {
		jdbc.sql("DELETE FROM draw_candidate_league WHERE batch_id = :id").param("id", id).update();
		jdbc.sql("DELETE FROM draw_batch_league WHERE batch_id = :id").param("id", id).update();
		jdbc.sql("DELETE FROM draw_batch WHERE id = :id").param("id", id).update();
	}

	private static StoredLeague stored(long cs, String pattern, String teams) {
		return new StoredLeague(cs, pattern, new ArrayList<>(Arrays.asList(teams.split(","))));
	}

	private static Instant instant(Timestamp ts) {
		return ts == null ? null : ts.toInstant();
	}

	private static String truncate(String s) {
		return s == null || s.length() <= 2000 ? s : s.substring(0, 2000);
	}
}
