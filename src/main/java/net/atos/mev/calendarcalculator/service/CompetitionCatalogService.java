package net.atos.mev.calendarcalculator.service;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import net.atos.mev.calendarcalculator.config.CalendarCompetitionsProperties;
import net.atos.mev.calendarcalculator.service.dto.CompetitionDTO;

@Service
public class CompetitionCatalogService {
    private final JdbcClient jdbc;

    public CompetitionCatalogService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<CompetitionDTO> listCompetitions() {
        return jdbc.sql("SELECT id, name FROM competition ORDER BY sort_order, id")
                .query((rs, n) -> new CompetitionDTO(rs.getString("id"), rs.getString("name"),
                        "DB:" + rs.getString("id"), true)).list();
    }

    public Optional<CompetitionDTO> getCompetitionById(String selector) {
        String id = selectorId(selector);
        var matches = jdbc.sql("""
                SELECT DISTINCT c.id, c.name FROM competition c
                LEFT JOIN competition_alias a ON a.competition_id = c.id
                WHERE LOWER(c.id) = LOWER(:id) OR LOWER(c.code) = LOWER(:id) OR LOWER(a.alias_value) = LOWER(:id)
                """)
                .param("id", id).query((rs, n) -> new CompetitionDTO(rs.getString("id"), rs.getString("name"),
                        "DB:" + rs.getString("id"), true)).list();
        if (matches.size() > 1) {
            throw new IllegalArgumentException("Ambiguous competition selector: " + selector);
        }
        return matches.stream().findFirst();
    }

    public CalendarCompetitionsProperties.CompetitionConfig configuration(String selector) {
        String id = getCompetitionById(selector).orElseThrow(
                () -> new IllegalArgumentException("Unknown competition: " + selector)).id();
        return jdbc.sql("SELECT name, storage_dir FROM competition WHERE id = :id").param("id", id)
                .query((rs, n) -> {
                    var config = new CalendarCompetitionsProperties.CompetitionConfig();
                    config.setName(rs.getString("name"));
                    config.setFile("DB:" + id);
                    config.setStorageDir(rs.getString("storage_dir"));
                    return config;
                }).single();
    }

    private String selectorId(String selector) {
        if (selector == null || selector.isBlank()) {
            throw new IllegalArgumentException("Competition selector is required");
        }
        String id = selector.trim();
        if (id.regionMatches(true, 0, "DB:", 0, 3)) {
            id = id.substring(3);
        }
        int at = id.lastIndexOf('@');
        return at < 0 ? id : id.substring(0, at);
    }
}
