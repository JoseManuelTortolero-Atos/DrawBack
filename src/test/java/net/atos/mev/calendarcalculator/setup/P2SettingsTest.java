package net.atos.mev.calendarcalculator.setup;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class P2SettingsTest {
    private final SeedFixture seed = SeedFixture.load();

    private SetupDtos.SeasonSetup withEngine(SetupDtos.EngineSettings engine) {
        var s = seed.setup("CNPFF_D1");
        return new SetupDtos.SeasonSetup(s.competitionId(), s.code(), s.season(), s.general(), s.timeslots(), s.ramadan(),
                s.noLightSlot(), s.teams(), s.rounds(), s.fixtures(), s.external(), s.blackouts(), s.restDays(),
                s.notHomeSameDay(), s.derbies(), s.rules(), s.dependsOn(), s.includeDependency(), engine, s.rowVersion(), s.updatedAt());
    }

    private SetupDtos.SeasonSetup withHomePairs(List<List<String>> pairs) {
        var s = seed.setup("CNPFF_D1");
        return new SetupDtos.SeasonSetup(s.competitionId(), s.code(), s.season(), s.general(), s.timeslots(), s.ramadan(),
                s.noLightSlot(), s.teams(), s.rounds(), s.fixtures(), s.external(), s.blackouts(), s.restDays(), pairs,
                s.derbies(), s.rules(), s.dependsOn(), s.includeDependency(), s.engine(), s.rowVersion(), s.updatedAt());
    }

    private SetupDtos.EngineSettings engine(String maj, String order, Map<String, String> colours) {
        var s = seed.setup("CNPFF_D1");
        return new SetupDtos.EngineSettings(1, 1, maj, order,
                List.of(new SetupDtos.AssignGroup(1, String.join(",", s.teams().stream().map(SetupDtos.Team::code).toList()), 3)), colours);
    }

    @Test
    void seedsRemainValidIncludingExternalTeamsInCalculationGroups() {
        for (String id : List.of("BOTOLA_D1", "BOTOLA_D2", "CNPFF_D1", "CNPFF_D2")) {
            assertEquals(List.of(), P2Settings.validate(seed.setup(id)), id);
        }
    }

    @Test
    void zeroMajIsNotPromotedToGroupOne() {
        var setup = withEngine(engine("0", "", Map.of()));
        assertEquals(List.of(), P2Settings.validate(setup));
        assertEquals(List.of("0"), new SetupToEngineMapper().toParameters(setup, 5).groupsCanGoToMAJ());
    }

    @Test
    void rejectsMalformedOrdersGroupsAndColours() {
        assertFalse(P2Settings.validate(withEngine(engine("1", "1,1", Map.of()))).isEmpty());
        assertFalse(P2Settings.validate(withEngine(engine("2", "", Map.of()))).isEmpty());
        assertFalse(P2Settings.validate(withEngine(engine("1", "", Map.of("CL", "NO_COLOUR")))).isEmpty());
        var malformed = new SetupDtos.EngineSettings(1, 1, "1", "",
                List.of(new SetupDtos.AssignGroup(2, "ASFAR,", 0)), Map.of());
        assertFalse(P2Settings.validate(withEngine(malformed)).isEmpty());
    }

    @Test
    void rejectsDuplicateHomePairsRegardlessOfTeamOrder() {
        var issues = P2Settings.validate(withHomePairs(List.of(List.of("TEAM_A", "TEAM_B"), List.of("TEAM_B", "TEAM_A"))));
        assertTrue(issues.stream().anyMatch(issue -> issue.msg().contains("duplicate team pairs")));
    }

    @Test
    void customOrderValidatesDatesForTheRoundsActuallyPlanned() {
        String order = java.util.stream.IntStream.iterate(22, r -> r - 1).limit(22)
                .mapToObj(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        var setup = withEngine(engine("1", order, Map.of()));
        var issues = new SetupValidator().validate(setup, 5, true);
        assertTrue(issues.stream().anyMatch(i -> i.msg().equals("Round 22 has no date")));
    }

    @Test
    void coloursReachTheActualP2Xlsx() throws Exception {
        var setup = withEngine(engine("1", "", Map.of("F1", "YELLOW")));
        assertEquals(List.of(), P2Settings.validate(setup));
        var bytes = new LegacyExcelBridge(new SetupToEngineMapper()).export(setup, List.of(), null);
        try (var book = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            boolean found = false;
            for (var sheet : book) for (var row : sheet) for (var cell : row) {
                found |= cell.getCellStyle().getFillForegroundColor() == IndexedColors.YELLOW.getIndex();
            }
            assertTrue(found, "The configured competition colour must be present in the exported workbook");
        }
    }
}
