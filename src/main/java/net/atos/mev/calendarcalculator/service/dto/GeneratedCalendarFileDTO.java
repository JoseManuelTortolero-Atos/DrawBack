package net.atos.mev.calendarcalculator.service.dto;

import java.time.Instant;

public record GeneratedCalendarFileDTO(
    String fileName,
    String version,
    String folder,
    long size,
    Instant lastModified,
    String downloadId
) {
}
