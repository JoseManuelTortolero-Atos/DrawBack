package net.atos.mev.calendarcalculator.setup;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class LegacyPropertiesCoverageTest {
    private static final Pattern KEY = Pattern.compile("^([A-Za-z][A-Za-z0-9_.-]*)\\s*[=:]");
    private static final Pattern COVERED = Pattern.compile(
            "(name|code|nTeams|nRounds|nChecks|team\\.[A-Za-z0-9]+|check\\.\\d+|nResults|env(?:\\.\\d+){0,2}|"
            + "teams\\.NotAtHomeTogether\\.\\d+|posfolder\\.\\d+|assign\\.file|configFile|tournament|start|end|"
            + "datesFile|resultsFolder|assignFolder|environment|nTournaments|templatePath|"
            + "distributionType|distributionUrl|wrapperVersion|"
            + "ScheduleMoroccoAlg\\.(assignGroups\\.\\d+|roundsForwardLook\\.\\d+|"
            + "restDays\\.[A-Za-z0-9]+|colour\\.[A-Za-z0-9]+|teamsNotHomeSameDay\\.\\d+|"
            + "ramadan\\.(start|end|timeslots)|groupsToRun|groupToOptimizeTimes|groupsCanGoToMAJ|"
            + "teamsToOptimizeTimes|roundCalculationOrder|lastRoundToAssign|timeAssignsForwardLook|"
            + "maxDaysAdvanceMatch|maxDaysPostponeMatch|competitions|maxMatchesPerDay|timeslots|"
            + "teamsWithoutLight|timeslotWithoutLight|startSecondLeg|"
            + "output(?:DatesCSV|DatesExcel|StatisticsCSV|TeamCSV|TeamExcel|TimeStatisticsCSV)))");

    @Test
    void everyActiveAndCommentedPropertyFamilyHasAnExplicitScreenOrTechnicalDisposition() throws Exception {
        var files = new ArrayList<Path>();
        for (String root : List.of("src/main/resources", "src/test/resources", "docs/AntiguaConfiguracionYRestricines", ".mvn")) {
            try (var paths = Files.walk(Path.of(root))) {
                files.addAll(paths.filter(p -> p.toString().endsWith(".properties")).toList());
            }
        }
        assertEquals(34, files.size(), "Update the documented inventory when adding or removing properties");
        var missing = new ArrayList<String>();
        int keys = 0;
        for (Path file : files) {
            for (String line : Files.readAllLines(file, StandardCharsets.ISO_8859_1)) {
                String candidate = line.replaceFirst("^[\\s#!]+", "");
                if (line.stripLeading().matches("[#!].*") && !candidate.contains("=")) continue;
                var matcher = KEY.matcher(candidate);
                if (matcher.find()) {
                    keys++;
                    if (!COVERED.matcher(matcher.group(1)).matches()) missing.add(file + " : " + matcher.group(1));
                }
            }
        }
        assertTrue(keys > 1000, "The audit must include indexed entries and commented examples");
        assertEquals(List.of(), missing, "Document and wire newly discovered configuration families");
    }
}
