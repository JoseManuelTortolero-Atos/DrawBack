package net.atos.mev.calendarcalculator.setup;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tools.jackson.databind.json.JsonMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GenerationStatisticsServiceTest {
    private final SeasonSetupRepository setups = mock(SeasonSetupRepository.class);
    private final GenerationRepository generations = mock(GenerationRepository.class);
    private final GenerationStatisticsService service = new GenerationStatisticsService(setups, generations, JsonMapper.builder().build());

    private static SetupDtos.GeneratedMatch match(String competition, String date, String hour, String home, String away) {
        return new SetupDtos.GeneratedMatch(competition, "1", LocalDate.parse(date), hour, home, away);
    }

    private static SetupDtos.Team team(String code) {
        return new SetupDtos.Team(code, code, null, false, false);
    }

    @Test
    void countsBothVenuesAllWeekdaysAndMissingHoursWithoutExternalMatches() {
        var matches = List.of(
                match("D1", "2026-10-12", "18:00", "A", "B"),
                match("D1", "2026-10-16", "18:00", "B", "A"),
                match("D1", "2026-10-17", null, "A", "B"),
                match("D1", "2026-10-18", "", "B", "A"),
                match("CAF", "2026-10-18", "22:00", "A", "External"),
                match("D2", "2026-10-18", "23:00", "A", "B"));
        var stats = GenerationStatisticsService.calculate("BOTOLA_D1", "2026-27", 3, "D1",
                List.of(team("A"), team("B"), team("ZERO")), matches);
        assertEquals(4, stats.matches());
        assertEquals(List.of("18:00", "UNASSIGNED"), stats.slots());
        var a = stats.teams().getFirst();
        assertEquals(new GenerationStatisticsService.Count(2, 2, 4), a.appearances());
        assertEquals(new GenerationStatisticsService.Count(1, 1, 2), a.timeslots().get("18:00"));
        assertEquals(new GenerationStatisticsService.Count(1, 1, 2), a.timeslots().get("UNASSIGNED"));
        assertEquals(new GenerationStatisticsService.Count(1, 1, 2), a.midweek());
        assertEquals(new GenerationStatisticsService.Count(1, 1, 2), a.weekend());
        assertEquals(new GenerationStatisticsService.Count(0, 1, 1), a.weekdays().get("FRIDAY"));
        assertEquals(7, a.weekdays().size());
        assertEquals(0, stats.teams().get(2).appearances().total());
        assertEquals(8, stats.teams().stream().mapToInt(t -> t.appearances().total()).sum());
        stats.teams().forEach(t -> {
            assertEquals(t.appearances().total(), t.weekdays().values().stream().mapToInt(GenerationStatisticsService.Count::total).sum());
            assertEquals(t.appearances().total(), t.timeslots().values().stream().mapToInt(GenerationStatisticsService.Count::total).sum());
        });
    }

    @Test
    void readsTheRequestedSeasonVersionAndItsHistoricalTeamSnapshot() {
        when(setups.competitionSeasonId("LEAGUE", "2025-26")).thenReturn(7L);
        when(generations.versionId(7, 2)).thenReturn(42L);
        when(generations.configSnapshot(42)).thenReturn(Optional.of("""
                {"competitionId":"LEAGUE","season":"2025-26","code":"OLD","includeDependency":false,"rowVersion":0,"teams":[
                 {"code":"A","name":"Historical name","noLight":false,"optimizeTimes":false},
                 {"code":"B","name":"B","noLight":false,"optimizeTimes":false},
                 {"code":"ZERO","name":"Zero","noLight":false,"optimizeTimes":false}]}"""));
        when(generations.matches(42)).thenReturn(List.of(match("OLD", "2026-10-17", "18:00", "A", "B"),
                match("NEW", "2026-10-17", "20:00", "A", "B")));
        var stats = service.statistics("LEAGUE", "2025-26", 2);
        assertEquals(1, stats.matches());
        assertEquals("Historical name", stats.teams().getFirst().name());
        assertEquals("2025-26", stats.season());
        assertEquals(2, stats.versionNo());
        verify(setups, never()).load(anyString(), anyString());
    }

    @Test
    void emptyCalendarKeepsZeroTeamsAndInvalidLeagueRowsAreReported() {
        var empty = GenerationStatisticsService.calculate("L", "S", 0, "D1", List.of(team("A")), List.of());
        assertEquals(0, empty.matches());
        assertTrue(empty.slots().isEmpty());
        assertEquals(0, empty.teams().getFirst().appearances().total());
        assertThrows(IllegalStateException.class, () -> GenerationStatisticsService.calculate("L", "S", 0, "D1", List.of(),
                List.of(match("D1", "2026-10-17", "18:00", "A", null))));
    }

    @Test
    void exportsTheSameCountsIncludingZerosAndQuotesNames() {
        var stats = GenerationStatisticsService.calculate("L", "S", 0, "D1",
                List.of(new SetupDtos.Team("A", "Club, \"A\"", null, false, false), team("B")),
                List.of(match("D1", "2026-10-17", "18:00", "A", "B")));
        String csv = new String(service.csv(stats), StandardCharsets.UTF_8);
        assertTrue(csv.contains("Team,Name,Dimension,Value,Home,Away,Total"));
        assertTrue(csv.contains("\"A\",\"Club, \"\"A\"\"\",TOTAL,\"\",1,0,1"));
        assertTrue(csv.contains("WEEKDAY,\"MONDAY\",0,0,0"));
        assertTrue(csv.contains("\"B\",\"B\",TIMESLOT,\"18:00\",0,1,1"));
    }

    @ParameterizedTest
    @CsvSource({
            "BOTOLA_D1,schBotolaD1-v0,statisticsDaysAtHome.csv,statisticsTime.csv",
            "BOTOLA_D2,schBotolaD2-v0,statisticsDaysAtHomeD2.csv,statisticsTimeD2.csv",
            "CNPFF_D1,schCNPFF1-v0,statisticsDaysAtHomeCNPFF1.csv,statisticsTimeCNPFF1.csv",
            "CNPFF_D2,schCNPFF2-v0,statisticsDaysAtHomeCNPFF2.csv,statisticsTimeCNPFF2.csv"
    })
    void reproducesEveryLegacyStatisticsCellForEachLeague(String cid, String folder, String daysFile, String timesFile) throws Exception {
        var seed = SeedFixture.load();
        var setup = seed.setup(cid);
        var stats = GenerationStatisticsService.calculate(cid, setup.season(), 0, setup.code(), setup.teams(), seed.approvedV0(cid));
        Path root = Path.of("tools/seed/input/results-v0", folder);
        for (String line : Files.readAllLines(root.resolve(daysFile)).subList(2, setup.teams().size() + 2)) {
            String[] fields = line.split(",");
            var team = stats.teams().stream().filter(t -> fields[0].equals(t.code())).findFirst().orElseThrow();
            for (var day : DayOfWeek.values()) {
                assertEquals(Integer.parseInt(fields[day.getValue()]), team.weekdays().get(day.name()).home(), cid + "/" + team.code() + "/" + day);
            }
            assertEquals(Integer.parseInt(fields[8]), team.appearances().home(), cid + "/" + team.code() + "/home");
            assertEquals(Integer.parseInt(fields[9]), team.midweek().home(), cid + "/" + team.code() + "/midweek");
            assertEquals(Integer.parseInt(fields[10]), team.weekend().home(), cid + "/" + team.code() + "/weekend");
        }
        List<String> times = Files.readAllLines(root.resolve(timesFile));
        String[] slots = times.get(1).split(",");
        for (String line : times.subList(2, times.size())) {
            if (line.isBlank()) continue;
            String[] fields = line.split(",");
            var team = stats.teams().stream().filter(t -> fields[0].equals(t.code())).findFirst().orElseThrow();
            for (int i = 1; i < slots.length; i++) {
                assertEquals(Integer.parseInt(fields[i]), team.timeslots().get(slots[i]).total(), cid + "/" + team.code() + "/" + slots[i]);
            }
        }
    }

    @Test
    void importedVersionsWithoutSnapshotUseOnlyTheirOwnStoredMatchTeams() {
        when(setups.competitionSeasonId("BOTOLA_D1", "2026-27")).thenReturn(7L);
        when(generations.versionId(7, 0)).thenReturn(42L);
        when(generations.configSnapshot(42)).thenReturn(Optional.of(""));
        when(setups.load("BOTOLA_D1", "2026-27")).thenReturn(SeedFixture.load().setup("BOTOLA_D1"));
        when(generations.matches(42)).thenReturn(List.of(match("D1", "2026-10-17", "18:00", "OLD_A", "OLD_B"),
                match("CAF", "2026-10-17", "20:00", "OTHER", "TEAM")));
        var stats = service.statistics("BOTOLA_D1", "2026-27", 0);
        assertEquals(List.of("OLD_A", "OLD_B"), stats.teams().stream().map(GenerationStatisticsService.TeamStatistics::code).toList());
        assertEquals(1, stats.matches());
    }

    @Test
    void endpointsExposeJsonAndAnAttachedCsvWithVersionScopedCounts() throws Exception {
        when(setups.competitionSeasonId("L", "S")).thenReturn(7L);
        when(generations.versionId(7, 2)).thenReturn(42L);
        when(generations.configSnapshot(42)).thenReturn(Optional.of("""
                {"code":"D1","teams":[],"includeDependency":false,"rowVersion":0}"""));
        when(generations.matches(42)).thenReturn(List.of(match("D1", "2026-10-17", "18:00", "A", "B")));
        var mvc = MockMvcBuilders.standaloneSetup(new GenerationStatisticsController(service)).build();
        mvc.perform(get("/api/setup/competitions/L/seasons/S/versions/2/statistics"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.matches").value(1))
                .andExpect(jsonPath("$.teams[0].timeslots['18:00'].home").value(1))
                .andExpect(jsonPath("$.teams[1].appearances.away").value(1));
        mvc.perform(get("/api/setup/competitions/L/seasons/S/versions/2/statistics.csv"))
                .andExpect(status().isOk()).andExpect(content().contentType("text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"statistics-v2.csv\""));
    }
}
