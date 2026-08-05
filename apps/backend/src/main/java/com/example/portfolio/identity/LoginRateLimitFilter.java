package com.example.portfolio.identity;

import com.example.portfolio.shared.web.RequestIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpMethod;
import org.springframework.web.filter.OncePerRequestFilter;

class LoginRateLimitFilter extends OncePerRequestFilter {
    private final AuthSecurityStore events;

    LoginRateLimitFilter(AuthSecurityStore events) {
        this.events = events;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().equals("/api/v1/auth/login")
                || !request.getMethod().equals(HttpMethod.POST.name());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var username = request.getParameter("username");
        if (username != null && events.recentFailures(username, Duration.ofMinutes(1)) >= 5) {
            events.record(username, "LOGIN_RATE_LIMITED", request.getRemoteAddr(), (String)
                    request.getAttribute(RequestIdFilter.HEADER));
            response.sendError(429, "Too many login attempts");
            return;
        }
        chain.doFilter(request, response);
    }
}
