package net.atos.mev.calendarcalculator.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import net.atos.mev.calendarcalculator.service.CompetitionCatalogService;
import net.atos.mev.calendarcalculator.service.CompetitionPropertiesService;
import net.atos.mev.calendarcalculator.service.SeasonCatalogService;
import net.atos.mev.calendarcalculator.service.dto.CompetitionDTO;
import net.atos.mev.calendarcalculator.service.dto.CompetitionRuntimeOverridesDTO;

@WebMvcTest({CompetitionController.class, ConfigController.class})
@TestPropertySource(properties = "calendar.legacy-files.enabled=false")
class CompetitionControllerTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private CompetitionCatalogService catalog;
    @MockitoBean private CompetitionPropertiesService properties;
    @MockitoBean private SeasonCatalogService seasons;

    @Test
    void listsDatabaseSelectorsAndKeepsOptionsWhenLegacyFilesAreDisabled() throws Exception {
        when(catalog.listCompetitions()).thenReturn(List.of(new CompetitionDTO("BOTOLA_D1", "Botola", "DB:BOTOLA_D1", true)));
        mvc.perform(get("/api/config/competitions")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].properties").value("DB:BOTOLA_D1"));
        mvc.perform(get("/api/config/options")).andExpect(status().isOk())
                .andExpect(jsonPath("$.configOptions").isArray());
    }

    @Test
    void forwardsExplicitSeasonAndRetainsLegacyResolveFields() throws Exception {
        var result = new Properties();
        result.setProperty("env", "DB:BOTOLA_D1@2026-27");
        result.setProperty("datesFile", "uploaded.xlsx");
        result.setProperty("resultsFolder", "results/run");
        result.setProperty("ScheduleMoroccoAlg.lastRoundToAssign", "6");
        when(properties.loadFinalProperties(eq("BOTOLA_D1"), eq("2026-27"), any(CompetitionRuntimeOverridesDTO.class)))
                .thenReturn(result);
        mvc.perform(post("/api/config/competitions/BOTOLA_D1/resolve").param("season", "2026-27")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"datesFile":"uploaded.xlsx","resultsFolder":"results/run","lastRoundToAssign":6}
                        """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("BOTOLA_D1"))
                .andExpect(jsonPath("$.env").value("DB:BOTOLA_D1@2026-27"))
                .andExpect(jsonPath("$.datesFile").value("uploaded.xlsx"))
                .andExpect(jsonPath("$.['ScheduleMoroccoAlg.lastRoundToAssign']").value("6"));
    }

    @Test
    void rejectsAmbiguousSeasonInsteadOfChoosingAnother() throws Exception {
        when(properties.loadFinalProperties(eq("BOTOLA_D1"), isNull(), isNull()))
                .thenThrow(new IllegalArgumentException("An explicit season is required"));
        mvc.perform(post("/api/config/competitions/BOTOLA_D1/resolve"))
                .andExpect(status().isBadRequest());
    }
}
