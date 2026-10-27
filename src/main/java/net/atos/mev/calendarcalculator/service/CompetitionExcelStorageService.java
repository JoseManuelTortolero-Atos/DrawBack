package net.atos.mev.calendarcalculator.service;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import net.atos.mev.calendarcalculator.setup.DatabaseEngineConfiguration;
import net.atos.mev.calendarcalculator.service.dto.CompetitionExcelFileDTO;

@Service
public class CompetitionExcelStorageService {
    private static final Pattern CALENDAR_NAME =
            Pattern.compile("^Calendar([DF][12])-v(\\d+)\\.xlsx$", Pattern.CASE_INSENSITIVE);
    private final Path baseDirectory;
    private final CompetitionCatalogService catalog;
    private final DatabaseEngineConfiguration configuration;

    public CompetitionExcelStorageService(@Value("${calendar.storage.base-dir:./data}") String baseDirectory,
            CompetitionCatalogService catalog, DatabaseEngineConfiguration configuration) {
        this.baseDirectory = Path.of(baseDirectory).toAbsolutePath().normalize();
        this.catalog = catalog;
        this.configuration = configuration;
    }

    /** Explicit initialization only; no historical seasons or bundled workbooks are seeded. */
    public void initializeStorageStructure() {
        try {
            Files.createDirectories(baseDirectory);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create Excel storage", e);
        }
    }

    public List<CompetitionExcelFileDTO> listExcelFiles(String competitionId, String season) {
        Path directory = directory(competitionId, season);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (var files = Files.list(directory)) {
            return files.filter(Files::isRegularFile).filter(p -> validName(p.getFileName().toString()))
                    .map(this::metadata).sorted(Comparator.comparing(CompetitionExcelFileDTO::fileName)).toList();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot list Excel files", e);
        }
    }

    public CompetitionExcelFileDTO storeExcelFile(String competitionId, String season, MultipartFile excel, boolean overwrite) {
        if (excel == null || excel.isEmpty()) {
            throw new IllegalArgumentException("Excel file is required");
        }
        String name = excel.getOriginalFilename();
        validateExcelFileNameForCompetition(competitionId, name);
        Path directory = directory(competitionId, season);
        Path target = existing(directory, name);
        Path staging = directory.resolve(name + ".upload");
        try {
            Files.createDirectories(directory);
            if (Files.exists(target) && !overwrite) {
                throw new IllegalArgumentException("Excel file already exists: " + name);
            }
            try (var input = excel.getInputStream()) {
                Files.copy(input, staging, StandardCopyOption.REPLACE_EXISTING);
            }
            try {
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return metadata(target);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot store Excel file", e);
        } finally {
            try {
                Files.deleteIfExists(staging);
            } catch (IOException ignored) {
                // A failed cleanup must not mask the original upload failure.
            }
        }
    }

    public boolean deleteExcelFile(String competitionId, String season, String fileName) {
        requireName(fileName);
        try {
            return Files.deleteIfExists(existing(directory(competitionId, season), fileName));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot delete Excel file", e);
        }
    }

    public Optional<byte[]> readExcelFile(String competitionId, String season, String fileName) {
        requireName(fileName);
        Path file = existing(directory(competitionId, season), fileName);
        try {
            return Files.isRegularFile(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read Excel file", e);
        }
    }

    public String buildAutoVersionName(String baseNameWithoutExtension) {
        String base = baseNameWithoutExtension == null || baseNameWithoutExtension.isBlank()
                ? "calendar" : baseNameWithoutExtension.replaceAll("[^A-Za-z0-9._-]", "_");
        return base + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".xlsx";
    }

    public void validateExcelFileNameForCompetition(String competitionId, String fileName) {
        requireName(fileName);
        var matcher = CALENDAR_NAME.matcher(fileName);
        var competition = catalog.getCompetitionById(competitionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown competition: " + competitionId));
        String description = (competition.id() + " " + competition.name()).toUpperCase(Locale.ROOT);
        String prefix = description.contains("CNPFF") ? "F" : "D";
        String division = description.matches(".*(?:D1|DIVISION\\s*1).*") ? prefix + "1"
                : description.matches(".*(?:D2|DIVISION\\s*2).*") ? prefix + "2" : null;
        if (!matcher.matches() || division != null && !division.equalsIgnoreCase(matcher.group(1))) {
            throw new IllegalArgumentException("Invalid file name for selected competition. Expected Calendar"
                    + (division == null ? "[D1/D2/F1/F2]" : division) + "-v<number>.xlsx");
        }
    }

    public String buildResultsFolder(String competitionId, String season, String excelFileName) {
        validateExcelFileNameForCompetition(competitionId, excelFileName);
        var setup = configuration.setup(competitionId, season);
        var matcher = CALENDAR_NAME.matcher(excelFileName);
        matcher.matches();
        return "results/" + safeSeason(setup.season()) + "/" + storageDirectory(setup.competitionId()) + "-v" + matcher.group(2);
    }

    private Path directory(String competitionId, String season) {
        var setup = configuration.setup(competitionId, season);
        Path path = baseDirectory.resolve(storageDirectory(setup.competitionId())).resolve(safeSeason(setup.season())).normalize();
        if (!path.startsWith(baseDirectory)) {
            throw new IllegalArgumentException("Invalid storage path");
        }
        return path;
    }

    private String storageDirectory(String competitionId) {
        String storage = catalog.configuration(competitionId).getStorageDir();
        if (storage == null || storage.isBlank() || Path.of(storage).isAbsolute()
                || storage.contains("..") || storage.contains("\\")) {
            throw new IllegalArgumentException("Invalid database storage directory");
        }
        return storage;
    }

    private String safeSeason(String season) {
        if (season == null || !season.matches("[A-Za-z0-9][A-Za-z0-9._-]*") || season.contains("..")) {
            throw new IllegalArgumentException("Invalid season: " + season);
        }
        return season;
    }

    private boolean validName(String name) {
        return name != null && !name.startsWith("~$") && !name.contains("..")
                && name.matches("(?i)[A-Za-z0-9._() -]+\\.xlsx");
    }

    private void requireName(String name) {
        if (!validName(name)) {
            throw new IllegalArgumentException("Invalid Excel file name: " + name);
        }
    }

    private Path existing(Path directory, String name) {
        Path exact = directory.resolve(name).normalize();
        if (!exact.startsWith(directory)) {
            throw new IllegalArgumentException("Invalid Excel path");
        }
        if (!Files.isDirectory(directory)) {
            return exact;
        }
        try (var files = Files.list(directory)) {
            var matches = files.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase(name)).toList();
            if (matches.size() > 1) {
                throw new IllegalStateException("Ambiguous Excel file name: " + name);
            }
            return matches.isEmpty() ? exact : matches.get(0);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot resolve Excel file", e);
        }
    }

    private CompetitionExcelFileDTO metadata(Path file) {
        try {
            return new CompetitionExcelFileDTO(file.getFileName().toString(), Files.size(file), Files.getLastModifiedTime(file).toInstant());
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read Excel metadata", e);
        }
    }
}
