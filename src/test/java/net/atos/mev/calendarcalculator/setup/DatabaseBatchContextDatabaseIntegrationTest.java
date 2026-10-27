package net.atos.mev.calendarcalculator.setup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List;
import java.util.UUID;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import net.atos.mev.calendarcalculator.compat.DatabaseBatchSupport;
import net.atos.mev.calendarcalculator.setup.draw.DrawBatchRepository;
import net.atos.mev.calendarcalculator.setup.draw.DrawDtos;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DatabaseBatchContextDatabaseIntegrationTest {
    @Autowired JdbcClient jdbc;
    @Autowired SeasonSetupRepository setups;
    @Autowired DrawBatchRepository draws;
    @Autowired DemoSeeder demo;
    @Autowired JsonMapper json;
    @Autowired GenerationRepository generations;
    @TempDir Path output;

    @Test
    void openingCompatibilityContextDoesNotRecoverOtherProcessesJobsOrDropDatabase() {
        long cs = setups.competitionSeasonId("CNPFF_D1", "2026-27");
        UUID planning = UUID.randomUUID();
        String draw = UUID.randomUUID().toString();
        jdbc.sql("INSERT INTO planning_job (id, competition_season_id, status, last_round_to_assign) VALUES (:id, :cs, 'RUNNING', 1)")
                .param("id", planning).param("cs", cs).update();
        try {
            draws.create(draw, cs, 1, "{}", List.of(new DrawDtos.DrawLeague("CNPFF_D1", "F1", cs)));
            assertEquals("F1", DatabaseBatchSupport.withConfiguration(c -> c.environment("MoroccoFemDiv1.properties", "2026-27").code));
            assertEquals("RUNNING", jdbc.sql("SELECT status FROM planning_job WHERE id = :id").param("id", planning).query(String.class).single());
            assertEquals("RUNNING", draws.require(draw).status());
        } finally {
            draws.delete(draw);
            jdbc.sql("DELETE FROM planning_job WHERE id = :id").param("id", planning).update();
        }
    }

    @Test
    void batchP1AndP2UseTheNormalPersistedPipelinesAndExportRealArtifacts() throws Exception {
        demo.seed(2030, 4);
        DatabaseBatchSupport.runDraw(new String[] {"DB:DEV_MINI@2030-31", "2", output.toString()});
        long cs = setups.competitionSeasonId("DEV_MINI", "2030-31");
        var batch = draws.list(cs, "2030-31").getFirst();
        assertEquals(2, batch.generated());
        try (var files = Files.walk(output)) {
            var zip = files.filter(p -> p.toString().endsWith(".zip")).findFirst().orElseThrow();
            try (var content = new java.util.zip.ZipFile(zip.toFile())) {
                assertEquals(2, content.stream().filter(entry -> entry.getName().endsWith(".xlsx")).count());
            }
        }
        draws.choose(batch.id(), 1);
        var setup = setups.load("DEV_MINI", "2030-31");
        ObjectNode body = (ObjectNode) json.readTree(json.writeValueAsString(setup));
        var rounds = body.putArray("rounds");
        for (int n = 1; n <= 6; n++) {
            var round = rounds.addObject().put("n", n).put("leg", n <= 3 ? 1 : 2);
            round.putArray("dates").add((n <= 3 ? LocalDate.of(2030, 9, 1).plusWeeks(n - 1)
                    : LocalDate.of(2031, 1, 1).plusWeeks(n - 4)).toString());
        }
        body.putObject("restDays").put("DMO", 2);
        setups.replace(json.readValue(body.toString(), SetupDtos.SeasonSetup.class));
        DatabaseBatchSupport.runPlanning(new String[] {"DB:DEV_MINI@2030-31", "1", output.toString()});
        var version = generations.versions(cs).getFirst();
        assertEquals("DRAFT", version.status());
        assertEquals(1, version.lastRoundToAssign());
        assertEquals(5, version.files().size());
        try (var files = Files.walk(output)) {
            assertEquals(2, files.filter(p -> p.toString().endsWith(".xlsx")).count());
        }
    }
}
