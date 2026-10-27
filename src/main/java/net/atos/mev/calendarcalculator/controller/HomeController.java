package net.atos.mev.calendarcalculator.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    @GetMapping("/")
    public String home() {
        return "¡Bienvenido a Calendar Calculator! 📅\n\n" +
               "Tu aplicación Spring Boot está funcionando correctamente.\n" +
               "Fecha actual: " + java.time.LocalDateTime.now() + "\n\n" +
               "Endpoints disponibles:\n" +
               "- GET / (esta página)\n" +
               "- GET /health (estado de la aplicación)\n" +
               "- GET /info (información de la aplicación)";
    }

    @GetMapping("/health")
    public String health() {
        return "✅ Aplicación funcionando correctamente - " + java.time.LocalDateTime.now();
    }

    @GetMapping("/info")
    public String info() {
        return "📊 Calendar Calculator v0.0.1-SNAPSHOT\n" +
               "🚀 Spring Boot 3.5.6\n" +
               "☕ Java 21\n" +
               "🏗️ Construido con Maven\n" +
               "📅 " + java.time.LocalDateTime.now();
    }
}