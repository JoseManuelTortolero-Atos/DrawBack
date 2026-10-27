package net.atos.mev.calendarcalculator.service.dto;

import java.time.Instant;
import java.util.List;

public record GenerationLogsDTO(
    String generationId,
    String status,
    Instant startedAt,
    Instant finishedAt,
    String errorMessage,
    List<String> lines
) {
}
