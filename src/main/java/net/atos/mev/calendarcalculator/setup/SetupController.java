package net.atos.mev.calendarcalculator.setup;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import tools.jackson.databind.json.JsonMapper;

import net.atos.mev.calendarcalculator.setup.rules.RuleScopeService;
import net.atos.mev.calendarcalculator.setup.jobs.PlanningJobService;

/**
 * API del Configurador (mockup/CONTRACT.md §5). Toda la configuración se lee y se guarda en
 * PostgreSQL. CHG-006/007 se publican aquí cuando la baseline ya dispone del catálogo.
 */
@RestController
@RequestMapping("/api/setup")
public class SetupController {

    private final SeasonSetupRepository setups;
    private final GenerationRepository generations;
    private final SetupValidator validator;
    private final PlanningJobService jobs;
    private final LegacyExcelBridge excel;
    private final JsonMapper json;
    private final RuleScopeService scopedRules;
    private final MatchStateService matchStates;
    private final String requiredAuthority;
    private final String serviceAuthority;
    private final String serviceClientId;

    public SetupController(SeasonSetupRepository setups, GenerationRepository generations, SetupValidator validator, PlanningJobService jobs,
            LegacyExcelBridge excel, JsonMapper json, RuleScopeService scopedRules, MatchStateService matchStates,
            @Value("${calendar.security.oidc.required-authority:}") String requiredAuthority,
            @Value("${calendar.security.oidc.service-authority:}") String serviceAuthority,
            @Value("${calendar.security.oidc.service-client-id:}") String serviceClientId) {
        this.scopedRules = scopedRules;
        this.matchStates = matchStates;
        this.requiredAuthority = requiredAuthority;
        this.serviceAuthority = serviceAuthority;
        this.serviceClientId = serviceClientId;
        this.setups = setups;
        this.generations = generations;
        this.validator = validator;
        this.jobs = jobs;
        this.excel = excel;
        this.json = json;
    }

    @GetMapping("/competitions")
    public List<SetupDtos.CompetitionSeasonRef> competitions() {
        return setups.listCompetitions();
    }

    @GetMapping("/competitions/{cid}/seasons/{season}")
    public SetupDtos.SeasonSetup load(@PathVariable String cid, @PathVariable String season) {
        return setups.load(cid, season);
    }

    @PutMapping("/competitions/{cid}/seasons/{season}")
    public SetupDtos.SeasonSetup save(@PathVariable String cid, @PathVariable String season, @RequestBody SetupDtos.SeasonSetup body) {
        if (!cid.equals(body.competitionId()) || !season.equals(body.season())) {
            throw new IllegalArgumentException("Path and body competition/season differ");
        }
        setups.replace(body);
        return setups.load(cid, season);
    }

    /** Valida el borrador recibido (o lo guardado si no hay cuerpo). */
    @PostMapping("/competitions/{cid}/seasons/{season}/validate")
    public List<SetupDtos.ValidationIssue> validate(@PathVariable String cid, @PathVariable String season,
            @RequestParam(required = false) Integer firstRoundToAssign, @RequestParam(required = false) Integer lastRoundToAssign,
            @RequestBody(required = false) SetupDtos.SeasonSetup draft) {
        SetupDtos.SeasonSetup s = scopedRules.withScopedRules(draft != null ? draft : setups.load(cid, season));
        boolean depApproved = s.dependsOn() == null || generations.approvedMatches(s.dependsOn(), season).isPresent();
        return firstRoundToAssign == null ? validator.validate(s, lastRoundToAssign, depApproved)
                : validator.validate(s, firstRoundToAssign, lastRoundToAssign, depApproved);
    }

    @PostMapping("/competitions/{cid}/seasons/{season}/planning-jobs")
    public ResponseEntity<SetupDtos.PlanningJob> start(@PathVariable String cid, @PathVariable String season,
            @RequestBody(required = false) SetupDtos.StartPlanningRequest body) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(jobs.start(cid, season,
                body == null ? null : body.firstRoundToAssign(), body == null ? null : body.lastRoundToAssign()));
    }

    @GetMapping("/planning-jobs/{id}")
    public SetupDtos.PlanningJob job(@PathVariable UUID id) {
        return jobs.job(id);
    }

    @GetMapping("/planning-jobs/{id}/logs")
    public List<SetupDtos.JobLogLine> logs(@PathVariable UUID id, @RequestParam(defaultValue = "0") int from) {
        return jobs.logs(id, from);
    }

    @PostMapping("/planning-jobs/{id}/cancel")
    public SetupDtos.PlanningJob cancel(@PathVariable UUID id) {
        return jobs.cancel(id);
    }

    @PatchMapping("/competitions/{cid}/seasons/{season}/fixtures/lock")
    public SetupDtos.SeasonSetup lockFixture(@PathVariable String cid, @PathVariable String season,
            @RequestBody SetupDtos.FixtureLockRequest body) {
        setups.lockFixture(cid, season, body);
        return setups.load(cid, season);
    }

    @GetMapping("/competitions/{cid}/seasons/{season}/versions")
    public List<SetupDtos.Version> versions(@PathVariable String cid, @PathVariable String season) {
        return generations.versions(setups.competitionSeasonId(cid, season));
    }

    @GetMapping("/competitions/{cid}/seasons/{season}/versions/{n}/matches")
    public List<SetupDtos.GeneratedMatch> matches(@PathVariable String cid, @PathVariable String season, @PathVariable int n) {
        return generations.matches(generations.versionId(setups.competitionSeasonId(cid, season), n));
    }

    @GetMapping("/competitions/{cid}/seasons/{season}/match-states")
    public List<SetupDtos.MatchState> matchStates(@PathVariable String cid, @PathVariable String season) {
        return matchStates.list(setups.competitionSeasonId(cid, season));
    }

    @PostMapping("/competitions/{cid}/seasons/{season}/matches/status")
    public SetupDtos.MatchState changeMatchStatus(@PathVariable String cid, @PathVariable String season,
            @RequestBody SetupDtos.MatchStatusRequest body, Authentication authentication) {
        ActorIdentity identity = actorIdentity(authentication);
        return matchStates.changeStatus(setups.competitionSeasonId(cid, season), body, identity.actor(),
                identity.authority(), identity.actorType());
    }

    @PostMapping("/competitions/{cid}/seasons/{season}/matches/unplan")
    public SetupDtos.MatchState cancelMatchPlanning(@PathVariable String cid, @PathVariable String season,
            @RequestBody SetupDtos.MatchPlanCancellationRequest body, Authentication authentication) {
        ActorIdentity identity = actorIdentity(authentication);
        return matchStates.cancelPlanning(setups.competitionSeasonId(cid, season), body, identity.actor(),
                identity.authority(), identity.actorType());
    }

    @PostMapping("/competitions/{cid}/seasons/{season}/matches/reschedule")
    public SetupDtos.MatchState rescheduleMatch(@PathVariable String cid, @PathVariable String season,
            @RequestBody SetupDtos.MatchRescheduleRequest body, Authentication authentication) {
        ActorIdentity identity = actorIdentity(authentication);
        return matchStates.reschedule(setups.competitionSeasonId(cid, season), body, identity.actor(),
                identity.authority(), identity.actorType());
    }

    @PostMapping("/competitions/{cid}/seasons/{season}/rounds/reschedule")
    public SetupDtos.RoundRescheduleResult rescheduleRound(@PathVariable String cid, @PathVariable String season,
            @RequestBody SetupDtos.RoundRescheduleRequest body, Authentication authentication) {
        ActorIdentity identity = actorIdentity(authentication);
        return matchStates.rescheduleRound(setups.competitionSeasonId(cid, season), body, identity.actor(),
                identity.authority(), identity.actorType());
    }

    private ActorIdentity actorIdentity(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            return ActorIdentity.denied();
        }
        var jwt = jwtAuthentication.getToken();
        String clientId = jwt.getClaimAsString("azp");
        if (clientId == null || clientId.isBlank()) clientId = jwt.getClaimAsString("client_id");
        if (serviceClientId != null && !serviceClientId.isBlank() && serviceClientId.equals(clientId)) {
            String authority = exactAuthority(authentication, serviceAuthority);
            if (authority == null) {
                return ActorIdentity.denied();
            }
            return new ActorIdentity("oidc-service:" + clientId, "SERVICE", authority);
        }
        String subject = jwt.getSubject();
        String authority = exactAuthority(authentication, requiredAuthority);
        if (subject == null || subject.isBlank() || authority == null) {
            return ActorIdentity.denied();
        }
        return new ActorIdentity(subject, "USER", authority);
    }

    private static String exactAuthority(Authentication authentication, String required) {
        if (authentication == null || required == null || required.isBlank()) {
            return null;
        }
        return authentication.getAuthorities().stream().map(org.springframework.security.core.GrantedAuthority::getAuthority)
                .filter(required.trim()::equals).findFirst().orElse(null);
    }

    private record ActorIdentity(String actor, String actorType, String authority) {
        static ActorIdentity denied() {
            return new ActorIdentity(null, null, null);
        }
    }

    @GetMapping("/competitions/{cid}/seasons/{season}/versions/{n}/files/{name:.+}")
    public ResponseEntity<byte[]> file(@PathVariable String cid, @PathVariable String season, @PathVariable int n, @PathVariable String name) {
        GenerationRepository.StoredFile f = generations.file(generations.versionId(setups.competitionSeasonId(cid, season), n), name)
                .orElseThrow(() -> new SetupExceptions.NotFound("File " + name + " not found in version " + n));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(f.name()).build().toString())
                .body(f.content());
    }

    @PostMapping("/competitions/{cid}/seasons/{season}/versions/{n}/approve")
    public List<SetupDtos.Version> approve(@PathVariable String cid, @PathVariable String season, @PathVariable int n, Principal principal) {
        long cs = setups.competitionSeasonId(cid, season);
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new SetupExceptions.Conflict("An authenticated approver is required");
        }
        generations.approve(cs, n, principal.getName());
        return generations.versions(cs);
    }

    /** Restaura la configuración guardada con la versión (config_snapshot). */
    @PostMapping("/competitions/{cid}/seasons/{season}/versions/{n}/restore")
    public SetupDtos.SeasonSetup restore(@PathVariable String cid, @PathVariable String season, @PathVariable int n) throws IOException {
        long cs = setups.competitionSeasonId(cid, season);
        String snap = generations.configSnapshot(generations.versionId(cs, n))
                .orElseThrow(() -> new SetupExceptions.NotFound("Version " + n + " has no configuration snapshot"));
        if (snap.isEmpty()) {
            throw new SetupExceptions.NotFound("Version " + n + " has no configuration snapshot (legacy import)");
        }
        SetupDtos.SeasonSetup old = json.readValue(snap, SetupDtos.SeasonSetup.class);
        SetupDtos.SeasonSetup current = setups.load(cid, season);
        setups.replace(new SetupDtos.SeasonSetup(cid, old.code(), season, old.general(), old.timeslots(), old.ramadan(), old.noLightSlot(),
                old.teams(), old.rounds(), old.fixtures(), old.external(), old.blackouts(), old.restDays(), old.notHomeSameDay(), old.derbies(),
                old.rules(), old.dependsOn(), old.includeDependency(), old.engine(), current.rowVersion(), current.updatedAt()));
        return setups.load(cid, season);
    }

    /** Vista previa: no guarda nada hasta que el front haga PUT. */
    @PostMapping(path = "/competitions/{cid}/seasons/{season}/import-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SetupDtos.SeasonSetup importExcel(@PathVariable String cid, @PathVariable String season, @RequestPart("file") MultipartFile file)
            throws IOException {
        return excel.importPreview(setups.load(cid, season), file.getBytes());
    }

    @GetMapping("/competitions/{cid}/seasons/{season}/export-excel")
    public ResponseEntity<byte[]> exportExcel(@PathVariable String cid, @PathVariable String season) {
        SetupDtos.SeasonSetup s = setups.load(cid, season);
        List<SetupDtos.GeneratedMatch> dep = s.dependsOn() == null || !s.includeDependency() ? List.of()
                : generations.approvedMatches(s.dependsOn(), season).orElse(List.of());
        String depCode = s.dependsOn() == null ? null : setups.competitionCode(s.dependsOn());
        byte[] body = excel.export(s, dep, depCode);
        String name = "Calendar" + s.code() + "-" + season + ".xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(GenerationRepository.contentType(name)))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .body(body);
    }
}
