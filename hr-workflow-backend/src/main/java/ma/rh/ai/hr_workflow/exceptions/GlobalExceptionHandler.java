package ma.rh.ai.hr_workflow.exceptions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Translates exceptions into RFC 7807 {@link ProblemDetail} responses so every error
 * the API returns has the same shape: {@code {type, title, status, detail, instance}}.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleDuplicate(DuplicateResourceException ex) {
        return problem(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** Two requests changed the same execution at once (e.g. a double-clicked approve). */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleConcurrentUpdate(ObjectOptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "This item was changed by another request. Refresh and try again.");
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class,
            HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail handleBadRequest(Exception ex) {
        return problem(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Validation failed");
        pd.setProperty("errors", errors);
        return pd;
    }

    @ExceptionHandler({BadCredentialsException.class, DisabledException.class})
    public ProblemDetail handleAuthentication(RuntimeException ex) {
        return problem(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "You do not have permission to perform this action");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        // Spring's own web exceptions (404 no handler, 405 wrong method, 415, ...) already carry the right status
        if (ex instanceof ErrorResponse errorResponse) {
            return errorResponse.getBody();
        }
        log.error("Unhandled exception", ex);
        // Don't leak internal details (stack traces, SQL, paths) to the client
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
