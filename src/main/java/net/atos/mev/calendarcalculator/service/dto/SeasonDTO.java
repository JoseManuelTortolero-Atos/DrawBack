package net.atos.mev.calendarcalculator.service.dto;

public record SeasonDTO(
    String id,
    String label,
    boolean enabled,
    boolean current
) {}
