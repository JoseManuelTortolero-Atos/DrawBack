package net.atos.mev.calendarcalculator.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import net.atos.mev.calendarcalculator.config.CalendarCompetitionsProperties;
import net.atos.mev.calendarcalculator.service.dto.GeneratedCalendarFileDTO;

@Service
public class GeneratedCalendarStorageService {

    private static final String COMPETITION_ID_REGEX = "^[A-Za-z0-9_-]+$";
    private static final String SEASON_REGEX = "^[A-Za-z0-9][A-Za-z0-9._-]*$";
    private static final Pattern VERSION_PATTERN = Pattern.compile(".*-v(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern ZIP_FILE_VERSION_PATTERN = Pattern.compile(".*-v(\\d+)\\.(xlsx|zip)$", Pattern.CASE_INSENSITIVE);
    private static final String RESULTS_PREFIX = "results/";

    private final Path resultsBaseDirectory;
    private final CompetitionCatalogService catalog;

    public GeneratedCalendarStorageService(
        @Value("${calendar.results.base-dir:./results}") String resultsBaseDirectory,
        CompetitionCatalogService catalog
    ) {
        this.resultsBaseDirectory = Path.of(resultsBaseDirectory).toAbsolutePath().normalize();
        this.catalog = catalog;
    }

    public GeneratedCalendarFileDTO createGenerationZip(
        String competitionId,
        String season,
        String generationResultsFolder,
        Integer roundsToAssign
    ) {
        Path seasonDirectory = resolveSeasonDirectory(competitionId, season);
        Path generationDirectory = resolveGenerationDirectory(generationResultsFolder);
        if (!generationDirectory.startsWith(seasonDirectory)) {
            throw new IllegalArgumentException(
                "Generation results folder does not belong to selected competition/season: " + generationResultsFolder
            );
        }
        if (!Files.exists(generationDirectory) || !Files.isDirectory(generationDirectory)) {
            throw new IllegalStateException("Generation results folder not found: " + generationDirectory);
        }

        String archiveBaseName = buildArchiveBaseName(competitionId, season, roundsToAssign);
        Path zipTarget = generationDirectory.resolve(archiveBaseName + ".zip").normalize();
        if (!zipTarget.startsWith(generationDirectory)) {
            throw new IllegalArgumentException("Invalid zip path for generation folder");
        }

        createZipArchive(generationDirectory, zipTarget, archiveBaseName + "/");
        return toGeneratedCalendarFileDTO(zipTarget, seasonDirectory);
    }

    public List<GeneratedCalendarFileDTO> listGeneratedFullCalendars(String competitionId, String season) {
        Path seasonDirectory = resolveSeasonDirectory(competitionId, season);
        if (!Files.exists(seasonDirectory)) {
            return List.of();
        }

        String storageDirectory = resolveStorageDirectoryName(competitionId);
        try (Stream<Path> files = Files.walk(seasonDirectory, 6)) {
            List<GeneratedCalendarFileDTO> collected = files
                .filter(Files::isRegularFile)
                .filter(this::isGeneratedResultFile)
                .filter(file -> belongsToCompetition(file, seasonDirectory, storageDirectory))
                .map(file -> toGeneratedCalendarFileDTO(file, seasonDirectory))
                .toList();

            return deduplicatePerVersionAndType(collected, storageDirectory).stream()
                .sorted(Comparator.comparing(GeneratedCalendarFileDTO::lastModified, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(GeneratedCalendarFileDTO::fileName))
                .toList();
        } catch (IOException exception) {
            throw new IllegalStateException(
                "Cannot list generated files for competitionId " + competitionId + " and season " + season,
                exception
            );
        }
    }

    public Optional<byte[]> readGeneratedFullCalendar(String competitionId, String season, String downloadId) {
        Path seasonDirectory = resolveSeasonDirectory(competitionId, season);
        String storageDirectory = resolveStorageDirectoryName(competitionId);
        Path file = decodeDownloadPath(downloadId, seasonDirectory);

        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        if (!isGeneratedResultFile(file)) {
            return Optional.empty();
        }
        if (!belongsToCompetition(file, seasonDirectory, storageDirectory)) {
            return Optional.empty();
        }

        try {
            return Optional.of(Files.readAllBytes(file));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read generated file: " + file.getFileName(), exception);
        }
    }

    private Path resolveSeasonDirectory(String competitionId, String season) {
        if (competitionId == null || !competitionId.matches(COMPETITION_ID_REGEX)) {
            throw new IllegalArgumentException("Invalid competitionId: " + competitionId);
        }
        if (season == null || !season.matches(SEASON_REGEX)) {
            throw new IllegalArgumentException("Invalid season: " + season);
        }

        Path resolved = resultsBaseDirectory.resolve(season).normalize();
        if (!resolved.startsWith(resultsBaseDirectory)) {
            throw new IllegalArgumentException("Invalid season path");
        }
        return resolved;
    }

    private Path resolveGenerationDirectory(String generationResultsFolder) {
        if (!StringUtils.hasText(generationResultsFolder)) {
            throw new IllegalArgumentException("Generation results folder is required");
        }

        String normalizedInput = generationResultsFolder.replace('\\', '/');
        Path resolved;
        if (Path.of(normalizedInput).isAbsolute()) {
            resolved = Path.of(normalizedInput).normalize();
        } else if (normalizedInput.startsWith(RESULTS_PREFIX)) {
            resolved = resultsBaseDirectory.resolve(normalizedInput.substring(RESULTS_PREFIX.length())).normalize();
        } else {
            resolved = Path.of(".").toAbsolutePath().normalize().resolve(normalizedInput).normalize();
        }

        if (!resolved.startsWith(resultsBaseDirectory)) {
            throw new IllegalArgumentException("Invalid generation results folder: " + generationResultsFolder);
        }
        return resolved;
    }

    private boolean isGeneratedResultFile(Path file) {
        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return (fileName.startsWith("fullcalendar") && fileName.endsWith(".xlsx")) || fileName.endsWith(".zip");
    }

    private List<GeneratedCalendarFileDTO> deduplicatePerVersionAndType(
        List<GeneratedCalendarFileDTO> files,
        String storageDirectory
    ) {
        Map<String, GeneratedCalendarFileDTO> selected = new HashMap<>();
        for (GeneratedCalendarFileDTO candidate : files) {
            String key = candidate.version() + "|" + detectResultType(candidate.fileName());
            GeneratedCalendarFileDTO current = selected.get(key);
            if (current == null || shouldPreferCandidate(candidate, current, storageDirectory)) {
                selected.put(key, candidate);
            }
        }
        return selected.values().stream().toList();
    }

    private String detectResultType(String fileName) {
        return fileName != null && fileName.toLowerCase(Locale.ROOT).endsWith(".zip") ? "zip" : "xlsx";
    }

    private boolean shouldPreferCandidate(
        GeneratedCalendarFileDTO candidate,
        GeneratedCalendarFileDTO current,
        String storageDirectory
    ) {
        boolean candidateVersioned = isVersionedStorageFolder(candidate.folder(), storageDirectory);
        boolean currentVersioned = isVersionedStorageFolder(current.folder(), storageDirectory);
        if (candidateVersioned != currentVersioned) {
            return candidateVersioned;
        }

        Instant candidateLastModified = candidate.lastModified();
        Instant currentLastModified = current.lastModified();
        if (candidateLastModified != null && currentLastModified != null) {
            int compare = candidateLastModified.compareTo(currentLastModified);
            if (compare != 0) {
                return compare > 0;
            }
        } else if (candidateLastModified != null) {
            return true;
        } else if (currentLastModified != null) {
            return false;
        }

        return candidate.fileName().compareToIgnoreCase(current.fileName()) < 0;
    }

    private boolean isVersionedStorageFolder(String folder, String storageDirectory) {
        if (!StringUtils.hasText(folder) || !StringUtils.hasText(storageDirectory)) {
            return false;
        }
        String normalizedFolder = folder.replace('\\', '/').toLowerCase(Locale.ROOT);
        String normalizedStorage = storageDirectory.toLowerCase(Locale.ROOT);
        String regex = "(^|/)" + Pattern.quote(normalizedStorage) + "-v\\d+($|/)";
        return normalizedFolder.matches(regex);
    }

    private boolean belongsToCompetition(Path file, Path seasonDirectory, String storageDirectory) {
        Path relative = seasonDirectory.relativize(file);
        for (Path segment : relative) {
            String segmentName = segment.toString();
            if (segmentName.equals(storageDirectory) || segmentName.startsWith(storageDirectory + "-")) {
                return true;
            }
        }
        return false;
    }

    private GeneratedCalendarFileDTO toGeneratedCalendarFileDTO(Path file, Path seasonDirectory) {
        Path relative = seasonDirectory.relativize(file);
        String normalizedRelative = relative.toString().replace('\\', '/');
        String downloadId = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(normalizedRelative.getBytes(StandardCharsets.UTF_8));

        String folder = relative.getParent() == null
            ? ""
            : relative.getParent().toString().replace('\\', '/');

        return new GeneratedCalendarFileDTO(
            file.getFileName().toString(),
            extractVersion(relative),
            folder,
            readFileSize(file),
            readLastModified(file),
            downloadId
        );
    }

    private long readFileSize(Path file) {
        try {
            return Files.size(file);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read size for generated calendar: " + file, exception);
        }
    }

    private Instant readLastModified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toInstant();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read last modified for generated calendar: " + file, exception);
        }
    }

    private String extractVersion(Path relativePath) {
        for (int i = relativePath.getNameCount() - 2; i >= 0; i--) {
            String segment = relativePath.getName(i).toString();
            Matcher matcher = VERSION_PATTERN.matcher(segment);
            if (matcher.matches()) {
                return "v" + matcher.group(1);
            }
        }

        String fileName = relativePath.getFileName().toString();
        Matcher fileMatcher = ZIP_FILE_VERSION_PATTERN.matcher(fileName);
        if (fileMatcher.matches()) {
            return "v" + fileMatcher.group(1);
        }

        return "base";
    }

    private Path decodeDownloadPath(String downloadId, Path seasonDirectory) {
        if (!StringUtils.hasText(downloadId)) {
            throw new IllegalArgumentException("Invalid downloadId");
        }

        String decoded;
        try {
            decoded = new String(Base64.getUrlDecoder().decode(downloadId), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid downloadId", exception);
        }

        Path relative = Path.of(decoded).normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new IllegalArgumentException("Invalid download path");
        }

        Path resolved = seasonDirectory.resolve(relative).normalize();
        if (!resolved.startsWith(seasonDirectory)) {
            throw new IllegalArgumentException("Invalid download path");
        }
        return resolved;
    }

    private String resolveStorageDirectoryName(String competitionId) {
        var competitionConfig = findCompetitionConfig(competitionId);
        if (competitionConfig == null) {
            throw new IllegalArgumentException("Unknown competitionId: " + competitionId);
        }

        if (StringUtils.hasText(competitionConfig.getStorageDir())) {
            return sanitizeStorageDirectory(competitionConfig.getStorageDir());
        }

        throw new IllegalStateException(
            "Missing storageDir for competitionId " + competitionId
                + ". Configure storage_dir in the database"
        );
    }

    private CalendarCompetitionsProperties.CompetitionConfig findCompetitionConfig(String competitionId) {
        return catalog.configuration(competitionId);
    }

    private String sanitizeStorageDirectory(String rawDirectory) {
        String normalized = rawDirectory.replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        if (!StringUtils.hasText(normalized) || normalized.contains("..")) {
            throw new IllegalArgumentException("Invalid storage directory: " + rawDirectory);
        }

        return normalized;
    }

    private void createZipArchive(Path generationDirectory, Path zipTarget, String zipRootFolder) {
        List<Path> filesToZip;
        try (Stream<Path> files = Files.walk(generationDirectory, 6)) {
            filesToZip = files
                .filter(Files::isRegularFile)
                .filter(file -> shouldIncludeInArchive(file, zipTarget))
                .sorted()
                .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot scan generation results folder for archive: " + generationDirectory, exception);
        }

        if (filesToZip.isEmpty()) {
            throw new IllegalStateException("No eligible files to create generated results zip in: " + generationDirectory);
        }

        try (ZipOutputStream zipOutputStream = new ZipOutputStream(
            Files.newOutputStream(zipTarget, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
        )) {
            for (Path file : filesToZip) {
                Path relative = generationDirectory.relativize(file);
                String entryName = zipRootFolder + relative.toString().replace('\\', '/');
                ZipEntry entry = new ZipEntry(entryName);
                zipOutputStream.putNextEntry(entry);
                Files.copy(file, zipOutputStream);
                zipOutputStream.closeEntry();
            }
            zipOutputStream.flush();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot create generated results zip: " + zipTarget, exception);
        }

        // Lightweight integrity check so a partial zip is not exposed for download.
        try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(zipTarget))) {
            if (zipInputStream.getNextEntry() == null) {
                throw new IllegalStateException("Generated zip is empty: " + zipTarget);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot validate generated results zip: " + zipTarget, exception);
        }
    }

    private boolean shouldIncludeInArchive(Path file, Path zipTarget) {
        if (file.equals(zipTarget)) {
            return false;
        }

        String fileName = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileName.endsWith(".zip")) {
            return false;
        }

        // Exclude only intermediate out*.csv/out*.cvs files.
        return !(fileName.startsWith("out") && (fileName.endsWith(".csv") || fileName.endsWith(".cvs")));
    }

    private String buildArchiveBaseName(String competitionId, String season, Integer roundsToAssign) {
        String label = resolveArchiveCompetitionLabel(competitionId);
        String roundsText = roundsToAssign == null ? "all" : String.valueOf(roundsToAssign);
        String raw = label + " " + season + " rounds to " + roundsText;
        return sanitizeArchiveName(raw);
    }

    private String resolveArchiveCompetitionLabel(String competitionId) {
        String id = competitionId == null ? "" : competitionId.toUpperCase(Locale.ROOT);
        if (id.startsWith("CNPFF_D1")) {
            return "F1 WOMEN";
        }
        if (id.startsWith("CNPFF_D2N")) {
            return "F2N WOMEN";
        }
        if (id.startsWith("CNPFF_D2S")) {
            return "F2S WOMEN";
        }
        if (id.startsWith("CNPFF_D2")) {
            return "F2 WOMEN";
        }
        if (id.startsWith("BOTOLA_D1")) {
            return "D1 MEN";
        }
        if (id.startsWith("BOTOLA_D2")) {
            return "D2 MEN";
        }

        var config = findCompetitionConfig(competitionId);
        if (config != null && StringUtils.hasText(config.getName())) {
            return config.getName().toUpperCase(Locale.ROOT);
        }

        return StringUtils.hasText(competitionId) ? competitionId.toUpperCase(Locale.ROOT) : "GENERATED";
    }

    private String sanitizeArchiveName(String rawName) {
        String sanitized = rawName.replaceAll("[\\\\/:*?\"<>|]+", " ").replaceAll("\\s+", " ").trim();
        return StringUtils.hasText(sanitized) ? sanitized : "generated-results";
    }
}
