package net.atos.mev.calendarcalculator.setup;

import java.util.List;
import java.util.Objects;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/setup/competitions/{competitionId}/legacy-aliases")
public class LegacyAliasController {
    public record Alias(String type, String value) { }
    public record Change(String value, String expectedValue) { }
    private final JdbcClient jdbc;

    public LegacyAliasController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Alias> list(@PathVariable String competitionId) {
        requireCompetition(competitionId);
        return aliases(competitionId);
    }

    @PutMapping("/{type}")
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public List<Alias> change(@PathVariable String competitionId, @PathVariable String type, @RequestBody Change change) {
        requireCompetition(competitionId);
        if (!type.matches("LEGACY_[A-Z0-9_]{1,25}")) {
            throw new IllegalArgumentException("Alias type must be LEGACY_* with at most 32 characters");
        }
        String value = change.value() == null ? null : change.value().strip();
        if (value != null && (value.isBlank() || value.length() > 128 || value.contains("@")
                || value.regionMatches(true, 0, "DB:", 0, 3) || value.codePoints().anyMatch(Character::isISOControl))) {
            throw new IllegalArgumentException("An opaque alias must have 1-128 characters, without @, DB: or control characters");
        }
        String previous = jdbc.sql("SELECT alias_value FROM competition_alias WHERE competition_id = :id AND alias_type = :type")
                .param("id", competitionId).param("type", type).query(String.class).optional().orElse(null);
        if (!Objects.equals(previous, change.expectedValue())) {
            throw new SetupExceptions.Conflict("The alias changed. Reload before saving; no data was changed");
        }
        if (value == null) {
            if (previous == null) throw new IllegalArgumentException("An existing alias and its expected value are required for deletion");
            jdbc.sql("DELETE FROM competition_alias WHERE competition_id = :id AND alias_type = :type")
                    .param("id", competitionId).param("type", type).update();
        } else {
            var collisions = jdbc.sql("""
                    SELECT DISTINCT c.id FROM competition c LEFT JOIN competition_alias a ON a.competition_id = c.id
                    WHERE c.id <> :id AND (UPPER(c.id) = UPPER(:value) OR UPPER(c.code) = UPPER(:value)
                        OR UPPER(a.alias_value) = UPPER(:value))""")
                    .param("id", competitionId).param("value", value).query(String.class).list();
            if (!collisions.isEmpty()) throw new SetupExceptions.Conflict("This alias already identifies another competition");
            if (previous == null) {
                jdbc.sql("INSERT INTO competition_alias (competition_id, alias_type, alias_value) VALUES (:id, :type, :value)")
                        .param("id", competitionId).param("type", type).param("value", value).update();
            } else {
                jdbc.sql("UPDATE competition_alias SET alias_value = :value WHERE competition_id = :id AND alias_type = :type")
                        .param("id", competitionId).param("type", type).param("value", value).update();
            }
        }
        return aliases(competitionId);
    }

    private List<Alias> aliases(String competitionId) {
        return jdbc.sql("""
                SELECT alias_type, alias_value FROM competition_alias
                WHERE competition_id = :id AND LEFT(alias_type, 7) = 'LEGACY_' ORDER BY alias_type""")
                .param("id", competitionId).query((rs, row) -> new Alias(rs.getString(1), rs.getString(2))).list();
    }

    private void requireCompetition(String id) {
        if (jdbc.sql("SELECT id FROM competition WHERE id = :id").param("id", id).query(String.class).optional().isEmpty()) {
            throw new SetupExceptions.NotFound("Competition not found: " + id);
        }
    }
}
