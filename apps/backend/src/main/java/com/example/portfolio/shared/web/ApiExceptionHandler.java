package com.example.portfolio.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail status(ResponseStatusException exception, HttpServletRequest request) {
        var status = exception.getStatusCode();
        var httpStatus = HttpStatus.resolve(status.value());
        var code =
                switch (status.value()) {
                    case 404 -> "RESOURCE_NOT_FOUND";
                    case 409 -> "ANALYSIS_NOT_READY";
                    case 422 -> "OWNER_INPUT_REQUIRED";
                    case 503 -> "DEPENDENCY_UNAVAILABLE";
                    default -> "REQUEST_FAILED";
                };
        var message = exception.getReason() == null || exception.getReason().isBlank()
                ? ownerDetail(status.value())
                : exception.getReason();
        var detail = ProblemDetail.forStatusAndDetail(status, message);
        detail.setType(URI.create(
                "https://portfolio.local/problems/" + code.toLowerCase().replace('_', '-')));
        detail.setTitle(httpStatus == null ? "Request failed" : httpStatus.getReasonPhrase());
        detail.setInstance(URI.create(request.getRequestURI()));
        detail.setProperty("code", code);
        detail.setProperty("requestId", request.getAttribute(RequestIdFilter.HEADER));
        detail.setProperty("retryable", status.value() == 429 || status.value() >= 500);
        detail.setProperty("nextAction", nextAction(status.value()));
        detail.setProperty("ruleIds", List.of());
        detail.setProperty("context", Map.of());
        return detail;
    }

    private static String ownerDetail(int status) {
        return switch (status) {
            case 404 -> "The requested owner-scoped record does not exist.";
            case 409 -> "The requested analysis is not ready yet.";
            case 422 -> "Owner confirmation or corrected input is required.";
            case 503 -> "A required data service is temporarily unavailable.";
            default -> "The request could not be completed.";
        };
    }

    private static String nextAction(int status) {
        return switch (status) {
            case 404 -> "Return to the portfolio and select an available item.";
            case 409 -> "Wait for analysis to finish, then refresh.";
            case 422 -> "Review the highlighted input and confirm it.";
            case 429, 503 -> "Retry later; do not act on stale or incomplete analysis.";
            default -> "Review the request and try again.";
        };
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        detail.setType(URI.create("https://portfolio.local/problems/validation"));
        detail.setTitle("Invalid request");
        detail.setInstance(URI.create(request.getRequestURI()));
        detail.setProperty("code", "REQUEST_VALIDATION_FAILED");
        detail.setProperty("requestId", request.getAttribute(RequestIdFilter.HEADER));
        detail.setProperty("ruleIds", List.of());
        detail.setProperty("context", Map.of("errorCount", exception.getErrorCount()));
        return detail;
    }
}
