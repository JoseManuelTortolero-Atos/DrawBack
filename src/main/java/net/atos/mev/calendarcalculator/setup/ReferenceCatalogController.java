package net.atos.mev.calendarcalculator.setup;

import java.util.Arrays;
import java.util.List;

import org.apache.poi.ss.usermodel.IndexedColors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/setup/reference-catalogs")
public class ReferenceCatalogController {
    public record ExternalCompetition(String code, String name, String colour) { }
    public record ReferenceCatalogs(List<ExternalCompetition> externalCompetitions, List<String> excelColours) { }
    private final JdbcClient jdbc;

    public ReferenceCatalogController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public ReferenceCatalogs get() {
        return new ReferenceCatalogs(jdbc.sql("SELECT code, name, colour FROM external_competition ORDER BY code")
                .query((rs, i) -> new ExternalCompetition(rs.getString("code"), rs.getString("name"), rs.getString("colour"))).list(),
                Arrays.stream(IndexedColors.values()).map(Enum::name).toList());
    }
}
