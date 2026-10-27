package net.atos.mev.calendarcalculator.i18n;

import java.util.Set;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/i18n")
public class I18nController {
    private static final Set<String> LANGUAGES = Set.of("es", "fr", "en");

    @GetMapping(value = "/{language}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Resource> dictionary(@PathVariable String language) {
        if (!LANGUAGES.contains(language)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unsupported language");
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .cacheControl(CacheControl.noCache())
                .header("Content-Language", language)
                .body(new ClassPathResource("i18n/" + language + ".json"));
    }
}
