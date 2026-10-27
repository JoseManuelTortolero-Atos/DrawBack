package net.atos.mev.calendarcalculator.schedules;

/** Lanzada por el motor cuando se llama a {@link ScheduleMoroccoAlg#cancel()} (o se interrumpe el hilo). */
public class PlanningCancelledException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PlanningCancelledException(String message) {
        super(message);
    }
}
