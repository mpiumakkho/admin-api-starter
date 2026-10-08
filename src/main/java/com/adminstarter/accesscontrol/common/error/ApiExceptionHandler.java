package com.adminstarter.accesscontrol.common.error;

import com.adminstarter.accesscontrol.common.constant.ErrorCode;
import com.adminstarter.accesscontrol.common.context.RequestContext;
import com.adminstarter.accesscontrol.common.exception.AccessControlException;
import java.util.Map;
import java.util.Optional;
import org.hibernate.exception.ConstraintViolationException;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every refusal into an RFC 9457 problem detail with the {@code code} admin-ng-starter reads and the request
 * id for the logs: {@code { "status": 409, "code": "email_taken", "requestId": "…", … }}.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** Database constraints that stand for a rule, for the race the service checks cannot see */
    private static final Map<String, ErrorCode> CONSTRAINTS = Map.of(
            "ux_users_email", ErrorCode.EMAIL_TAKEN,
            "fk_users_role", ErrorCode.ROLE_IN_USE,
            "fk_admin_settings_default_role", ErrorCode.DEFAULT_ROLE);

    public static ProblemDetail problem(ErrorCode code) {
        var problem = ProblemDetail.forStatus(code.status());
        problem.setProperty("code", code.code());
        RequestContext.current().ifPresent(context -> problem.setProperty("requestId", context.requestId()));
        return problem;
    }

    @ExceptionHandler(AccessControlException.class)
    ResponseEntity<ProblemDetail> refused(AccessControlException exception) {
        return answer(exception.code());
    }

    /** {@code @PreAuthorize} said no */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> denied() {
        return answer(ErrorCode.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> unauthenticated() {
        return answer(ErrorCode.UNAUTHENTICATED);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> constraint(DataIntegrityViolationException exception) {
        return constraintName(exception)
                .map(name -> CONSTRAINTS.get(name.toLowerCase()))
                .map(ApiExceptionHandler::answer)
                .orElseGet(() -> failed(exception));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> failed(Exception exception) {
        log.error("Request failed", exception);
        var problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        RequestContext.current().ifPresent(context -> problem.setProperty("requestId", context.requestId()));
        return ResponseEntity.internalServerError().body(problem);
    }

    /** An id in the path that is not a UUID names nothing that exists */
    @Override
    protected @Nullable ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (exception instanceof MethodArgumentTypeMismatchException mismatch
                && mismatch.getParameter().hasParameterAnnotation(PathVariable.class)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem(ErrorCode.NOT_FOUND));
        }
        return super.handleTypeMismatch(exception, headers, status, request);
    }

    /** Spring MVC's own refusals (unreadable JSON, unknown fields, failed validation…) get a code by their status */
    @Override
    protected @Nullable ResponseEntity<Object> handleExceptionInternal(
            Exception exception, @Nullable Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var problem = body instanceof ProblemDetail detail ? detail : ProblemDetail.forStatus(status);
        codeFor(status).ifPresent(code -> problem.setProperty("code", code.code()));
        RequestContext.current().ifPresent(context -> problem.setProperty("requestId", context.requestId()));
        return super.handleExceptionInternal(exception, problem, headers, status, request);
    }

    private static ResponseEntity<ProblemDetail> answer(ErrorCode code) {
        return ResponseEntity.status(code.status()).body(problem(code));
    }

    private static Optional<ErrorCode> codeFor(HttpStatusCode status) {
        return Optional.ofNullable(switch (status.value()) {
            case 400 -> ErrorCode.INVALID_REQUEST;
            case 401 -> ErrorCode.UNAUTHENTICATED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 429 -> ErrorCode.TOO_MANY_REQUESTS;
            default -> null;
        });
    }

    private static Optional<String> constraintName(Throwable exception) {
        for (var cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation && violation.getConstraintName() != null) {
                return Optional.of(violation.getConstraintName());
            }
        }
        return Optional.empty();
    }
}
