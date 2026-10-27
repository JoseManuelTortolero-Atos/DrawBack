package net.atos.mev.calendarcalculator.setup;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

class DemoConfigurationTest {
    private MockEnvironment environment(boolean enabled, String... profiles) {
        var env = new MockEnvironment().withProperty("calendar.demo.enabled", Boolean.toString(enabled));
        env.setActiveProfiles(profiles);
        return env;
    }

    @Test
    void defaultDevProductionAndTestDoNotRequestDemo() {
        for (String profile : new String[] {"dev", "prod", "production", "test"}) {
            assertDoesNotThrow(() -> DemoConfiguration.validate(environment(false, profile)));
        }
    }

    @Test
    void onlyDevWithExplicitOptInCanSeed() {
        assertDoesNotThrow(() -> DemoConfiguration.validate(environment(true, "dev", "postgres")));
        assertThrows(IllegalStateException.class, () -> DemoConfiguration.validate(environment(true)));
        for (String profile : new String[] {"prod", "production", "test"}) {
            assertThrows(IllegalStateException.class, () -> DemoConfiguration.validate(environment(true, profile)));
            assertThrows(IllegalStateException.class, () -> DemoConfiguration.validate(environment(true, "dev", profile)));
        }
    }

    @Test
    void validatesConfigurableExampleBeforeAnyDatabaseInitialization() {
        for (String value : new String[] {"0", "3", "5", "18"}) {
            assertThrows(IllegalStateException.class, () -> DemoConfiguration.validate(
                    environment(true, "dev").withProperty("calendar.demo.team-count", value)));
        }
        for (String value : new String[] {"1999", "2099"}) {
            assertThrows(IllegalStateException.class, () -> DemoConfiguration.validate(
                    environment(true, "dev").withProperty("calendar.demo.season-year", value)));
        }
        assertDoesNotThrow(() -> DemoConfiguration.validate(environment(true, "dev")
                .withProperty("calendar.demo.season-year", "2032").withProperty("calendar.demo.team-count", "6")));
    }

    @Test
    void optInCreatesRunnerOnlyInDevAndForbiddenProfilesFailBeforeBeans() {
        var seeder = mock(DemoSeeder.class);
        var runner = new ApplicationContextRunner().withUserConfiguration(DemoConfiguration.class)
                .withBean(DemoSeeder.class, () -> seeder);
        runner.run(ctx -> assertEquals(0, ctx.getBeansOfType(ApplicationRunner.class).size()));
        runner.withPropertyValues("spring.profiles.active=dev", "calendar.demo.enabled=true",
                "calendar.demo.season-year=2032", "calendar.demo.team-count=6").run(ctx -> {
                    ctx.getBean(ApplicationRunner.class).run(null);
                    verify(seeder).seed(2032, 6);
                });
        runner.withPropertyValues("spring.profiles.active=dev,test", "calendar.demo.enabled=true").run(ctx -> {
            assertNotNull(ctx.getStartupFailure());
            assertTrue(ctx.getStartupFailure().getMessage().contains("Demo data requires"));
        });
        verifyNoMoreInteractions(seeder);
    }
}
