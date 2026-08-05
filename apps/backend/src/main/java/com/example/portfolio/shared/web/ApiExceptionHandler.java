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

@RestControllerAdvice
public class ApiExceptionHandler {
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
