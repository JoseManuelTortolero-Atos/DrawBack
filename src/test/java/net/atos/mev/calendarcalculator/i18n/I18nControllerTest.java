package net.atos.mev.calendarcalculator.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class I18nControllerTest {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new I18nController()).build();
    private final JsonMapper json = JsonMapper.builder().build();

    private Map<String, String> dictionary(String language) throws Exception {
        var response = mvc.perform(get("/api/i18n/" + language))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Language", language))
                .andReturn().getResponse();
        return json.readValue(response.getContentAsString(StandardCharsets.UTF_8), new TypeReference<>() { });
    }

    @Test
    void servesSpanishFrenchAndEnglishWithTheSameKeysAndParameters() throws Exception {
        var english = dictionary("en");
        assertTrue(english.size() >= 400);
        for (String language : Set.of("es", "fr", "en")) {
            var translated = dictionary(language);
            assertEquals(english.keySet(), translated.keySet(), language + " must cover every UI key");
            translated.forEach((key, value) -> {
                assertFalse(value == null || value.isBlank(), language + ": " + key);
                assertEquals(parameters(english.get(key)), parameters(value), language + ": " + key);
            });
        }
        assertEquals("Guardar", dictionary("es").get("Save"));
        assertEquals("Enregistrer", dictionary("fr").get("Save"));
        assertEquals("Save", english.get("Save"));
    }

    @Test
    void doesNotServeUnsupportedResources() throws Exception {
        mvc.perform(get("/api/i18n/de")).andExpect(status().isNotFound());
        mvc.perform(get("/api/i18n/application.yml")).andExpect(status().isNotFound());
    }

    private static Set<String> parameters(String text) {
        var matcher = Pattern.compile("\\{\\{(\\w+)\\}\\}").matcher(text);
        var names = new HashSet<String>();
        while (matcher.find()) names.add(matcher.group(1));
        return names;
    }
}
