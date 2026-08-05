package com.example.portfolio.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

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

    @SuppressWarnings("unused")
    private static void accept(Input input) {}

    private record Input(String name) {}
}
