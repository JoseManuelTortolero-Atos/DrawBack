package net.atos.mev.calendarcalculator.setup;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Editable competition master fields; seasons and draw data are managed separately. */
@RestController
@RequestMapping("/api")
public class CompetitionAdminController {
    public record CreateCompetition(String id, String code, String name) {}
    public record UpdateCompetition(String name, String originalName) {}

    private final JdbcClient jdbc;
    private final SetupCatalogRepository catalog;

    public CompetitionAdminController(JdbcClient jdbc, SetupCatalogRepository catalog) {
        this.jdbc = jdbc;
        this.catalog = catalog;
    }

    @PostMapping("/federations/{federation}/competitions")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public SetupDtos.CompetitionSummary create(@PathVariable String federation, @RequestBody CreateCompetition body) {
        if (body == null || body.id() == null || !body.id().matches("[A-Z0-9_]{2,40}")
                || body.code() == null || !body.code().matches("[A-Z0-9_]{1,10}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use uppercase letters, numbers and underscores for the ID and code");
        }
        String name = name(body.name());
        if (jdbc.sql("SELECT code FROM federation WHERE code = :code").param("code", federation).query(String.class).optional().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Federation not found");
        }
        try {
            jdbc.sql("""
                    INSERT INTO competition (id, code, name, storage_dir, output_suffix, sort_order, federation_code)
                    VALUES (:id, :code, :name, :storage, '', 0, :federation)
                    """).param("id", body.id()).param("code", body.code()).param("name", name)
                    .param("storage", body.id()).param("federation", federation).update();
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Competition ID or code already exists");
        }
        return catalog.competition(body.id());
    }

    @PutMapping("/competitions/{id}")
    @Transactional
    public SetupDtos.CompetitionSummary update(@PathVariable String id, @RequestBody UpdateCompetition body) {
        if (body == null || body.originalName() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "originalName is required");
        }
        String name = name(body.name());
        if (jdbc.sql("SELECT id FROM competition WHERE id = :id").param("id", id).query(String.class).optional().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Competition not found");
        }
        int changed = jdbc.sql("UPDATE competition SET name = :name WHERE id = :id AND name = :original")
                .param("name", name).param("id", id).param("original", body.originalName()).update();
        if (changed == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Competition changed. Reload before saving");
        }
        return catalog.competition(id);
    }

    private static String name(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 120) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required (maximum 120 characters)");
        }
        return value.trim();
    }
}
