package net.atos.mev.calendarcalculator.setup;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class P2SettingsDatabaseIntegrationTest {
    private static final String URL = "/api/setup/competitions/CNPFF_D1/seasons/2026-27";
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired SetupToEngineMapper mapper;

    private SetupDtos.SeasonSetup load() throws Exception {
        return json.readValue(mvc.perform(get(URL)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(),
                SetupDtos.SeasonSetup.class);
    }

    private ObjectNode draft(SetupDtos.SeasonSetup s) {
        return (ObjectNode) json.readTree(json.writeValueAsString(s));
    }

    @Test
    void editedGroupsMajAndColoursSurvivePutGetAndReachMapperWithConflictProtection() throws Exception {
        var original = load();
        ObjectNode body = draft(original);
        String teams = String.join(",", original.teams().stream().map(SetupDtos.Team::code).toList());
        var engine = new SetupDtos.EngineSettings(2, original.engine().groupToOptimizeTimes(), "2", "",
                List.of(new SetupDtos.AssignGroup(1, teams, 3), new SetupDtos.AssignGroup(2, teams, 4)),
                Map.of("F1", "YELLOW", "CL", "RED"));
        body.set("engine", json.readTree(json.writeValueAsString(engine)));
        body.set("timeslots", json.readTree("[\"15:00\",\"15:00\",\"15:00\",\"15:00\"]"));
        List<List<String>> pairs = List.of(List.of(original.teams().getFirst().code(), original.teams().getLast().code()));
        body.set("notHomeSameDay", json.readTree(json.writeValueAsString(pairs)));
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isOk());
        var saved = load();
        assertEquals(2, saved.engine().groupsToRun());
        assertEquals(4, saved.engine().assignGroups().get(1).roundsForwardLook());
        assertEquals(original.general().roundsForwardLook(), saved.engine().assignGroups().getFirst().roundsForwardLook());
        assertEquals("2", saved.engine().groupsCanGoToMAJ());
        assertEquals(Map.of("F1", "YELLOW", "CL", "RED"), saved.engine().exportColours());
        assertEquals(List.of("15:00", "15:00", "15:00", "15:00"), saved.timeslots());
        assertEquals(pairs, saved.notHomeSameDay());
        assertEquals(pairs, mapper.toParameters(saved, 5).teamsNotHomeSameDay());
        assertEquals(List.of("2"), mapper.toParameters(saved, 5).groupsCanGoToMAJ());
        assertEquals(original.rowVersion() + 1, saved.rowVersion());
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isConflict());
        assertEquals(saved.engine(), load().engine());
    }

    @Test
    void omittedOptionalColoursArePreservedAndInvalidSettingsNeverWrite() throws Exception {
        ObjectNode body = draft(load());
        ((ObjectNode) body.get("engine")).set("exportColours", json.readTree("{\"F1\":\"RED\"}"));
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isOk());
        body = draft(load());
        ((ObjectNode) body.get("engine")).remove("exportColours");
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isOk());
        assertEquals(Map.of("F1", "RED"), load().engine().exportColours());
        var saved = load();
        body = draft(saved);
        ((ObjectNode) body.get("engine")).put("roundCalculationOrder", "1,1");
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isUnprocessableEntity());
        assertEquals(saved, load());
        body = draft(saved);
        ((ObjectNode) body.get("engine")).set("exportColours", json.readTree("{\"F1\":\"NOT_A_COLOUR\"}"));
        mvc.perform(put(URL).contentType(MediaType.APPLICATION_JSON).content(body.toString())).andExpect(status().isUnprocessableEntity());
        assertEquals(saved, load());
    }
}
