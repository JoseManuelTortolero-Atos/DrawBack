package net.atos.mev.calendarcalculator.setup;

import java.util.List;

/** Excepciones de negocio de /api/setup; {@code SetupProblemHandler} las traduce a RFC 7807. */
public final class SetupExceptions {

    private SetupExceptions() {
    }

    /** 404 */
    public static class NotFound extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public NotFound(String message) {
            super(message);
        }
    }

    /** 409: otro usuario guardó antes (row_version distinto) o ya hay un job activo. */
    public static class Conflict extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public Conflict(String message) {
            super(message);
        }
    }

    public static class BlockedDelete extends Conflict {
        private static final long serialVersionUID = 1L;
        private final transient List<String> blockers;

        public BlockedDelete(String message, List<String> blockers) {
            super(message);
            this.blockers = List.copyOf(blockers);
        }

        public List<String> blockers() {
            return blockers;
        }
    }

    /** 422: la configuración tiene errores que bloquean la generación. */
    public static class Invalid extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final transient List<SetupDtos.ValidationIssue> issues;

        public Invalid(String message, List<SetupDtos.ValidationIssue> issues) {
            super(message);
            this.issues = List.copyOf(issues);
        }

        public List<SetupDtos.ValidationIssue> issues() {
            return issues;
        }
    }
}
