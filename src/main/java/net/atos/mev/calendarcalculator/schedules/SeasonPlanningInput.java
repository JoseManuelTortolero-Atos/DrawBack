package net.atos.mev.calendarcalculator.schedules;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Entrada del motor construida desde la BD (o desde cualquier fuente) sin ficheros.
 *
 * <ul>
 *   <li>{@code teams}: orden = posición del sorteo.</li>
 *   <li>{@code rounds}: fechas disponibles por jornada; {@code label} numérico = jornada,
 *       no numérico (MAJ1…) = bloque de aplazados.</li>
 *   <li>{@code fixtures}: las 2(n-1) jornadas, en el orden significativo (reparto de franjas).
 *       {@code date != null} = partido ya colocado en ese día; {@code hour != null} = hora confirmada.</li>
 *   <li>{@code external}: partidos de otras competiciones (incluida la liga de la que se depende).</li>
 * </ul>
 */
public record SeasonPlanningInput(
        String competitionCode,
        String competitionName,
        LocalDate seasonStart,
        LocalDate seasonEnd,
        List<TeamRef> teams,
        List<RoundDates> rounds,
        List<FixtureInput> fixtures,
        List<ExternalInput> external) {

    public SeasonPlanningInput {
        Objects.requireNonNull(competitionCode, "competitionCode");
        Objects.requireNonNull(seasonStart, "seasonStart");
        Objects.requireNonNull(seasonEnd, "seasonEnd");
        teams = List.copyOf(teams);
        rounds = List.copyOf(rounds);
        fixtures = List.copyOf(fixtures);
        external = List.copyOf(external);
    }

    public record TeamRef(String code, String name) {
    }

    public record RoundDates(String label, List<LocalDate> dates) {
        public RoundDates {
            dates = List.copyOf(dates);
        }
    }

    public record FixtureInput(int round, String home, String away, LocalDate date, String hour, boolean locked) {
    }

    public record ExternalInput(String competition, String round, LocalDate date, String hour, String home, String away) {
    }
}
