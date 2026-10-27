package net.atos.mev.calendarcalculator.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
public class ConfigController {
    private static final Logger log = LoggerFactory.getLogger(ConfigController.class);

    @GetMapping("/options")
    public Map<String, Object> getOptions() {
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> configOptions = new ArrayList<>();
        
        // Opción 1: Vista por defecto
        Map<String, Object> option1 = new HashMap<>();
        option1.put("id", "defaultView");
        option1.put("name", "Vista predeterminada");
        option1.put("value", "month");
        option1.put("type", "select");
        option1.put("options", List.of("day", "week", "month", "year"));
        configOptions.add(option1);
                // Opción 2: Primer día de la semana
                // Opción 2: Primer día de la semana

        // Opción 2: Primer día de la semana
        Map<String, Object> option2 = new HashMap<>();
        option2.put("id", "firstDayOfWeek");
        option2.put("name", "Primer día de la semana");
        option2.put("value", 1);
        option2.put("type", "select");
        option2.put("options", List.of(
            Map.of("value", 0, "label", "Domingo"),
            Map.of("value", 1, "label", "Lunes")
        ));
        configOptions.add(option2);
        
        // Opción 3: Idioma
        Map<String, Object> option3 = new HashMap<>();
        option3.put("id", "locale");
        option3.put("name", "Idioma");
        option3.put("value", "es-ES");
        option3.put("type", "select");
        option3.put("options", List.of("es-ES", "en-US", "fr-FR"));
        configOptions.add(option3);
        
        // Opción 4: Formato de fecha
        Map<String, Object> option4 = new HashMap<>();
        option4.put("id", "dateFormat");
        option4.put("name", "Formato de fecha");
        option4.put("value", "dd/MM/yyyy");
        option4.put("type", "select");
        option4.put("options", List.of("dd/MM/yyyy", "MM/dd/yyyy", "yyyy-MM-dd"));
        configOptions.add(option4);
        
        response.put("configOptions", configOptions);
        log.info("Response de /api/config/options: {}", response);
        return response;
    }
}
