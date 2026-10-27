package net.atos.mev.calendarcalculator.setup;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import net.atos.mev.calendarcalculator.setup.SetupDtos.MatchPlanCancellationRequest;
import net.atos.mev.calendarcalculator.setup.jobs.PlanningJobService;
import net.atos.mev.calendarcalculator.setup.rules.RuleScopeService;
import tools.jackson.databind.json.JsonMapper;

class SetupControllerActorIdentityTest {

    private final SeasonSetupRepository setups = mock(SeasonSetupRepository.class);
    private final MatchStateService matchStates = mock(MatchStateService.class);
    private final SetupController controller = new SetupController(setups, mock(GenerationRepository.class),
            mock(SetupValidator.class), mock(PlanningJobService.class), mock(LegacyExcelBridge.class),
            mock(JsonMapper.class), mock(RuleScopeService.class), matchStates,
            "ROLE_SCHEDULE_USER", "ROLE_SCHEDULE_SERVICE", "calendar-planning-service");

    @Test
    void humanActorComesFromSubjectAndConfiguredHumanAuthority() {
        MatchPlanCancellationRequest request = new MatchPlanCancellationRequest(3, "A", "B", "Cancelled");
        when(setups.competitionSeasonId("COMP", "2026")).thenReturn(42L);
        var authentication = new JwtAuthenticationToken(
                Jwt.withTokenValue("test").header("alg", "none").claim("sub", "human-subject").build(),
                List.of(new SimpleGrantedAuthority("ROLE_SCHEDULE_USER")));

        controller.cancelMatchPlanning("COMP", "2026", request, authentication);

        verify(matchStates).cancelPlanning(42L, request, "human-subject", "ROLE_SCHEDULE_USER", "USER");
    }

    @Test
    void serviceActorComesFromConfiguredClientAndServiceAuthority() {
        MatchPlanCancellationRequest request = new MatchPlanCancellationRequest(3, "A", "B", "Cancelled");
        when(setups.competitionSeasonId("COMP", "2026")).thenReturn(42L);
        var authentication = new JwtAuthenticationToken(
                Jwt.withTokenValue("test").header("alg", "none").claim("sub", "service-account")
                        .claim("azp", "calendar-planning-service").build(),
                List.of(new SimpleGrantedAuthority("ROLE_SCHEDULE_SERVICE")));

        controller.cancelMatchPlanning("COMP", "2026", request, authentication);

        verify(matchStates).cancelPlanning(eq(42L), same(request), eq("oidc-service:calendar-planning-service"),
                eq("ROLE_SCHEDULE_SERVICE"), eq("SERVICE"));
    }
}
