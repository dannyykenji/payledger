package com.payledger.common.exception;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Um ou mais campos estão inválidos."
        );
        problem.setTitle("Validation Error");
        problem.setType(URI.create("https://payledger.dev/errors/validation"));
        problem.setProperty("timestamp", Instant.now());

        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        err -> err.getDefaultMessage() != null ? err.getDefaultMessage() : "Inválido",
                        (first, second) -> first
                ));
        problem.setProperty("fields", fields);
        return problem;
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex) {
        return buildProblem(HttpStatus.CONFLICT, "Conflict", "conflict", ex.getMessage());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return buildProblem(HttpStatus.NOT_FOUND, "Resource Not Found", "not-found", ex.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex) {
        return buildProblem(HttpStatus.UNPROCESSABLE_ENTITY, "Business Rule Violation", "business-rule", ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        return buildProblem(HttpStatus.UNAUTHORIZED, "Unauthorized", "invalid-credentials", "Credenciais inválidas.");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingHeader(MissingRequestHeaderException ex) {
        return buildProblem(
                HttpStatus.BAD_REQUEST,
                "Missing Required Header",
                "missing-header",
                "Header obrigatório ausente: " + ex.getHeaderName()
        );
    }

    private ProblemDetail buildProblem(HttpStatus status, String title, String errorSlug, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("https://payledger.dev/errors/" + errorSlug));
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}
