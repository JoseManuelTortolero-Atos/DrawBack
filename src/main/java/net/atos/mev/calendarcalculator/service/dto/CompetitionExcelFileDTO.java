package net.atos.mev.calendarcalculator.service.dto;

import java.time.Instant;

public record CompetitionExcelFileDTO(
    String fileName,
    long size,
    Instant lastModified
) {}
