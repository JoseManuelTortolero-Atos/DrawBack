package net.atos.mev.calendarcalculator.setup;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Beans Java puros del configurador (mapper y validador no dependen de Spring para poder probarlos sin contexto). */
@Configuration
public class SetupConfig {

    @Bean
    SetupToEngineMapper setupToEngineMapper() {
        return new SetupToEngineMapper();
    }

    @Bean
    SetupValidator setupValidator() {
        return new SetupValidator(s -> catalog.ruleTypes(catalog.federationCodeForCompetition(s.competitionId())));
    }
    private final SetupCatalogRepository catalog;

    public SetupConfig(SetupCatalogRepository catalog) {
        this.catalog = catalog;
    }
}
