package net.atos.mev.calendarcalculator.setup;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import net.atos.mev.calendarcalculator.compat.DatabaseBatchSupport;
import net.atos.mev.calendarcalculator.schedules.PlanningParameters;
import net.atos.mev.calendarcalculator.schedules.ReferenceProperties;

class DatabaseOnlyBusinessConfigurationTest {
    @Test
    void productionResourcesContainNoBusinessProperties() throws Exception {
        try (var files = Files.walk(Path.of("src/main/resources"))) {
            assertEquals(0, files.filter(p -> p.toString().endsWith(".properties")).count());
        }
    }

    @Test
    void productionLoadersCannotOpenPropertiesFiles() throws Exception {
        for (String file : new String[] {"Environment.java", "ClassEnvLoader.java", "assignpositions/AssignPositions.java"}) {
            String source = Files.readString(Path.of("src/main/java/net/atos/mev/calendarcalculator", file));
            assertFalse(source.contains("getResourceAsStream("), file);
            assertFalse(source.contains("getSystemResource("), file);
            assertFalse(source.matches("(?s).*\\b(?:prop|properties|loadedProps)\\.load\\s*\\(.*"), file);
        }
        assertThrows(IllegalArgumentException.class, () -> DatabaseBatchSupport.selector(new String[0]));
        assertEquals("DB:MY_LEAGUE@2030-31", DatabaseBatchSupport.selector(new String[] {"DB:MY_LEAGUE@2030-31"}));
    }

    @Test
    void memoryAdapterSupportsAnUnconfiguredRamadanAndEmptyRestCatalog() {
        var snapshot = ReferenceProperties.loadFromConfigFile("schCNPFF1/MoroccoCNPFF1.properties");
        snapshot.remove("ScheduleMoroccoAlg.ramadan.start");
        snapshot.remove("ScheduleMoroccoAlg.ramadan.end");
        snapshot.setProperty("ScheduleMoroccoAlg.competitions", "");
        var parameters = PlanningParameters.fromProperties(snapshot, "F1");
        assertNull(parameters.ramadanStart());
        assertNull(parameters.ramadanEnd());
        assertTrue(parameters.restDays().isEmpty());
    }

    @Test
    void memoryAdapterRejectsAnIncompleteRamadanInsteadOfIgnoringIt() {
        var snapshot = ReferenceProperties.loadFromConfigFile("schCNPFF1/MoroccoCNPFF1.properties");
        snapshot.remove("ScheduleMoroccoAlg.ramadan.start");
        assertThrows(IllegalArgumentException.class, () -> PlanningParameters.fromProperties(snapshot, "F1"));
    }
}
