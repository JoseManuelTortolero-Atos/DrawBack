package net.atos.mev.calendarcalculator.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;

import net.atos.mev.calendarcalculator.service.dto.GenerationLogsDTO;

@ConditionalOnProperty(name = "calendar.legacy-files.enabled", havingValue = "true")
@Service
public class GenerationLogStoreService {

    private static final int MAX_SESSIONS = 100;
    private static final int MAX_LINES_PER_SESSION = 20000;

    private final ConcurrentMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final Deque<String> sessionOrder = new ConcurrentLinkedDeque<>();

    public String startSession(String competitionId, String season, String excelFileName) {
        String generationId = UUID.randomUUID().toString();
        Session session = new Session(generationId, Instant.now());
        session.append("Generation started: competition=" + competitionId + ", season=" + season + ", excel=" + excelFileName);
        sessions.put(generationId, session);
        sessionOrder.addLast(generationId);
        trimOldSessions();
        return generationId;
    }

    public void append(String generationId, String line) {
        if (line == null) {
            return;
        }
        Session session = sessions.get(generationId);
        if (session == null) {
            return;
        }
        String normalized = line.strip();
        if (normalized.isEmpty()) {
            return;
        }
        session.append(normalized);
    }

    public void markSuccess(String generationId) {
        Session session = sessions.get(generationId);
        if (session == null) {
            return;
        }
        session.finish("SUCCESS", null);
    }

    public void markFailure(String generationId, String errorMessage) {
        Session session = sessions.get(generationId);
        if (session == null) {
            return;
        }
        session.append("ERROR: " + (errorMessage == null ? "Unknown generation error" : errorMessage));
        session.finish("FAILED", errorMessage);
    }

    public Optional<GenerationLogsDTO> getLogs(String generationId) {
        Session session = sessions.get(generationId);
        if (session == null) {
            return Optional.empty();
        }
        return Optional.of(session.toDto());
    }

    private void trimOldSessions() {
        while (sessionOrder.size() > MAX_SESSIONS) {
            String oldest = sessionOrder.pollFirst();
            if (oldest == null) {
                break;
            }
            sessions.remove(oldest);
        }
    }

    private static final class Session {
        private final String generationId;
        private final Instant startedAt;
        private final List<String> lines = new ArrayList<>();
        private Instant finishedAt;
        private String status = "RUNNING";
        private String errorMessage;

        private Session(String generationId, Instant startedAt) {
            this.generationId = generationId;
            this.startedAt = startedAt;
        }

        private synchronized void append(String line) {
            if (lines.size() >= MAX_LINES_PER_SESSION) {
                if (lines.size() == MAX_LINES_PER_SESSION) {
                    lines.add("... log truncated ...");
                }
                return;
            }
            lines.add(line);
        }

        private synchronized void finish(String status, String errorMessage) {
            this.status = status;
            this.errorMessage = errorMessage;
            this.finishedAt = Instant.now();
        }

        private synchronized GenerationLogsDTO toDto() {
            return new GenerationLogsDTO(
                generationId,
                status,
                startedAt,
                finishedAt,
                errorMessage,
                Collections.unmodifiableList(new ArrayList<>(lines))
            );
        }
    }
}
