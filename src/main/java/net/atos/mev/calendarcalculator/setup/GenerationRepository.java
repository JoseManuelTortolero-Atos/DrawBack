package net.atos.mev.calendarcalculator.setup;

import java.sql.Timestamp;
import java.sql.Types;
import java.sql.SQLException;
import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.HashMap;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;

/** Versiones generadas, sus partidos y sus ficheros (todo en BD; nada en disco). */
@Repository
public class GenerationRepository {

    public record StoredFile(String name, String contentType, byte[] content) {
    }

    private final JdbcClient jdbc;

    private final int snapshotSqlType;

    public GenerationRepository(JdbcClient jdbc, DataSource dataSource) throws SQLException {
        this.jdbc = jdbc;
        try (var connection = dataSource.getConnection()) {
            snapshotSqlType = "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName()) ? Types.OTHER : Types.NVARCHAR;
        }
    }

    public List<SetupDtos.Version> versions(long cs) {
        return jdbc.sql("""
                SELECT id, version_no, status, first_round_to_assign, last_round_to_assign, source, created_at, approved_at, approved_by
                  FROM generation_version WHERE competition_season_id = :cs ORDER BY version_no DESC""")
                .param("cs", cs)
                .query((rs, i) -> new SetupDtos.Version(rs.getLong("id"), rs.getInt("version_no"), rs.getString("status"),
                        rs.getInt("first_round_to_assign"), rs.getInt("last_round_to_assign"), rs.getString("source"),
                        ts(rs.getTimestamp("created_at")),
                        ts(rs.getTimestamp("approved_at")), rs.getString("approved_by"), files(rs.getLong("id"))))
                .list();
    }

    private List<SetupDtos.FileInfo> files(long versionId) {
        return jdbc.sql("SELECT id, name, content_type, size_bytes FROM generation_file WHERE version_id = :v ORDER BY name")
                .param("v", versionId)
                .query((rs, i) -> new SetupDtos.FileInfo(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4))).list();
    }

    public long versionId(long cs, int versionNo) {
        return jdbc.sql("SELECT id FROM generation_version WHERE competition_season_id = :cs AND version_no = :n")
                .param("cs", cs).param("n", versionNo).query(Long.class).optional()
                .orElseThrow(() -> new SetupExceptions.NotFound("Version " + versionNo + " not found"));
    }

    public List<SetupDtos.GeneratedMatch> matches(long versionId) {
        Map<String, String> statuses = new HashMap<>();
        List<Map<String, Object>> versions = jdbc.sql("""
                SELECT cs.id AS competition_season_id, c.code AS competition_code
                  FROM generation_version v
                  JOIN competition_season cs ON cs.id = v.competition_season_id
                  JOIN competition c ON c.id = cs.competition_id
                 WHERE v.id = :v""")
                .param("v", versionId).query().listOfRows();
        if (versions.isEmpty()) {
            throw new SetupExceptions.NotFound("Generated version " + versionId + " not found");
        }
        Map<String, Object> version = versions.get(0);
        long competitionSeasonId = ((Number) version.get("competition_season_id")).longValue();
        jdbc.sql("""
                SELECT round_no, home, away, status FROM season_match_state
                 WHERE competition_season_id = :cs""")
                .param("cs", competitionSeasonId).query().listOfRows().forEach(row ->
                        statuses.put(matchKey(((Number) row.get("round_no")).intValue(), (String) row.get("home"), (String) row.get("away")),
                                (String) row.get("status")));
        String competitionCode = (String) version.get("competition_code");
        return jdbc.sql("""
                SELECT competition_code, round_label, play_date, play_hour, home, away FROM generated_match
                 WHERE version_id = :v ORDER BY list_order""")
                .param("v", versionId)
                .query((rs, i) -> {
                    String competition = rs.getString(1);
                    String round = rs.getString(2);
                    String home = rs.getString(5);
                    String away = rs.getString(6);
                    String status = competitionCode.equals(competition) && numericRound(round) != null
                            ? statuses.getOrDefault(matchKey(numericRound(round), home, away), MatchStateService.PENDING) : null;
                    return new SetupDtos.GeneratedMatch(competition, round, rs.getDate(3).toLocalDate(), rs.getString(4),
                            home, away, status);
                })
                .list();
    }

    private static Integer numericRound(String round) {
        if (round == null || !round.matches("[1-9][0-9]*")) return null;
        try {
            return Integer.valueOf(round);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String matchKey(int round, String home, String away) {
        return round + "\u0000" + home + "\u0000" + away;
    }

    /** Partidos de la versión APROBADA de otra competición en la misma temporada (D2 → D1). */
    public Optional<List<SetupDtos.GeneratedMatch>> approvedMatches(String competitionId, String season) {
        return jdbc.sql("""
                SELECT v.id FROM generation_version v JOIN competition_season cs ON cs.id = v.competition_season_id
                 WHERE cs.competition_id = :c AND cs.season_id = :s AND v.status = 'APPROVED'""")
                .param("c", competitionId).param("s", season).query(Long.class).optional().map(this::matches);
    }

    public Optional<StoredFile> file(long versionId, String name) {
        return jdbc.sql("SELECT name, content_type, content FROM generation_file WHERE version_id = :v AND name = :n")
                .param("v", versionId).param("n", name)
                .query((rs, i) -> new StoredFile(rs.getString(1), rs.getString(2), rs.getBytes(3))).optional();
    }

    public Optional<String> configSnapshot(long versionId) {
        return jdbc.sql("SELECT config_snapshot FROM generation_version WHERE id = :v").param("v", versionId)
                .query((rs, i) -> rs.getString(1) == null ? "" : rs.getString(1)).optional();
    }

    @Transactional
    public SetupDtos.Version createVersion(long cs, int lastRound, UUID jobId, String snapshotJson,
            List<SetupDtos.GeneratedMatch> matches, Map<String, byte[]> files) {
        return createVersion(cs, 1, lastRound, jobId, snapshotJson, matches, files);
    }

    @Transactional
    public SetupDtos.Version createVersion(long cs, int firstRound, int lastRound, UUID jobId, String snapshotJson,
            List<SetupDtos.GeneratedMatch> matches, Map<String, byte[]> files) {
        int next = jdbc.sql("SELECT COALESCE(MAX(version_no), -1) + 1 FROM generation_version WHERE competition_season_id = :cs")
                .param("cs", cs).query(Integer.class).single();
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO generation_version
                    (competition_season_id, version_no, status, first_round_to_assign, last_round_to_assign, source, job_id, config_snapshot)
                VALUES (:cs, :n, 'DRAFT', :first, :last, 'ENGINE', :job, :snap)""")
                .param("cs", cs).param("n", next).param("first", firstRound).param("last", lastRound)
                .param("job", jobId).param("snap", snapshotJson, snapshotSqlType)
                .update(keys, "id");
        long vid = java.util.Objects.requireNonNull(keys.getKey(), "Missing generation version ID").longValue();
        for (int i = 0; i < matches.size(); i++) {
            SetupDtos.GeneratedMatch m = matches.get(i);
            jdbc.sql("""
                    INSERT INTO generated_match (version_id, list_order, competition_code, round_label, play_date, play_hour, home, away)
                    VALUES (:v, :o, :c, :r, :d, :h, :home, :away)""")
                    .param("v", vid).param("o", i).param("c", m.competition()).param("r", m.round()).param("d", m.date())
                    .param("h", m.hour()).param("home", m.home()).param("away", m.away()).update();
        }
        for (Map.Entry<String, byte[]> f : files.entrySet()) {
            jdbc.sql("INSERT INTO generation_file (version_id, name, content_type, size_bytes, content) VALUES (:v, :n, :t, :s, :c)")
                    .param("v", vid).param("n", f.getKey()).param("t", contentType(f.getKey())).param("s", f.getValue().length)
                    .param("c", f.getValue()).update();
        }
        return versions(cs).stream().filter(v -> v.id() == vid).findFirst().orElseThrow();
    }

    /** Aprueba una versión: la aprobada anterior pasa a ARCHIVED (índice único parcial). */
    @Transactional
    public void approve(long cs, int versionNo, String by) {
        long vid = versionId(cs, versionNo);
        jdbc.sql("UPDATE generation_version SET status = 'ARCHIVED' WHERE competition_season_id = :cs AND status = 'APPROVED' AND id <> :v")
                .param("cs", cs).param("v", vid).update();
        jdbc.sql("UPDATE generation_version SET status = 'APPROVED', approved_at = CURRENT_TIMESTAMP, approved_by = :by WHERE id = :v")
                .param("v", vid).param("by", by).update();
    }

    static String contentType(String name) {
        if (name.endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }
        if (name.endsWith(".zip")) {
            return "application/zip";
        }
        return "text/csv";
    }

    private static Instant ts(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
