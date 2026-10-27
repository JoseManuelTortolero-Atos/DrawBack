package net.atos.mev.calendarcalculator.setup;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoSeeder {
    private static final Logger LOG = LoggerFactory.getLogger(DemoSeeder.class);
    private static final String ID = "DEV_MINI";
    private static final String MARKER = "dev-demo-v1";
    private final JdbcClient jdbc;
    private final MasterDataService masters;

    public DemoSeeder(JdbcClient jdbc, MasterDataService masters) {
        this.jdbc = jdbc;
        this.masters = masters;
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void seed(int year, int teamCount) {
        if (year < 2000 || year > 2098 || teamCount < 4 || teamCount > 16 || teamCount % 2 != 0) {
            throw new IllegalArgumentException("Invalid demo season-year or team-count");
        }
        var existing = jdbc.sql("SELECT storage_dir FROM competition WHERE id = :id")
                .param("id", ID).query(String.class).optional();
        if (existing.isPresent()) {
            if (!MARKER.equals(existing.get())) {
                throw new IllegalStateException("DEV_MINI already exists and is not the development example");
            }
            LOG.info("Development example already exists; preserving all user changes (year/count are creation-only)");
            return;
        }
        jdbc.sql("""
                INSERT INTO competition (id, code, name, storage_dir, output_suffix, sort_order, federation_code)
                VALUES (:id, 'DMO', 'Development example', :marker, '', 999, 'DEMO')""")
                .param("id", ID).param("marker", MARKER).update();
        String season = String.format(Locale.ROOT, "%04d-%02d", year, (year + 1) % 100);
        var setup = masters.createSeason(ID, season, new MasterDataController.CreateSeason(
                LocalDate.of(year, 8, 1), LocalDate.of(year + 1, 6, 30),
                LocalDate.of(year, 9, 1), LocalDate.of(year + 1, 1, 1)));
        long cs = jdbc.sql("SELECT id FROM competition_season WHERE competition_id = :id AND season_id = :season")
                .param("id", ID).param("season", setup.season()).query(Long.class).single();
        var teams = new ArrayList<String>();
        for (int i = 1; i <= teamCount; i++) {
            String code = String.format(Locale.ROOT, "DM%02d", i);
            masters.createTeam(code, new MasterDataController.TeamRequest("Example team " + i, null, null, null));
            jdbc.sql("""
                    INSERT INTO competition_season_team (competition_season_id, team_code, list_order, display_name,
                        no_light, optimize_times) VALUES (:cs, :team, :position, :name, :noLight, :optimize)""")
                    .param("cs", cs).param("team", code).param("position", i - 1).param("name", "Example team " + i)
                    .param("noLight", false).param("optimize", true).update();
            teams.add(code);
        }
        jdbc.sql("INSERT INTO timeslot (competition_season_id, list_order, slot) VALUES (:cs, 0, '15:00')")
                .param("cs", cs).update();
        jdbc.sql("""
                INSERT INTO assign_group (competition_season_id, group_no, teams, rounds_forward_look)
                VALUES (:cs, 1, :teams, 1)""").param("cs", cs).param("teams", String.join(",", teams)).update();
        LOG.info("Created development example {} / {} with {} teams, without historical fixtures or results", ID, season, teamCount);
    }
}
