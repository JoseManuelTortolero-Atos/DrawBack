package net.atos.mev.calendarcalculator.service.dto;

public record CompetitionDTO(
    String id,
    String name,
    String properties,
    boolean enabled
) {}
