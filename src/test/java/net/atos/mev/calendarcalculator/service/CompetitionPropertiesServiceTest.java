package net.atos.mev.calendarcalculator.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import net.atos.mev.calendarcalculator.setup.DatabaseEngineConfiguration;
import net.atos.mev.calendarcalculator.setup.SetupDtos.SeasonSetup;
import net.atos.mev.calendarcalculator.service.dto.CompetitionRuntimeOverridesDTO;

class CompetitionPropertiesServiceTest {
    private final DatabaseEngineConfiguration database = mock(DatabaseEngineConfiguration.class);
    private final CompetitionPropertiesService service = new CompetitionPropertiesService(database);

    @Test
    void exposesOnlyAnInMemoryDatabaseSnapshotWithExplicitSeason() {
        var setup = mock(SeasonSetup.class);
        when(setup.competitionId()).thenReturn("BOTOLA_D1");
        when(setup.season()).thenReturn("2026-27");
        when(database.setup("BD1", "2026-27")).thenReturn(setup);
        var overrides = new CompetitionRuntimeOverridesDTO("uploaded.xlsx", "results/run", 8);
        Properties source = new Properties();
        source.setProperty("start", "24/09/2026");
        source.setProperty("ScheduleMoroccoAlg.lastRoundToAssign", "8");
        when(database.properties("BD1", "2026-27", overrides)).thenReturn(source);

        Properties resolved = service.loadFinalProperties("BD1", "2026-27", overrides);

        assertThat(resolved.getProperty("database.competitionId")).isEqualTo("BOTOLA_D1");
        assertThat(resolved.getProperty("database.season")).isEqualTo("2026-27");
        assertThat(resolved.getProperty("start")).isEqualTo("24/09/2026");
        assertThat(source).doesNotContainKey("database.competitionId");
        verify(database).properties("BD1", "2026-27", overrides);
    }

    @Test
    void doesNotChooseASeasonWhenDatabaseReportsAmbiguity() {
        when(database.setup("BOTOLA_D1", null)).thenThrow(new IllegalArgumentException("Specify season"));
        assertThatThrownBy(() -> service.loadFinalProperties("BOTOLA_D1"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("season");
        verify(database, never()).properties(any(), any(), any());
    }

    @Test
    void forwardsEncodedDatabaseSelectorsWithoutReadingConfigurationFiles() {
        when(database.setup("old.properties", null)).thenThrow(new IllegalArgumentException("Unknown selector"));
        assertThatThrownBy(() -> service.loadFinalProperties("old.properties"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unknown selector");
    }
}
