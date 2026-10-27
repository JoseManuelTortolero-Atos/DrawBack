package net.atos.mev.calendarcalculator.schedules;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Properties;

import net.atos.mev.calendarcalculator.Environment;
import net.atos.mev.calendarcalculator.Tournament;
import net.atos.mev.calendarcalculator.checks.Check;
import net.atos.mev.calendarcalculator.checks.CheckProcessor;

/** Historical file parsing is test-only, so parity checks do not restore a production fallback. */
public final class ReferenceProperties {
    private ReferenceProperties() { }

    public static Properties loadFromConfigFile(String file) {
        try (var input = ReferenceProperties.class.getClassLoader().getResourceAsStream(file)) {
            if (input == null) throw new IllegalArgumentException("Reference fixture not found: " + file);
            var properties = new Properties();
            properties.load(input);
            return properties;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public static SchEnvironment scheduling(Properties properties) {
        Properties original = loadFromConfigFile(properties.getProperty("env"));
        var teams = new ArrayList<String[]>();
        int count = Integer.parseInt(original.getProperty("nTeams"));
        for (int i = 0; i < count; i++) {
            String code = original.getProperty("team." + i);
            teams.add(new String[] {code, original.getProperty("team." + code)});
        }
        var env = Environment.inMemory(original.getProperty("name"), original.getProperty("code"), teams);
        env.nRounds = Integer.parseInt(original.getProperty("nRounds"));
        var checks = new ArrayList<Check>();
        for (int i = 1; i <= Integer.parseInt(original.getProperty("nChecks")); i++) {
            String definition = original.getProperty("check." + i);
            if (definition != null) {
                Check parsed = CheckProcessor.createCheck(env, definition);
                if (parsed != null) checks.add(parsed);
            }
        }
        env.checks = checks.toArray(Check[]::new);
        env.nChecks = checks.size();
        env.setOriginalProp(original);
        var result = SchEnvironment.inMemory(SchEnvironment.readDate(properties.getProperty("start")),
                SchEnvironment.readDate(properties.getProperty("end")), env,
                Tournament.readFromLineWithAssignments(env, properties.getProperty("tournament")), properties);
        result.resultsFolder = properties.getProperty("resultsFolder");
        result.datesFile = properties.getProperty("datesFile");
        return result;
    }
}
