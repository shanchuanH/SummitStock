package com.example.portfolio.identity;

import io.swagger.v3.oas.annotations.Parameter;
import java.security.Principal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {
    @GetMapping("/session")
    SessionResponse session(Principal principal) {
        return new SessionResponse(principal != null, principal == null ? null : principal.getName());
    }

    @GetMapping("/csrf")
    CsrfResponse csrf(@Parameter(hidden = true) CsrfToken token) {
        return new CsrfResponse(token.getHeaderName(), token.getParameterName(), token.getToken());
    }

    record SessionResponse(boolean authenticated, String username) {}

    record CsrfResponse(String headerName, String parameterName, String token) {}
}
