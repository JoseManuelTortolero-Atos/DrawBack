package net.atos.mev.calendarcalculator.service;

import java.util.List;
import org.springframework.stereotype.Service;
import net.atos.mev.calendarcalculator.setup.SeasonSetupRepository;
import net.atos.mev.calendarcalculator.service.dto.SeasonDTO;

@Service
public class SeasonCatalogService {
    private final SeasonSetupRepository setups;

    public SeasonCatalogService(SeasonSetupRepository setups) {
        this.setups = setups;
    }

    public List<SeasonDTO> listSeasons() {
        var seasons = setups.listCompetitions().stream().map(ref -> ref.season())
                .distinct().sorted(java.util.Comparator.reverseOrder()).toList();
        var today = java.time.LocalDate.now();
        int startYear = today.getMonthValue() >= 7 ? today.getYear() : today.getYear() - 1;
        String calendarSeason = String.format(java.util.Locale.ROOT, "%d-%02d", startYear, (startYear + 1) % 100);
        String current = seasons.contains(calendarSeason) ? calendarSeason : seasons.isEmpty() ? null : seasons.get(0);
        return seasons.stream().map(season -> new SeasonDTO(season, season, true,
                season.equals(current))).toList();
    }
}
