package net.atos.mev.calendarcalculator.service;

import java.util.Properties;
import org.springframework.stereotype.Service;
import net.atos.mev.calendarcalculator.setup.DatabaseEngineConfiguration;
import net.atos.mev.calendarcalculator.service.dto.CompetitionRuntimeOverridesDTO;

/**
 * Compatibility adapter: Properties is an in-memory view of database configuration,
 * never a configuration file or a YAML competition definition.
 */
@Service
public class CompetitionPropertiesService {
    private final DatabaseEngineConfiguration configuration;

    public CompetitionPropertiesService(DatabaseEngineConfiguration configuration) {
        this.configuration = configuration;
    }

    public Properties loadFinalProperties(String selector) {
        return loadFinalProperties(selector, null, CompetitionRuntimeOverridesDTO.empty());
    }

    public Properties loadFinalProperties(String selector, CompetitionRuntimeOverridesDTO overrides) {
        return loadFinalProperties(selector, null, overrides);
    }

    public Properties loadFinalProperties(String selector, String season, CompetitionRuntimeOverridesDTO overrides) {
        var setup = configuration.setup(selector, season);
        Properties properties = new Properties();
        properties.putAll(configuration.properties(selector, setup.season(),
                overrides == null ? CompetitionRuntimeOverridesDTO.empty() : overrides));
        properties.setProperty("database.competitionId", setup.competitionId());
        properties.setProperty("database.season", setup.season());
        return properties;
    }
}
