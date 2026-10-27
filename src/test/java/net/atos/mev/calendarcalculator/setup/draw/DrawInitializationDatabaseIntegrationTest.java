package net.atos.mev.calendarcalculator.setup.draw;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import net.atos.mev.calendarcalculator.generatepositions.TwoColumnsGenPosAlg;
import net.atos.mev.calendarcalculator.setup.*;

@SpringBootTest
@Transactional
class DrawInitializationDatabaseIntegrationTest {
    @Autowired DemoSeeder demo;
    @Autowired DatabaseEngineConfiguration configuration;
    @Autowired SeasonSetupRepository setups;
    @Autowired DrawBatchRepository draws;
    @Autowired JdbcClient jdbc;
    private long cs;
    private String batch;

    @BeforeEach
    void candidate() {
        demo.seed(2030, 4);
        cs = setups.competitionSeasonId("DEV_MINI", "2030-31");
        var env = configuration.environment("DB:DEV_MINI@2030-31", null);
        String pattern = TwoColumnsGenPosAlg.generatePatterns(env, TwoColumnsGenPosAlg.defaultForceRepetitions(4), 1, 5000, () -> false).getFirst();
        batch = UUID.randomUUID().toString();
        draws.create(batch, cs, 1, "{}", List.of(new DrawDtos.DrawLeague("DEV_MINI", "DMO", cs)));
        draws.addCandidate(batch, new DrawEngine.Candidate(1, List.of(new DrawEngine.LeagueResult(cs, pattern,
                List.of("DM01", "DM02", "DM03", "DM04")))), 1);
        draws.finish(batch, "DONE", 1, null);
    }

    @Test
    void choiceInitializesAllReturnFixturesBeforePlanning() {
        draws.choose(batch, 1);
        assertEquals(12, count("SELECT COUNT(*) FROM fixture WHERE competition_season_id = :cs"));
        assertEquals("CHOSEN", draws.require(batch).status());
        assertEquals(0, count("SELECT COUNT(*) FROM fixture WHERE competition_season_id = :cs AND play_date IS NOT NULL"));
    }

    @Test
    void activePlanningPreventsChoiceWithoutDeletingFixtures() {
        jdbc.sql("INSERT INTO planning_job (id, competition_season_id, status, last_round_to_assign) VALUES (:id, :cs, 'RUNNING', 1)")
                .param("id", UUID.randomUUID()).param("cs", cs).update();
        assertThrows(SetupExceptions.Conflict.class, () -> draws.choose(batch, 1));
        assertEquals(0, count("SELECT COUNT(*) FROM fixture WHERE competition_season_id = :cs"));
    }

    @Test
    void datedOrConfirmedFixturePreventsChangingDraw() {
        draws.choose(batch, 1);
        jdbc.sql("UPDATE fixture SET locked = :locked WHERE competition_season_id = :cs")
                .param("locked", true).param("cs", cs).update();
        assertThrows(SetupExceptions.Conflict.class, () -> draws.choose(batch, 1));
        assertEquals(12, count("SELECT COUNT(*) FROM fixture WHERE competition_season_id = :cs"));
    }

    @Test
    void existingPlanningVersionsPreventChangingDraw() {
        jdbc.sql("""
                INSERT INTO generation_version (competition_season_id, version_no, status, last_round_to_assign, source)
                VALUES (:cs, 1, 'DRAFT', 1, 'ENGINE')""").param("cs", cs).update();
        assertThrows(SetupExceptions.Conflict.class, () -> draws.choose(batch, 1));
    }

    private int count(String sql) { return jdbc.sql(sql).param("cs", cs).query(Integer.class).single(); }
}
