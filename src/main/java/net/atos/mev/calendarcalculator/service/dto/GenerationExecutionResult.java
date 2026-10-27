package net.atos.mev.calendarcalculator.service.dto;

public record GenerationExecutionResult(
    String generationId,
    byte[] generatedExcel
) {
}
