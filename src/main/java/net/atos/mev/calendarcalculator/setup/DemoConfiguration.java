package net.atos.mev.calendarcalculator.setup;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration(proxyBeanMethods = false)
public class DemoConfiguration {
    @Bean
    static BeanFactoryPostProcessor demoPolicy(Environment environment) {
        // Fail before Liquibase or any application bean can write to the database.
        return beanFactory -> validate(environment);
    }

    static void validate(Environment environment) {
        if (!environment.getProperty("calendar.demo.enabled", Boolean.class, false)) {
            return;
        }
        if (!environment.acceptsProfiles(Profiles.of("dev"))
                || environment.acceptsProfiles(Profiles.of("prod", "production", "test"))) {
            throw new IllegalStateException("Demo data requires profile dev and is forbidden with prod, production or test");
        }
        int year = environment.getProperty("calendar.demo.season-year", Integer.class, 2030);
        int teams = environment.getProperty("calendar.demo.team-count", Integer.class, 4);
        if (year < 2000 || year > 2098 || teams < 4 || teams > 16 || teams % 2 != 0) {
            throw new IllegalStateException("Demo requires season-year 2000..2098 and an even team-count 4..16");
        }
    }

    @Bean
    @ConditionalOnProperty(name = "calendar.demo.enabled", havingValue = "true")
    ApplicationRunner demoRunner(Environment environment, DemoSeeder seeder) {
        return args -> seeder.seed(environment.getProperty("calendar.demo.season-year", Integer.class, 2030),
                environment.getProperty("calendar.demo.team-count", Integer.class, 4));
    }
}
