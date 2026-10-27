package net.atos.mev.calendarcalculator.setup;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class SetupValidatorTest {

    private final SeedFixture seed = SeedFixture.load();
    private final SetupValidator validator = new SetupValidator();

    @Test
    void theSeedIsValidForTheFirstFiveRounds() {
        for (String id : List.of("BOTOLA_D1", "BOTOLA_D2", "CNPFF_D1", "CNPFF_D2")) {
            List<SetupDtos.ValidationIssue> issues = validator.validate(seed.setup(id), 5, true);
            assertFalse(SetupValidator.hasErrors(issues), id + " " + issues);
        }
    }

    @Test
    void roundSixHasNoDatesYet() {
        List<SetupDtos.ValidationIssue> issues = validator.validate(seed.setup("BOTOLA_D1"), 6, true);
        assertTrue(issues.stream().anyMatch(i -> i.level().equals("error") && i.msg().equals("Round 6 has no date")), issues.toString());
    }

    @Test
    void lastRoundOutOfRange() {
        assertTrue(validator.validate(seed.setup("CNPFF_D1"), 23, true).stream().anyMatch(i -> i.section().equals("generate")));
        assertTrue(validator.validate(seed.setup("CNPFF_D1"), 0, true).stream().anyMatch(i -> i.section().equals("generate")));
    }

    @Test
    void secondLegMustMirrorTheFirst() {
        SetupDtos.SeasonSetup s = seed.setup("CNPFF_D1");
        List<SetupDtos.Fixture> fx = new ArrayList<>(s.fixtures());
        int idx = 0;
        while (fx.get(idx).round() != 12) {
            idx++;
        }
        SetupDtos.Fixture f = fx.get(idx);
        fx.set(idx, new SetupDtos.Fixture(null, 12, f.away(), f.home(), false, null, null));
        SetupDtos.SeasonSetup broken = new SetupDtos.SeasonSetup(s.competitionId(), s.code(), s.season(), s.general(), s.timeslots(), s.ramadan(),
                s.noLightSlot(), s.teams(), s.rounds(), fx, s.external(), s.blackouts(), s.restDays(), s.notHomeSameDay(), s.derbies(), s.rules(),
                s.dependsOn(), s.includeDependency(), s.engine(), s.rowVersion(), s.updatedAt());
        assertTrue(validator.validate(broken, 5, true).stream().anyMatch(i -> i.msg().contains("not the mirror of round 1")));
    }

    @Test
    void missingApprovedDependencyIsAWarningNotAnError() {
        List<SetupDtos.ValidationIssue> issues = validator.validate(seed.setup("BOTOLA_D2"), 5, false);
        assertTrue(issues.stream().anyMatch(i -> i.level().equals("warn") && i.msg().startsWith("No generated calendar yet")));
        assertFalse(SetupValidator.hasErrors(issues));
    }
}
