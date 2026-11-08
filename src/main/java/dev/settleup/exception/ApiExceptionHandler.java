package dev.settleup.exception;

import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Map;
import java.util.TreeMap;

/**
 * Every error is an RFC 9457 problem+json body.
 * <ul>
 *   <li>400 — malformed request (bad JSON, missing/invalid fields, unknown enum value)</li>
 *   <li>404 — group or expense does not exist</li>
 *   <li>409 — another write to the same group held the lock too long; safe to retry</li>
 *   <li>422 — well-formed but breaks a domain rule (percentages ≠ 100, sub-cent amount, …)</li>
 * </ul>
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "Not found", e.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    ProblemDetail businessRule(BusinessRuleException e) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Business rule violated", e.getMessage());
    }

    @ExceptionHandler(PessimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> lockTimeout(PessimisticLockingFailureException e) {
        ProblemDetail body = problem(HttpStatus.CONFLICT, "Group is busy",
                "Another change to this group is in progress. Please retry.");
        return ResponseEntity.status(HttpStatus.CONFLICT).header(HttpHeaders.RETRY_AFTER, "1").body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail typeMismatch(MethodArgumentTypeMismatchException e) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid parameter",
                "Invalid value '" + e.getValue() + "' for parameter '" + e.getName() + "'");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException ex,
                                                                  @NonNull HttpHeaders headers,
                                                                  @NonNull HttpStatusCode status,
                                                                  @NonNull WebRequest request) {
        Map<String, String> errors = new TreeMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> errors.putIfAbsent(toSnakeCase(fe.getField()), fe.getDefaultMessage()));
        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "One or more fields are invalid");
        body.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    /** Report field paths in the same snake_case the client sent ("allocations[0].participant_id"). */
    static String toSnakeCase(String field) {
        return field.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        return pd;
    }
}
