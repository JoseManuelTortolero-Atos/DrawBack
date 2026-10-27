package net.atos.mev.calendarcalculator.setup;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.atos.mev.calendarcalculator.setup.SetupDtos.SeasonSetup;

/**
 * Lee src/test/resources/seed/seed-2026-27.tsv (generado por tools/seed/generate_seed.py con
 * la MISMA información que 002-seed-2026-27.sql) y construye los SeasonSetup tal y como los
 * devolvería SeasonSetupRepository. Permite probar mapper y motor sin PostgreSQL.
 */
public final class SeedFixture {

    private final Map<String, Builder> byId = new LinkedHashMap<>();

    private static final class Builder {
        String[] cs;
        final List<SetupDtos.Team> teams = new ArrayList<>();
        final List<String> slots = new ArrayList<>();
        final List<SetupDtos.AssignGroup> groups = new ArrayList<>();
        final Map<String, Integer> rest = new LinkedHashMap<>();
        final List<List<String>> pairs = new ArrayList<>();
        final List<List<String>> derbies = new ArrayList<>();
        final List<SetupDtos.Rule> rules = new ArrayList<>();
        final List<SetupDtos.Round> rounds = new ArrayList<>();
        final List<SetupDtos.Fixture> fixtures = new ArrayList<>();
        final List<SetupDtos.ExternalMatch> external = new ArrayList<>();
        final List<SetupDtos.GeneratedMatch> v0 = new ArrayList<>();
    }

    public static SeedFixture load() {
        try (InputStream in = SeedFixture.class.getResourceAsStream("/seed/seed-2026-27.tsv")) {
            if (in == null) {
                throw new IllegalStateException("seed/seed-2026-27.tsv not found in test resources");
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static SeedFixture parse(String text) {
        SeedFixture f = new SeedFixture();
        for (String line : text.split("\n")) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] p = line.split("\t", -1);
            Builder b = f.byId.computeIfAbsent(p[1], k -> new Builder());
            switch (p[0]) {
                case "CS" -> b.cs = p;
                case "TEAM" -> b.teams.add(new SetupDtos.Team(p[2], p[3], null, Boolean.parseBoolean(p[4]), Boolean.parseBoolean(p[5])));
                case "SLOT" -> b.slots.add(p[2]);
                case "GROUP" -> b.groups.add(new SetupDtos.AssignGroup(b.groups.size() + 1, p[2], Integer.parseInt(p[3])));
                case "REST" -> b.rest.put(p[2], Integer.parseInt(p[3]));
                case "PAIR" -> b.pairs.add(List.of(p[2], p[3]));
                case "DERBY" -> b.derbies.add(List.of(p[2], p[3]));
                case "RULE" -> b.rules.add(new SetupDtos.Rule(null, p[2], nz(p[3]), nz(p[4]), null, nz(p[5]), nz(p[6]),
                    p[7].isEmpty() ? null : Integer.valueOf(p[7]), null, null, null, null, p[8], null));
                case "ROUND" -> {
                    List<LocalDate> dates = new ArrayList<>();
                    for (String d : p[3].split(",")) {
                        dates.add(LocalDate.parse(d));
                    }
                    boolean numeric = p[2].matches("[0-9]+");
                    b.rounds.add(new SetupDtos.Round(numeric ? Integer.valueOf(p[2]) : null, numeric ? null : p[2], null, dates));
                }
                case "FIX" -> b.fixtures.add(new SetupDtos.Fixture(null, Integer.parseInt(p[2]), p[3], p[4], Boolean.parseBoolean(p[5]),
                        date(p[6]), nz(p[7])));
                case "EXT" -> b.external.add(new SetupDtos.ExternalMatch(null, p[2], nz(p[3]), date(p[4]), nz(p[5]), nz(p[6]), nz(p[7])));
                case "V0" -> b.v0.add(new SetupDtos.GeneratedMatch(p[2], nz(p[3]), date(p[4]), nz(p[5]), p[6], nz(p[7])));
                default -> throw new IllegalArgumentException("Unknown seed line " + p[0]);
            }
        }
        return f;
    }

    public SeasonSetup setup(String competitionId) {
        Builder b = byId.get(competitionId);
        String[] c = b.cs;
        SetupDtos.General g = new SetupDtos.General(LocalDate.parse(c[5]), LocalDate.parse(c[6]), date(c[7]), LocalDate.parse(c[8]),
                Integer.parseInt(c[9]), Integer.parseInt(c[10]), Integer.parseInt(c[11]), List.of(c[12].split(",")).contains("1"),
                Integer.parseInt(c[13]), Integer.parseInt(c[14]), Integer.parseInt(c[15]), "sat-sun");
        SetupDtos.EngineSettings engine = new SetupDtos.EngineSettings(Integer.parseInt(c[20]), Integer.parseInt(c[21]), c[12], c[22], List.copyOf(b.groups));
        return new SeasonSetup(competitionId, c[2], c[4], g, List.copyOf(b.slots), new SetupDtos.Ramadan(date(c[16]), date(c[17]), nz(c[18])),
                nz(c[19]), List.copyOf(b.teams), List.copyOf(b.rounds), List.copyOf(b.fixtures), List.copyOf(b.external), List.of(),
                new LinkedHashMap<>(b.rest), List.copyOf(b.pairs), List.copyOf(b.derbies), List.copyOf(b.rules), nz(c[23]),
                !c[23].isEmpty(), engine, 0, null);
    }

    public String code(String competitionId) {
        return byId.get(competitionId).cs[2];
    }

    /** Partidos de la versión v0 APROBADA (resultado legacy importado). */
    public List<SetupDtos.GeneratedMatch> approvedV0(String competitionId) {
        return List.copyOf(byId.get(competitionId).v0);
    }

    private static String nz(String v) {
        return v == null || v.isEmpty() ? null : v;
    }

    private static LocalDate date(String v) {
        return v == null || v.isEmpty() ? null : LocalDate.parse(v);
    }
}
