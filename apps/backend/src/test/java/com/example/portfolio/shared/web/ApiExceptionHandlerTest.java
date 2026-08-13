package com.example.portfolio.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.server.ResponseStatusException;

class ApiExceptionHandlerTest {
    @Test
    void validationErrorsUseStableProblemDetailsEnvelope() throws Exception {
        var input = new Input("");
        var errors = new BeanPropertyBindingResult(input, "input");
        errors.rejectValue("name", "NotBlank");
        Method method = ApiExceptionHandlerTest.class.getDeclaredMethod("accept", Input.class);
        var exception = new MethodArgumentNotValidException(new MethodParameter(method, 0), errors);
        var request = new MockHttpServletRequest("POST", "/api/v1/example");
        request.setAttribute(RequestIdFilter.HEADER, "request-123");

        var problem = new ApiExceptionHandler().validation(exception, request);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Invalid request");
        assertThat(problem.getType().toString()).endsWith("/validation");
        assertThat(problem.getProperties())
                .containsEntry("code", "REQUEST_VALIDATION_FAILED")
                .containsEntry("requestId", "request-123")
                .containsKey("ruleIds")
                .containsKey("context");
    }

    @Test
    void ownerErrorsExplainWhetherToRetryAndWhatToDoNext() {
        var request = new MockHttpServletRequest("GET", "/api/v1/holdings/missing/analyst-report");
        request.setAttribute(RequestIdFilter.HEADER, "request-456");

        var problem = new ApiExceptionHandler()
                .status(
                        new ResponseStatusException(HttpStatus.CONFLICT, "Holding analysis has not been generated"),
                        request);

        assertThat(problem.getStatus()).isEqualTo(409);
        assertThat(problem.getProperties())
                .containsEntry("code", "ANALYSIS_NOT_READY")
                .containsEntry("retryable", false)
                .containsEntry("nextAction", "Wait for analysis to finish, then refresh.")
                .containsEntry("requestId", "request-456");
    }

    @SuppressWarnings("unused")
    private static void accept(Input input) {}

    private record Input(String name) {}
}
