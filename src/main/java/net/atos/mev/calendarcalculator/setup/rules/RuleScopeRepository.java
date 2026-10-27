package net.atos.mev.calendarcalculator.setup.rules;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import net.atos.mev.calendarcalculator.setup.SetupExceptions;

/** SQL portable (PostgreSQL y SQL Server): sin RETURNING, ids UUID y CURRENT_TIMESTAMP. */
@Repository
public class RuleScopeRepository {

	private static final String SELECT = """
			SELECT a.id, a.scope_type, a.federation_code, a.competition_id, a.season_code, a.rule_type, c.phase_code,
			       a.mandatory, a.allow_exception, a.priority, a.enabled, a.exception_of, a.team_a, a.team_b, a.team, a.teams,
			       a.rounds, a.min_rounds, a.int_param, a.date_from, a.date_to, a.fixed_date, a.note, a.row_version,
			       a.created_at, a.updated_at
			  FROM rule_scope_assignment a
			  LEFT JOIN rule_type_catalog c ON c.federation_code = a.federation_code AND c.rule_type = a.rule_type
			""";

	private final JdbcClient jdbc;

	public RuleScopeRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	public List<RuleScopeDtos.Assignment> list(String federationCode, String competitionId, String season, String scopeType) {
		StringBuilder sql = new StringBuilder(SELECT).append(" WHERE a.federation_code = :f");
		if (competitionId != null && !competitionId.isBlank()) {
			sql.append(" AND (a.competition_id = :c OR a.scope_type = 'FEDERATION_DEFAULT')");
		}
		if (season != null && !season.isBlank()) {
			sql.append(" AND (a.season_code = :s OR a.scope_type = 'FEDERATION_DEFAULT')");
		}
		if (scopeType != null && !scopeType.isBlank()) {
			sql.append(" AND a.scope_type = :t");
		}
		sql.append(" ORDER BY a.priority DESC, a.created_at, a.id");
		JdbcClient.StatementSpec spec = jdbc.sql(sql.toString()).param("f", federationCode);
		if (competitionId != null && !competitionId.isBlank()) {
			spec = spec.param("c", competitionId);
		}
		if (season != null && !season.isBlank()) {
			spec = spec.param("s", season);
		}
		if (scopeType != null && !scopeType.isBlank()) {
			spec = spec.param("t", scopeType);
		}
		return spec.query(this::map).list();
	}

	/** Asignaciones que pueden afectar a una liga-temporada (incluye las del grupo de coordinación de su principal). */
	public List<RuleScopeDtos.Assignment> candidatesFor(String federationCode, String competitionId, String parentCompetitionId,
			String season) {
		return jdbc.sql(SELECT + """
				 WHERE a.federation_code = :f
				   AND (a.scope_type = 'FEDERATION_DEFAULT'
				        OR (a.season_code = :s AND (a.competition_id = :c OR a.competition_id = :p)))
				 ORDER BY a.priority DESC, a.created_at, a.id""")
				.param("f", federationCode).param("s", season).param("c", competitionId)
				.param("p", parentCompetitionId == null ? competitionId : parentCompetitionId)
				.query(this::map).list();
	}

	public Optional<RuleScopeDtos.Assignment> find(String id) {
		return jdbc.sql(SELECT + " WHERE a.id = :id").param("id", id).query(this::map).optional();
	}

	public RuleScopeDtos.Assignment require(String id) {
		return find(id).orElseThrow(() -> new SetupExceptions.NotFound("Rule assignment " + id + " not found"));
	}

	public String insert(RuleScopeDtos.Assignment a) {
		String id = UUID.randomUUID().toString();
		jdbc.sql("""
				INSERT INTO rule_scope_assignment (id, scope_type, federation_code, competition_id, season_code, rule_type, mandatory,
				       allow_exception, priority, enabled, exception_of, team_a, team_b, team, teams, rounds, min_rounds, int_param,
				       date_from, date_to, fixed_date, note, row_version, created_at)
				VALUES (:id, :scope, :f, :c, :s, :type, :mandatory, :allowException, :priority, :enabled, :exceptionOf, :a, :b, :team,
				        :teams, :rounds, :minRounds, :intParam, :from, :to, :date, :note, 0, CURRENT_TIMESTAMP)""")
				.param("id", id).params(values(a)).update();
		return id;
	}

	public void update(String id, long rowVersion, RuleScopeDtos.Assignment a) {
		int n = jdbc.sql("""
				UPDATE rule_scope_assignment
				   SET scope_type = :scope, federation_code = :f, competition_id = :c, season_code = :s, rule_type = :type,
				       mandatory = :mandatory, allow_exception = :allowException, priority = :priority, enabled = :enabled,
				       exception_of = :exceptionOf, team_a = :a, team_b = :b, team = :team, teams = :teams, rounds = :rounds,
				       min_rounds = :minRounds, int_param = :intParam, date_from = :from, date_to = :to, fixed_date = :date,
				       note = :note, row_version = row_version + 1, updated_at = CURRENT_TIMESTAMP
				 WHERE id = :id AND row_version = :rv""")
				.param("id", id).param("rv", rowVersion).params(values(a)).update();
		if (n == 0) {
			throw new SetupExceptions.Conflict("Rule assignment " + id + " was modified by someone else; reload it");
		}
	}

	@Transactional(isolation = Isolation.SERIALIZABLE)
	public int delete(String id) {
		require(id);
		int exceptions = jdbc.sql("SELECT COUNT(*) FROM rule_scope_assignment WHERE exception_of = :id")
				.param("id", id).query(Integer.class).single();
		if (exceptions > 0) {
			throw new SetupExceptions.BlockedDelete("Cannot delete rule assignment " + id + ": " + exceptions
					+ " exceptions reference it. Remove those exceptions first, or disable the assignment to retain its history. No data was deleted.",
					List.of(exceptions + " rule exceptions"));
		}
		int n = jdbc.sql("DELETE FROM rule_scope_assignment WHERE id = :id").param("id", id).update();
		if (n == 0) {
			throw new SetupExceptions.NotFound("Rule assignment " + id + " not found");
		}
		return n;
	}

	public List<String> teamsOf(String competitionId, String season) {
		return jdbc.sql("""
				SELECT cst.team_code FROM competition_season_team cst
				  JOIN competition_season cs ON cs.id = cst.competition_season_id
				 WHERE cs.competition_id = :c AND cs.season_id = :s ORDER BY cst.list_order""")
				.param("c", competitionId).param("s", season).query(String.class).list();
	}

	/** Liga principal + las que dependen de ella en esa temporada. */
	public List<String> coordinationGroup(String competitionId, String season) {
		return jdbc.sql("""
				SELECT competition_id FROM competition_season
				 WHERE season_id = :s AND (competition_id = :c OR depends_on_competition_id = :c) ORDER BY competition_id""")
				.param("s", season).param("c", competitionId).query(String.class).list();
	}

	public Optional<String> parentCompetition(String competitionId, String season) {
		return jdbc.sql("SELECT depends_on_competition_id FROM competition_season WHERE competition_id = :c AND season_id = :s")
				.param("c", competitionId).param("s", season).query((rs, i) -> Optional.ofNullable(rs.getString(1)))
				.optional().flatMap(o -> o);
	}

	public boolean competitionSeasonExists(String competitionId, String season) {
		return jdbc.sql("SELECT COUNT(*) FROM competition_season WHERE competition_id = :c AND season_id = :s")
				.param("c", competitionId).param("s", season).query(Integer.class).single() > 0;
	}

	public boolean teamExists(String code) {
		return jdbc.sql("SELECT COUNT(*) FROM team WHERE code = :c").param("c", code).query(Integer.class).single() > 0;
	}

	public List<String> federationTeams(String federation, String season) {
		return jdbc.sql("""
				SELECT DISTINCT cst.team_code FROM competition_season_team cst
				JOIN competition_season cs ON cs.id = cst.competition_season_id
				JOIN competition c ON c.id = cs.competition_id
				WHERE c.federation_code = :f AND cs.season_id = :s""")
				.param("f", federation).param("s", season).query(String.class).list();
	}

	public boolean teamAvailableToFederation(String team, String federation) {
		int registrations = jdbc.sql("SELECT COUNT(*) FROM competition_season_team WHERE team_code = :t")
				.param("t", team).query(Integer.class).single();
		return registrations == 0 || jdbc.sql("""
				SELECT COUNT(*) FROM competition_season_team cst
				JOIN competition_season cs ON cs.id = cst.competition_season_id
				JOIN competition c ON c.id = cs.competition_id
				WHERE cst.team_code = :t AND c.federation_code = :f""")
				.param("t", team).param("f", federation).query(Integer.class).single() > 0;
	}

	private static java.util.Map<String, Object> values(RuleScopeDtos.Assignment a) {
		java.util.Map<String, Object> m = new java.util.HashMap<>();
		m.put("scope", a.scopeType());
		m.put("f", a.federationCode());
		m.put("c", a.competitionId());
		m.put("s", a.season());
		m.put("type", a.ruleType());
		m.put("mandatory", a.mandatory());
		m.put("allowException", a.allowException());
		m.put("priority", a.priority());
		m.put("enabled", a.enabled());
		m.put("exceptionOf", a.exceptionOf());
		m.put("a", a.a());
		m.put("b", a.b());
		m.put("team", a.team());
		m.put("teams", a.teams());
		m.put("rounds", a.rounds());
		m.put("minRounds", a.minRounds());
		m.put("intParam", a.intParam());
		m.put("from", a.from());
		m.put("to", a.to());
		m.put("date", a.date());
		m.put("note", a.note());
		return m;
	}

	private RuleScopeDtos.Assignment map(ResultSet rs, int i) throws SQLException {
		String phase = rs.getString("phase_code");
		return new RuleScopeDtos.Assignment(rs.getString("id"), rs.getString("scope_type"), rs.getString("federation_code"),
				rs.getString("competition_id"), rs.getString("season_code"), rs.getString("rule_type"),
				phase == null ? null : phase.trim(), rs.getBoolean("mandatory"), rs.getBoolean("allow_exception"),
				rs.getInt("priority"), rs.getBoolean("enabled"), rs.getString("exception_of"), rs.getString("team_a"),
				rs.getString("team_b"), rs.getString("team"), rs.getString("teams"), rs.getString("rounds"),
				(Integer) rs.getObject("min_rounds"), (Integer) rs.getObject("int_param"), date(rs, "date_from"),
				date(rs, "date_to"), date(rs, "fixed_date"), rs.getString("note"), rs.getLong("row_version"),
				ts(rs.getTimestamp("created_at")), ts(rs.getTimestamp("updated_at")));
	}

	private static LocalDate date(ResultSet rs, String col) throws SQLException {
		java.sql.Date d = rs.getDate(col);
		return d == null ? null : d.toLocalDate();
	}

	private static Instant ts(java.sql.Timestamp t) {
		return t == null ? null : t.toInstant();
	}
}
