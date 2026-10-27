package net.atos.mev.calendarcalculator.setup;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import net.atos.mev.calendarcalculator.setup.SetupDtos.Fixture;
import net.atos.mev.calendarcalculator.setup.SetupDtos.MatchRescheduleRequest;
import net.atos.mev.calendarcalculator.setup.SetupDtos.MatchPlanCancellationRequest;
import net.atos.mev.calendarcalculator.setup.SetupDtos.MatchState;
import net.atos.mev.calendarcalculator.setup.SetupDtos.MatchStatusRequest;
import net.atos.mev.calendarcalculator.setup.SetupDtos.SeasonSetup;
import net.atos.mev.calendarcalculator.setup.SetupDtos.Version;

@Service
public class MatchStateService {

    public static final String PENDING = "PENDIENTE";
    public static final String PLANNED = "PLANIFICADA";
    public static final String DELIVERED = "ENTREGADA";
    public static final String PLAYED = "JUGADA";
    private final JdbcClient jdbc;
    private final GenerationRepository generations;
    private final RoundDateRepository roundDates;

    public MatchStateService(JdbcClient jdbc, GenerationRepository generations, RoundDateRepository roundDates) {
        this.jdbc = jdbc;
        this.generations = generations;
        this.roundDates = roundDates;
    }

    public List<MatchState> list(long competitionSeasonId) {
        return jdbc.sql("""
                SELECT round_no, home, away, status, play_date, play_hour
                  FROM season_match_state
                 WHERE competition_season_id = :cs
                 ORDER BY round_no, home, away""")
                .param("cs", competitionSeasonId)
                .query((rs, i) -> state(rs))
                .list();
    }

    public SeasonSetup protectFinalizedMatches(SeasonSetup setup) {
        long cs = seasonId(setup.competitionId(), setup.season());
        Map<String, MatchState> protectedMatches = new HashMap<>();
        for (MatchState state : list(cs)) {
            if (DELIVERED.equals(state.status()) || PLAYED.equals(state.status())) {
                protectedMatches.put(key(state.round(), state.home(), state.away()), state);
            }
        }
        if (protectedMatches.isEmpty()) {
            return setup;
        }
        List<Fixture> fixtures = new ArrayList<>(setup.fixtures());
        for (Map.Entry<String, MatchState> entry : protectedMatches.entrySet()) {
            MatchState state = entry.getValue();
            if (state.date() == null) {
                throw new SetupExceptions.Conflict("Protected match has no scheduled date: "
                        + state.round() + " " + state.home() + "-" + state.away());
            }
            int index = -1;
            for (int i = 0; i < fixtures.size(); i++) {
                Fixture fixture = fixtures.get(i);
                if (key(fixture.round(), fixture.home(), fixture.away()).equals(entry.getKey())) {
                    index = i;
                    break;
                }
            }
            if (index < 0) {
                throw new SetupExceptions.Conflict("Setup is missing protected match "
                        + state.round() + " " + state.home() + "-" + state.away());
            }
            Fixture fixture = fixtures.get(index);
            fixtures.set(index, new Fixture(fixture.id(), fixture.round(), fixture.home(), fixture.away(), true,
                    state.date(), state.hour()));
        }
        return new SeasonSetup(setup.competitionId(), setup.code(), setup.season(), setup.general(), setup.timeslots(),
                setup.ramadan(), setup.noLightSlot(), setup.teams(), setup.rounds(), fixtures, setup.external(),
                setup.blackouts(), setup.restDays(), setup.notHomeSameDay(), setup.derbies(), setup.rules(),
                setup.dependsOn(), setup.includeDependency(), setup.engine(), setup.rowVersion(), setup.updatedAt());
    }

    @Transactional
    public Version saveGenerated(long competitionSeasonId, String competitionCode, int lastRound, UUID jobId, String snapshot,
            List<SetupDtos.GeneratedMatch> matches, Map<String, byte[]> files) {
        return saveGenerated(competitionSeasonId, competitionCode, 1, lastRound, jobId, snapshot, matches, files);
    }

    @Transactional
    public Version saveGenerated(long competitionSeasonId, String competitionCode, int firstRound, int lastRound,
            UUID jobId, String snapshot, List<SetupDtos.GeneratedMatch> matches, Map<String, byte[]> files) {
        lockSeason(competitionSeasonId);
        for (SetupDtos.GeneratedMatch match : matches) {
            if (!competitionCode.equals(match.competition()) || match.date() == null) {
                continue;
            }
            Integer round = numericRound(match.round());
            if (round == null) {
                continue;
            }
            Optional<MatchState> current = find(competitionSeasonId, round, match.home(), match.away());
            if (current.isEmpty()) {
                jdbc.sql("""
                        INSERT INTO season_match_state
                            (competition_season_id, round_no, home, away, status, play_date, play_hour, updated_at)
                        VALUES (:cs, :r, :home, :away, 'PLANIFICADA', :date, :hour, CURRENT_TIMESTAMP)""")
                        .param("cs", competitionSeasonId).param("r", round).param("home", match.home()).param("away", match.away())
                        .param("date", match.date()).param("hour", blankToNull(match.hour())).update();
                MatchState pending = new MatchState(round, match.home(), match.away(), PENDING, null, null);
                MatchState planned = new MatchState(round, match.home(), match.away(), PLANNED, match.date(), blankToNull(match.hour()));
                audit(competitionSeasonId, pending, planned, "planning-job:" + jobId, "INTERNAL:PLANNING_JOB",
                        "SYSTEM", "PLANNING_GENERATED", "Automatic planning job " + jobId);
                continue;
            }
            MatchState state = current.get();
            if (DELIVERED.equals(state.status()) || PLAYED.equals(state.status())) {
                if (!Objects.equals(state.date(), match.date()) || !Objects.equals(blankToNull(state.hour()), blankToNull(match.hour()))) {
                    throw new SetupExceptions.Conflict("Generation would change protected match "
                            + round + " " + match.home() + "-" + match.away());
                }
                continue;
            }
            MatchState planned = new MatchState(round, match.home(), match.away(), PLANNED, match.date(), blankToNull(match.hour()));
            if (PLANNED.equals(state.status()) && Objects.equals(state.date(), planned.date())
                    && Objects.equals(blankToNull(state.hour()), planned.hour())) {
                continue;
            }
            jdbc.sql("""
                    UPDATE season_match_state
                       SET status = 'PLANIFICADA', play_date = :date, play_hour = :hour, updated_at = CURRENT_TIMESTAMP
                     WHERE competition_season_id = :cs AND round_no = :r AND home = :home AND away = :away""")
                    .param("date", match.date()).param("hour", blankToNull(match.hour())).param("cs", competitionSeasonId)
                    .param("r", round).param("home", match.home()).param("away", match.away()).update();
            audit(competitionSeasonId, state, planned, "planning-job:" + jobId, "INTERNAL:PLANNING_JOB",
                    "SYSTEM", "PLANNING_GENERATED", "Automatic planning job " + jobId);
        }
        return generations.createVersion(competitionSeasonId, firstRound, lastRound, jobId, snapshot, matches, files);
    }

    @Transactional
    public MatchState changeStatus(long competitionSeasonId, MatchStatusRequest request, String actor, String authorizedAuthority,
            String actorType) {
        requireActorAndReason(actor, authorizedAuthority, request.reason());
        MatchState before = require(competitionSeasonId, request.round(), request.home(), request.away());
        if (before.date() == null) {
            throw new SetupExceptions.Conflict("A match must be planned before it can be delivered or marked played");
        }
        String target = request.status();
        boolean allowed = (PLANNED.equals(before.status()) && DELIVERED.equals(target))
                || (DELIVERED.equals(before.status()) && PLAYED.equals(target));
        if (!allowed) {
            throw new SetupExceptions.Conflict("Invalid match-state transition " + before.status() + " -> " + target);
        }
        jdbc.sql("""
                UPDATE season_match_state SET status = :status, updated_at = CURRENT_TIMESTAMP
                 WHERE competition_season_id = :cs AND round_no = :r AND home = :home AND away = :away""")
                .param("status", target).param("cs", competitionSeasonId).param("r", request.round())
                .param("home", request.home()).param("away", request.away()).update();
        MatchState after = new MatchState(before.round(), before.home(), before.away(), target, before.date(), before.hour());
        audit(competitionSeasonId, before, after, actor, authorizedAuthority, actorType, "STATUS_TRANSITION", request.reason());
        return after;
    }

    @Transactional
    public MatchState cancelPlanning(long competitionSeasonId, MatchPlanCancellationRequest request,
            String actor, String authorizedAuthority, String actorType) {
        requireActorAndReason(actor, authorizedAuthority, request.reason());
        MatchState before = require(competitionSeasonId, request.round(), request.home(), request.away());
        if (!PLANNED.equals(before.status())) {
            throw new SetupExceptions.Conflict("Only a planned match can return to pending");
        }
        Boolean wasLocked = jdbc.sql("""
                SELECT locked FROM fixture
                 WHERE competition_season_id = :cs AND round_no = :r AND home = :home AND away = :away""")
                .param("cs", competitionSeasonId).param("r", request.round())
                .param("home", request.home()).param("away", request.away()).query(Boolean.class).optional()
                .orElseThrow(() -> new SetupExceptions.NotFound("Fixture not found for the planned match"));
        int updated = jdbc.sql("""
                UPDATE fixture SET locked = :locked, play_date = NULL, play_hour = NULL
                 WHERE competition_season_id = :cs AND round_no = :r AND home = :home AND away = :away""")
                .param("locked", false).param("cs", competitionSeasonId).param("r", request.round())
                .param("home", request.home()).param("away", request.away()).update();
        if (updated != 1) {
            throw new SetupExceptions.Conflict("Fixture changed during plan cancellation; no state change was saved");
        }
        jdbc.sql("""
                UPDATE season_match_state
                   SET status = 'PENDIENTE', play_date = NULL, play_hour = NULL, updated_at = CURRENT_TIMESTAMP
                 WHERE competition_season_id = :cs AND round_no = :r AND home = :home AND away = :away""")
                .param("cs", competitionSeasonId).param("r", request.round())
                .param("home", request.home()).param("away", request.away()).update();
        MatchState after = new MatchState(before.round(), before.home(), before.away(), PENDING, null, null);
        audit(competitionSeasonId, before, after, actor, authorizedAuthority, actorType,
                "PLANNING_CANCELLED", request.reason(),
                valuesWithLock(before, wasLocked),
                valuesWithLock(after, false));
        return after;
    }

    @Transactional
    public MatchState reschedule(long competitionSeasonId, MatchRescheduleRequest request, String actor, String authorizedAuthority,
            String actorType) {
        requireActorAndReason(actor, authorizedAuthority, request.reason());
        if (!request.confirm()) {
            throw new SetupExceptions.Invalid("Explicit confirmation is required for reprogramming", List.of(
                    SetupDtos.ValidationIssue.error("match", "confirm must be true")));
        }
        if (request.date() == null || (request.hour() != null && !request.hour().matches("(?:[01]\\d|2[0-3]):[0-5]\\d"))) {
            throw new SetupExceptions.Invalid("Invalid rescheduled date or hour", List.of(
                    SetupDtos.ValidationIssue.error("match", "date is required and hour must be HH:mm")));
        }
        MatchState before = require(competitionSeasonId, request.round(), request.home(), request.away());
        if (PLAYED.equals(before.status())) {
            throw new SetupExceptions.Conflict("Played matches cannot be reprogrammed");
        }
        if (DELIVERED.equals(before.status()) && !request.confirm()) {
            throw new SetupExceptions.Conflict("Delivered matches require explicit exceptional confirmation");
        }
        validateRescheduleLimits(competitionSeasonId, before, request.date());
        ensureNoConflict(competitionSeasonId, before, request.date());

        String nextStatus = PENDING.equals(before.status()) ? PLANNED : before.status();
        MatchState after = new MatchState(before.round(), before.home(), before.away(), nextStatus, request.date(), blankToNull(request.hour()));
        jdbc.sql("""
                UPDATE season_match_state
                   SET status = :status, play_date = :date, play_hour = :hour, updated_at = CURRENT_TIMESTAMP
                 WHERE competition_season_id = :cs AND round_no = :r AND home = :home AND away = :away""")
                .param("status", nextStatus).param("date", after.date()).param("hour", after.hour())
                .param("cs", competitionSeasonId).param("r", request.round()).param("home", request.home()).param("away", request.away()).update();
        int fixtureUpdated = jdbc.sql("""
                UPDATE fixture SET locked = :locked, play_date = :date, play_hour = :hour
                 WHERE competition_season_id = :cs AND round_no = :r AND home = :home AND away = :away""")
                .param("locked", true).param("date", after.date()).param("hour", after.hour())
                .param("cs", competitionSeasonId).param("r", request.round()).param("home", request.home()).param("away", request.away()).update();
        if (fixtureUpdated != 1) {
            throw new SetupExceptions.Conflict("Fixture changed during reprogramming; no calendar change was saved");
        }
        roundDates.include(competitionSeasonId, request.round(), request.date());
        String action = before.date() == null || before.date().equals(request.date()) ? "REPROGRAMMED"
                : request.date().isBefore(before.date()) ? "ADELANTADO" : "APLAZADO";
        audit(competitionSeasonId, before, after, actor, authorizedAuthority, actorType, action, request.reason());
        return after;
    }

    @Transactional
    public SetupDtos.RoundRescheduleResult rescheduleRound(long cs, SetupDtos.RoundRescheduleRequest request, String actor,
            String authorizedAuthority, String actorType) {
        requireActorAndReason(actor, authorizedAuthority, request.reason());
        if (!request.confirm() && !request.dryRun()) {
            throw new SetupExceptions.Invalid("Explicit confirmation is required for reprogramming", List.of(
                    SetupDtos.ValidationIssue.error("round", "confirm must be true")));
        }
        if (request.date() == null || request.round() < 1) {
            throw new SetupExceptions.Invalid("Round and target date are required", List.of(
                    SetupDtos.ValidationIssue.error("round", "round >= 1 and date are required")));
        }
        if (request.includeFollowing() && !request.authorizeFollowing()) {
            throw new SetupExceptions.Conflict("Moving the following rounds requires explicit authorization (authorizeFollowing=true)");
        }
        lockSeason(cs);
        List<MatchState> scope = list(cs).stream()
                .filter(s -> request.includeFollowing() ? s.round() >= request.round() : s.round() == request.round())
                .filter(s -> s.date() != null).toList();
        LocalDate anchor = scope.stream().filter(s -> s.round() == request.round()).map(MatchState::date)
                .min(LocalDate::compareTo)
                .orElseThrow(() -> new SetupExceptions.Conflict("Round " + request.round() + " has no scheduled matches to move"));
        long shift = ChronoUnit.DAYS.between(anchor, request.date());
        if (shift == 0) {
            throw new SetupExceptions.Invalid("The round already starts on that date", List.of(
                    SetupDtos.ValidationIssue.error("round", "date must differ from the current start of the round")));
        }
        List<String> conflicts = new ArrayList<>();
        List<SetupDtos.MatchMove> moves = new ArrayList<>();
        for (MatchState s : scope) {
            String id = s.round() + " " + s.home() + "-" + s.away();
            if (DELIVERED.equals(s.status()) || PLAYED.equals(s.status())) {
                conflicts.add(id + ": protected match (" + s.status() + ") cannot be moved by a round operation");
            }
            Boolean pinned = jdbc.sql("""
                    SELECT locked FROM fixture WHERE competition_season_id = :cs AND round_no = :r AND home = :home AND away = :away""")
                    .param("cs", cs).param("r", s.round()).param("home", s.home()).param("away", s.away())
                    .query(Boolean.class).optional().orElse(Boolean.FALSE);
            if (Boolean.TRUE.equals(pinned)) {
                conflicts.add(id + ": pinned match cannot be moved by a round operation; unpin or reschedule it individually");
            }
            moves.add(new SetupDtos.MatchMove(s.round(), s.home(), s.away(), s.date(), s.date().plusDays(shift), s.hour()));
        }
        var transaction = org.springframework.transaction.interceptor.TransactionAspectSupport.currentTransactionStatus();
        Object savepoint = transaction.createSavepoint();
        if (conflicts.isEmpty()) {
            // Tentative application: the checks read the database, so the whole move must be visible to them.
            for (SetupDtos.MatchMove m : moves) {
                jdbc.sql("""
                        UPDATE season_match_state SET play_date = :date, updated_at = CURRENT_TIMESTAMP
                         WHERE competition_season_id = :cs AND round_no = :r AND home = :home AND away = :away""")
                        .param("date", m.to()).param("cs", cs).param("r", m.round()).param("home", m.home()).param("away", m.away()).update();
            }
            for (SetupDtos.MatchMove m : moves) {
                MatchState original = new MatchState(m.round(), m.home(), m.away(), PLANNED, m.from(), m.hour());
                try {
                    validateRescheduleLimits(cs, original, m.to());
                    ensureNoConflict(cs, original, m.to());
                } catch (SetupExceptions.Conflict | SetupExceptions.Invalid e) {
                    conflicts.add(m.round() + " " + m.home() + "-" + m.away() + ": " + e.getMessage());
                }
            }
        }
        if (!conflicts.isEmpty() || request.dryRun()) {
            transaction.rollbackToSavepoint(savepoint);
            return new SetupDtos.RoundRescheduleResult(false, request.dryRun(), (int) shift, moves, conflicts);
        }
        transaction.releaseSavepoint(savepoint);
        for (SetupDtos.MatchMove m : moves) {
            int fixture = jdbc.sql("""
                    UPDATE fixture SET locked = :locked, play_date = :date, play_hour = :hour
                     WHERE competition_season_id = :cs AND round_no = :r AND home = :home AND away = :away""")
                    .param("locked", true).param("date", m.to()).param("hour", blankToNull(m.hour()))
                    .param("cs", cs).param("r", m.round()).param("home", m.home()).param("away", m.away()).update();
            if (fixture != 1) {
                throw new SetupExceptions.Conflict("Fixture changed during round reprogramming; nothing was saved");
            }
            roundDates.include(cs, m.round(), m.to());
            MatchState before = new MatchState(m.round(), m.home(), m.away(), PLANNED, m.from(), m.hour());
            MatchState after = new MatchState(m.round(), m.home(), m.away(), PLANNED, m.to(), m.hour());
            audit(cs, before, after, actor, authorizedAuthority, actorType,
                    shift < 0 ? "ROUND_ADELANTADO" : "ROUND_APLAZADO", request.reason());
        }
        return new SetupDtos.RoundRescheduleResult(true, false, (int) shift, moves, List.of());
    }

    private void validateRescheduleLimits(long cs, MatchState before, LocalDate targetDate) {
        var season = jdbc.sql("""
                SELECT season_start, season_end, max_days_advance_match, max_days_postpone_match
                  FROM competition_season WHERE id = :cs""")
                .param("cs", cs).query().singleRow();
        LocalDate start = date(season.get("season_start"));
        LocalDate end = date(season.get("season_end"));
        if (targetDate.isBefore(start) || targetDate.isAfter(end)) {
            throw new SetupExceptions.Invalid("Rescheduled date is outside the season", List.of(
                    SetupDtos.ValidationIssue.error("match", "date must be within seasonStart and seasonEnd")));
        }
        if (before.date() != null) {
            long days = ChronoUnit.DAYS.between(before.date(), targetDate);
            int maximum = days < 0 ? (Integer) season.get("max_days_advance_match") : (Integer) season.get("max_days_postpone_match");
            if (Math.abs(days) > maximum) {
                throw new SetupExceptions.Invalid("Reschedule exceeds the configured advance/postpone limit", List.of(
                        SetupDtos.ValidationIssue.error("match", "maximum allowed movement is " + maximum + " days")));
            }
        }
        Integer blackout = jdbc.sql("""
                SELECT COUNT(*) FROM blackout WHERE competition_season_id = :cs
                 AND date_from <= :date AND date_to >= :date""")
                .param("cs", cs).param("date", targetDate).query(Integer.class).single();
        if (blackout > 0) {
            throw new SetupExceptions.Conflict("Rescheduled date conflicts with a blackout");
        }
        Integer venue = jdbc.sql("""
                SELECT COUNT(*) FROM constraint_rule
                 WHERE competition_season_id = :cs AND rule_type = 'VenueUnavailable'
                   AND team = :home AND date_from <= :date AND COALESCE(date_to, date_from) >= :date""")
                .param("cs", cs).param("home", before.home()).param("date", targetDate).query(Integer.class).single();
        if (venue > 0) {
            throw new SetupExceptions.Conflict("Rescheduled date conflicts with venue unavailability");
        }
        Integer fixedDate = jdbc.sql("""
                SELECT COUNT(*) FROM constraint_rule
                 WHERE competition_season_id = :cs AND rule_type = 'FixedHomeDate'
                   AND team = :home AND fixed_date IS NOT NULL AND fixed_date <> :date""")
                .param("cs", cs).param("home", before.home()).param("date", targetDate).query(Integer.class).single();
        if (fixedDate > 0) {
            throw new SetupExceptions.Conflict("Rescheduled date conflicts with the fixed home date");
        }
    }

    private void ensureNoConflict(long cs, MatchState match, LocalDate date) {
        Integer teamConflict = jdbc.sql("""
                SELECT COUNT(*) FROM season_match_state
                 WHERE competition_season_id = :cs AND play_date = :date AND status <> 'PENDIENTE'
                   AND NOT (round_no = :r AND home = :home AND away = :away)
                   AND (home = :home OR away = :home OR home = :away OR away = :away)""")
                .param("cs", cs).param("date", date).param("r", match.round()).param("home", match.home()).param("away", match.away())
                .query(Integer.class).single();
        Integer externalConflict = jdbc.sql("""
                SELECT COUNT(*) FROM external_commitment
                 WHERE competition_season_id = :cs AND play_date = :date
                   AND (home = :home OR away = :home OR home = :away OR away = :away)""")
                .param("cs", cs).param("date", date).param("home", match.home()).param("away", match.away())
                .query(Integer.class).single();
        Integer dayMatches = jdbc.sql("""
                SELECT COUNT(*) FROM season_match_state
                 WHERE competition_season_id = :cs AND play_date = :date AND status <> 'PENDIENTE'
                   AND NOT (round_no = :r AND home = :home AND away = :away)""")
                .param("cs", cs).param("date", date).param("r", match.round()).param("home", match.home()).param("away", match.away())
                .query(Integer.class).single();
        Integer maxMatches = jdbc.sql("SELECT max_matches_per_day FROM competition_season WHERE id = :cs")
                .param("cs", cs).query(Integer.class).single();
        Integer homePairConflict = jdbc.sql("""
                SELECT COUNT(*) FROM team_pair_rule p
                JOIN season_match_state s ON s.competition_season_id = p.competition_season_id
                 AND s.play_date = :date AND s.status <> 'PENDIENTE'
                WHERE p.competition_season_id = :cs AND p.kind = 'NOT_HOME_SAME_DAY'
                  AND NOT (s.round_no = :r AND s.home = :home AND s.away = :away)
                  AND ((p.team_a = :home AND s.home = p.team_b) OR (p.team_b = :home AND s.home = p.team_a))""")
                .param("cs", cs).param("date", date).param("r", match.round()).param("home", match.home()).param("away", match.away())
                .query(Integer.class).single();
        Integer externalHomePairConflict = jdbc.sql("""
                SELECT COUNT(*) FROM team_pair_rule p
                JOIN external_commitment e ON e.competition_season_id = p.competition_season_id AND e.play_date = :date
                WHERE p.competition_season_id = :cs AND p.kind = 'NOT_HOME_SAME_DAY'
                  AND ((p.team_a = :home AND e.home = p.team_b) OR (p.team_b = :home AND e.home = p.team_a))""")
                .param("cs", cs).param("date", date).param("home", match.home()).query(Integer.class).single();
        if (teamConflict > 0 || externalConflict > 0) {
            throw new SetupExceptions.Conflict("Rescheduled date conflicts with another fixture or external commitment");
        }
        if (dayMatches >= maxMatches) {
            throw new SetupExceptions.Conflict("Rescheduled date exceeds the configured daily match limit");
        }
        if (homePairConflict > 0 || externalHomePairConflict > 0) {
            throw new SetupExceptions.Conflict("Rescheduled date conflicts with a NOT_HOME_SAME_DAY pair");
        }
    }

    private void audit(long cs, MatchState before, MatchState after, String actor, String authorizedAuthority,
            String actorType, String action, String reason) {
        audit(cs, before, after, actor, authorizedAuthority, actorType, action, reason, values(before), values(after));
    }

    private void audit(long cs, MatchState before, MatchState after, String actor, String authorizedAuthority,
            String actorType, String action, String reason, String beforeValues, String afterValues) {
        jdbc.sql("""
                INSERT INTO match_change_audit
                    (competition_season_id, round_no, home, away, actor, actor_type, authorized_role, action, reason,
                     previous_status, new_status, previous_values, new_values, occurred_at)
                VALUES (:cs, :r, :home, :away, :actor, :actorType, :auth, :action, :reason, :previous, :next, :before, :after, CURRENT_TIMESTAMP)""")
                .param("cs", cs).param("r", before.round()).param("home", before.home()).param("away", before.away())
                .param("actor", actor).param("actorType", actorType).param("auth", authorizedAuthority)
                .param("action", action).param("reason", reason.trim())
                .param("previous", before.status()).param("next", after.status())
                .param("before", beforeValues).param("after", afterValues).update();
    }

    private Optional<MatchState> find(long cs, int round, String home, String away) {
        return jdbc.sql("""
                SELECT round_no, home, away, status, play_date, play_hour FROM season_match_state
                 WHERE competition_season_id = :cs AND round_no = :r AND home = :home AND away = :away""")
                .param("cs", cs).param("r", round).param("home", home).param("away", away)
                .query((rs, i) -> state(rs)).optional();
    }

    private MatchState require(long cs, int round, String home, String away) {
        return find(cs, round, home, away)
                .orElseThrow(() -> new SetupExceptions.NotFound("Match state not found for " + round + " " + home + "-" + away));
    }

    private long seasonId(String competitionId, String season) {
        return jdbc.sql("SELECT id FROM competition_season WHERE competition_id = :c AND season_id = :s")
                .param("c", competitionId).param("s", season).query(Long.class).optional()
                .orElseThrow(() -> new SetupExceptions.NotFound("Unknown competition/season " + competitionId + " " + season));
    }

    private void lockSeason(long cs) {
        int locked = jdbc.sql("UPDATE competition_season SET row_version = row_version WHERE id = :cs")
                .param("cs", cs).update();
        if (locked != 1) {
            throw new SetupExceptions.NotFound("Competition season " + cs + " not found");
        }
    }

    private static MatchState state(ResultSet rs) throws SQLException {
        return new MatchState(rs.getInt("round_no"), rs.getString("home"), rs.getString("away"), rs.getString("status"),
                date(rs.getDate("play_date")), rs.getString("play_hour"));
    }

    private static LocalDate date(Object value) {
        if (value == null) return null;
        if (value instanceof java.sql.Date d) return d.toLocalDate();
        if (value instanceof LocalDate d) return d;
        throw new IllegalArgumentException("Unexpected date value " + value);
    }

    private static String values(MatchState state) {
        return "{\"status\":\"" + state.status() + "\",\"date\":"
                + (state.date() == null ? "null" : "\"" + state.date() + "\"") + ",\"hour\":"
                + (state.hour() == null ? "null" : "\"" + state.hour() + "\"") + "}";
    }

    private static String valuesWithLock(MatchState state, boolean locked) {
        return values(state).replaceFirst("}$", ",\"locked\":" + locked + "}");
    }

    private static String key(int round, String home, String away) {
        return round + "\u0000" + home + "\u0000" + away;
    }

    private static Integer numericRound(String round) {
        if (round == null || !round.matches("[1-9][0-9]*")) return null;
        try {
            return Integer.valueOf(round);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void requireActorAndReason(String actor, String authorizedAuthority, String reason) {
        if (actor == null || actor.isBlank() || actor.length() > 200) {
            throw new SetupExceptions.Conflict("An authenticated actor is required");
        }
        if (authorizedAuthority == null || authorizedAuthority.isBlank() || authorizedAuthority.length() > 100) {
            throw new SetupExceptions.Conflict("A configured authorization authority is required");
        }
        if (reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new SetupExceptions.Invalid("A reason of 1..1000 characters is required", List.of(
                    SetupDtos.ValidationIssue.error("match", "reason is required")));
        }
    }
}
