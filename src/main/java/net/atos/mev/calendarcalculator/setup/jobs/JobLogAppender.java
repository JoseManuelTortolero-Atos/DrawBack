package net.atos.mev.calendarcalculator.setup.jobs;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.jdbc.core.simple.JdbcClient;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/**
 * Appender de Logback que guarda en {@code planning_job_log} los mensajes INFO/WARN/ERROR
 * (y DEBUG del motor como INFO resumido) emitidos en el hilo de un job. El job se identifica
 * con la clave MDC {@value #MDC_KEY}. Se registra programáticamente en {@code PlanningJobService}.
 */
public class JobLogAppender extends AppenderBase<ILoggingEvent> {

    public static final String MDC_KEY = "planningJobId";
    /** Máximo de líneas por job (el motor en DEBUG puede emitir millones). */
    static final int MAX_LINES = 5000;

    private final JdbcClient jdbc;
    private final Map<UUID, AtomicInteger> seq = new ConcurrentHashMap<>();

    public JobLogAppender(JdbcClient jdbc) {
        this.jdbc = jdbc;
        setName("planning-job-db");
    }

    @Override
    protected void append(ILoggingEvent event) {
        String job = event.getMDCPropertyMap().get(MDC_KEY);
        if (job == null || !event.getLevel().isGreaterOrEqual(Level.INFO)) {
            return;
        }
        UUID id = UUID.fromString(job);
        int n = seq.computeIfAbsent(id, k -> new AtomicInteger()).incrementAndGet();
        if (n > MAX_LINES) {
            return;
        }
        String level = event.getLevel().isGreaterOrEqual(Level.ERROR) ? "ERROR" : event.getLevel().isGreaterOrEqual(Level.WARN) ? "WARN" : "INFO";
        String msg = n == MAX_LINES ? "Log truncated at " + MAX_LINES + " lines" : event.getFormattedMessage();
        try {
            jdbc.sql("INSERT INTO planning_job_log (job_id, seq, level, message) VALUES (:j, :s, :l, :m)")
                    .param("j", id).param("s", n).param("l", level).param("m", msg).update();
        } catch (RuntimeException e) {
            addError("Cannot store job log line", e);
        }
    }

    public void forget(UUID jobId) {
        seq.remove(jobId);
    }
}
