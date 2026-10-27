package net.atos.mev.calendarcalculator.schedules;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Escritura antigua de resultados (CSV y XLSX) aislada del motor. El motor v0.4
 * ({@link ScheduleMoroccoAlg#plan}) no escribe ficheros; esta clase la usan el flujo legacy
 * ({@link ScheduleMoroccoAlg#execute}) y el servicio de jobs para guardar los ficheros en BD.
 */
public final class LegacyResultFiles {

    private LegacyResultFiles() {
    }

    /** Escribe en disco los mismos ficheros que el legacy (fullCalendar, summaryPerTeam, estadísticas). */
    public static void writeAll(SchCalendar calendar, SchEnvironment schEnv, PlanningParameters p, String folder) {
        SchCalendarExcelIO.matchdaysToCSV(calendar, folder + "/" + p.outputDatesCSV());
        SchCalendarExcelIO.matchdaysToExcel(calendar, folder + "/" + p.outputDatesExcel(), schEnv);
        SchCalendarExcelIO.summaryPerTeamToExcel(calendar, folder + "/" + p.outputTeamExcel(), schEnv);
        SchCalendarExcelIO.statisticsToCSV(calendar, folder + "/" + p.outputStatisticsCSV());
        SchCalendarExcelIO.timeStatisticsToCSV(calendar, folder + "/" + p.outputTimeStatisticsCSV());
    }

    /**
     * Genera los mismos ficheros en un directorio temporal y los devuelve en memoria
     * (nombre → bytes) para guardarlos en {@code generation_file}. El directorio se borra.
     */
    public static Map<String, byte[]> renderAll(SchCalendar calendar, SchEnvironment schEnv, PlanningParameters p) {
        Path tmp = null;
        try {
            tmp = Files.createTempDirectory("calendar-result-");
            writeAll(calendar, schEnv, p, tmp.toString());
            Map<String, byte[]> files = new LinkedHashMap<>();
            for (String name : new String[] { p.outputDatesExcel(), p.outputTeamExcel(), p.outputDatesCSV(),
                    p.outputStatisticsCSV(), p.outputTimeStatisticsCSV() }) {
                Path f = tmp.resolve(name);
                if (Files.exists(f)) {
                    files.put(name, Files.readAllBytes(f));
                }
            }
            return files;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot render result files", e);
        } finally {
            deleteQuietly(tmp);
        }
    }

    /** Solo el calendario completo en XLSX (export). */
    public static byte[] fullCalendarXlsx(SchCalendar calendar, SchEnvironment schEnv) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SchCalendarExcelIO.matchdaysToExcel(calendar, out, schEnv);
        return out.toByteArray();
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(x -> {
                try {
                    Files.deleteIfExists(x);
                } catch (IOException ignored) {
                    // limpieza best-effort del directorio temporal
                }
            });
        } catch (IOException ignored) {
            // limpieza best-effort del directorio temporal
        }
    }
}
