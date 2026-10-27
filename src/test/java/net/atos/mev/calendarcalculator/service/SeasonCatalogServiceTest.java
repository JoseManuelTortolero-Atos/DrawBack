package net.atos.mev.calendarcalculator.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import net.atos.mev.calendarcalculator.setup.SeasonSetupRepository;
import net.atos.mev.calendarcalculator.setup.SetupDtos;
import net.atos.mev.calendarcalculator.service.dto.SeasonDTO;

class SeasonCatalogServiceTest {
    @Test
    void listsOnlyPersistedSeasonsWithoutInferringHistoricalSeasonsFromFiles() {
        var repository = mock(SeasonSetupRepository.class);
        when(repository.listCompetitions()).thenReturn(List.of(
                new SetupDtos.CompetitionSeasonRef(1, "A", "A", "A", "2026-27", "a", "", null, "FRMF"),
                new SetupDtos.CompetitionSeasonRef(2, "B", "B", "B", "2026-27", "b", "", null, "FRMF"),
                new SetupDtos.CompetitionSeasonRef(3, "A", "A", "A", "2025-26", "a", "", null, "FRMF")));
        assertThat(new SeasonCatalogService(repository).listSeasons()).extracting(SeasonDTO::id)
                .containsExactly("2026-27", "2025-26");
    }

    @Test
    void returnsAnEmptyCatalogWhenNoCompetitionSeasonExists() {
        var repository = mock(SeasonSetupRepository.class);
        when(repository.listCompetitions()).thenReturn(List.of());
        assertThat(new SeasonCatalogService(repository).listSeasons()).isEmpty();
    }
}
