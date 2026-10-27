package net.atos.mev.calendarcalculator.setup;

import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/** Errores RFC 7807 (application/problem+json). 422 incluye la lista {@code issues}. */
@RestControllerAdvice(assignableTypes = { SetupController.class, net.atos.mev.calendarcalculator.setup.draw.DrawController.class,
        net.atos.mev.calendarcalculator.setup.rules.RuleScopeController.class,
        FederationController.class, CompetitionAdminController.class, MasterDataController.class, LegacyAliasController.class })
public class SetupProblemHandler {

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail responseStatus(ResponseStatusException e) {
        return e.getBody();
    }

    @ExceptionHandler(SetupExceptions.NotFound.class)
    ProblemDetail notFound(SetupExceptions.NotFound e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(SetupExceptions.Conflict.class)
    ProblemDetail conflict(SetupExceptions.Conflict e) {
        ProblemDetail p = problem(HttpStatus.CONFLICT, e.getMessage());
        if (e instanceof SetupExceptions.BlockedDelete blocked) {
            p.setProperty("blockers", blocked.blockers());
        }
        return p;
    }

    @ExceptionHandler(SetupExceptions.Invalid.class)
    ProblemDetail invalid(SetupExceptions.Invalid e) {
        ProblemDetail p = problem(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        p.setProperty("issues", e.issues());
        return p;
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ProblemDetail duplicate(DuplicateKeyException e) {
        return problem(HttpStatus.CONFLICT, "The identifier already exists. Choose another identifier; no data was changed");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail referenced(DataIntegrityViolationException e) {
        return problem(HttpStatus.CONFLICT, "The operation conflicts with related data or a data constraint. Reload and review references and values; no data was changed");
    }

    @ExceptionHandler(ConcurrencyFailureException.class)
    ProblemDetail concurrent(ConcurrencyFailureException e) {
        return problem(HttpStatus.CONFLICT, "Related data changed during the operation. Reload and retry; no data was changed");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detail);
        p.setTitle(status.getReasonPhrase());
        return p;
    }
}
