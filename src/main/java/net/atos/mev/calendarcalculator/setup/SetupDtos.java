package net.atos.mev.calendarcalculator.setup;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Contrato REST de /api/setup (mockup/CONTRACT.md §3 y §5; front: core/setup/setup.models.ts).
 * Records puros: los serializa Jackson (fechas ISO yyyy-MM-dd, horas HH:mm).
 */
public final class SetupDtos {

    private SetupDtos() {
    }

    public record CompetitionSeasonRef(long id, String competitionId, String code, String name, String season,
            String storageDir, String outputSuffix, String dependsOn, String federationId) {
    }

    public record Federation(String id, String code, String name, String countryCode, String timeZone,
            String status, Instant createdAt, Instant updatedAt) {
    }

    public record FederationUpsertRequest(String name, String countryCode, String timeZone) {
    }

    public record FederationStatusRequest(String status) {
    }

    public record CompetitionSummary(String id, String code, String name, String modality, int level,
            String federationCode, String status, int seasons, int participants, int effectiveRules) {
    }

    public record General(LocalDate seasonStart, LocalDate seasonEnd, LocalDate firstRoundDate, LocalDate startSecondLeg,
            int maxMatchesPerDay, int maxDaysAdvanceMatch, int maxDaysPostponeMatch, boolean allowMAJ,
            int roundsForwardLook, int timeAssignsForwardLook, int lastRoundToAssign, String matchdayPattern) {
    }

    public record Ramadan(LocalDate start, LocalDate end, String slot) {
    }

    public record Team(String code, String name, String city, boolean noLight, boolean optimizeTimes) {
    }

    /** n = jornada (null en bloques MAJ), label = MAJ1…, leg = 1|2|null. */
    public record Round(Integer n, String label, Integer leg, List<LocalDate> dates) {
    }

    /** El orden de la lista es significativo (reparto de franjas). */
    public record Fixture(Long id, int round, String home, String away, boolean locked, LocalDate date, String hour) {
    }

    public record ExternalMatch(Long id, String competition, String round, LocalDate date, String hour, String home, String away) {
    }

    public record Blackout(Long id, String label, LocalDate from, LocalDate to) {
    }

    public record Rule(Long id, String type, String a, String b, String team, String teams, String rounds,
            Integer minRounds, Integer intParam, LocalDate from, LocalDate to, LocalDate date, String definition, String note) {
    }

    /** Configuracion avanzada P2 y presentacion XLSX de esta competicion/temporada. */
    public record EngineSettings(int groupsToRun, int groupToOptimizeTimes, String groupsCanGoToMAJ,
            String roundCalculationOrder, List<AssignGroup> assignGroups, Map<String, String> exportColours) {
        public EngineSettings(int groupsToRun, int groupToOptimizeTimes, String groupsCanGoToMAJ,
                String roundCalculationOrder, List<AssignGroup> assignGroups) {
            this(groupsToRun, groupToOptimizeTimes, groupsCanGoToMAJ, roundCalculationOrder, assignGroups, null);
        }
    }

    public record AssignGroup(int groupNo, String teams, int roundsForwardLook) {
    }

    public record SeasonSetup(String competitionId, String code, String season, General general, List<String> timeslots,
            Ramadan ramadan, String noLightSlot, List<Team> teams, List<Round> rounds, List<Fixture> fixtures,
            List<ExternalMatch> external, List<Blackout> blackouts, Map<String, Integer> restDays,
            List<List<String>> notHomeSameDay, List<List<String>> derbies, List<Rule> rules, String dependsOn,
            boolean includeDependency, EngineSettings engine, int rowVersion, Instant updatedAt) {
    }

    public record ValidationIssue(String section, String level, String msg) {
        public static ValidationIssue error(String section, String msg) {
            return new ValidationIssue(section, "error", msg);
        }

        public static ValidationIssue warn(String section, String msg) {
            return new ValidationIssue(section, "warn", msg);
        }
    }

    public record StartPlanningRequest(Integer firstRoundToAssign, Integer lastRoundToAssign) {
        public StartPlanningRequest(Integer lastRoundToAssign) {
            this(null, lastRoundToAssign);
        }
    }

    public record PlanningJob(String id, String competitionId, String season, String status, int firstRoundToAssign, int lastRoundToAssign,
            Instant createdAt, Instant startedAt, Instant finishedAt, String errorMessage, Long versionId, Integer versionNo) {
        public PlanningJob(String id, String competitionId, String season, String status, int lastRoundToAssign,
                Instant createdAt, Instant startedAt, Instant finishedAt, String errorMessage, Long versionId, Integer versionNo) {
            this(id, competitionId, season, status, 1, lastRoundToAssign, createdAt, startedAt, finishedAt, errorMessage, versionId, versionNo);
        }
    }

    public record JobLogLine(int seq, Instant at, String level, String message) {
    }

    public record FileInfo(long id, String name, String contentType, long size) {
    }

    public record Version(long id, int versionNo, String status, int firstRoundToAssign, int lastRoundToAssign, String source, Instant createdAt,
            Instant approvedAt, String approvedBy, List<FileInfo> files) {
        public Version(long id, int versionNo, String status, int lastRoundToAssign, String source, Instant createdAt,
                Instant approvedAt, String approvedBy, List<FileInfo> files) {
            this(id, versionNo, status, 1, lastRoundToAssign, source, createdAt, approvedAt, approvedBy, files);
        }
    }

    public record GeneratedMatch(String competition, String round, LocalDate date, String hour, String home, String away, String status) {
        public GeneratedMatch(String competition, String round, LocalDate date, String hour, String home, String away) {
            this(competition, round, date, hour, home, away, null);
        }
    }

    public record MatchState(int round, String home, String away, String status, LocalDate date, String hour) {
    }

    public record MatchStatusRequest(int round, String home, String away, String status, String reason) {
    }

    public record MatchPlanCancellationRequest(int round, String home, String away, String reason) {
    }

    public record MatchRescheduleRequest(int round, String home, String away, LocalDate date, String hour, String reason, boolean confirm) {
    }

    /**
     * Moves every dated match of {@code round} so that the round starts on {@code date}. Following rounds shift
     * by the same number of days only when both includeFollowing and authorizeFollowing are true.
     * With dryRun (or any conflict) nothing is persisted.
     */
    public record RoundRescheduleRequest(int round, LocalDate date, boolean includeFollowing, boolean authorizeFollowing,
            String reason, boolean confirm, boolean dryRun) {
    }

    public record MatchMove(int round, String home, String away, LocalDate from, LocalDate to, String hour) {
    }

    public record RoundRescheduleResult(boolean applied, boolean dryRun, int shiftDays, List<MatchMove> moves, List<String> conflicts) {
    }

        public record RuleParam(String key, String kind, String label) {
        }

        public record RuleTypeDef(String type, String label, String icon, String scope, String technicalCode,
                        String description, String category, List<String> allowedScopes, String supportStatus, String engine,
                        String engineVersion, boolean enabled, boolean editableDefinition, List<RuleParam> params) {
        }

        public record RuleTypeStatusRequest(boolean enabled) {
        }

    /** CHG-002 (pendiente en v0.4: el endpoint se añade con el Prompt 03). */
    public record FixtureLockRequest(String home, String away, LocalDate date, String hour, boolean locked) {
    }
}
