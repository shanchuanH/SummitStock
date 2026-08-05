package com.example.portfolio.identity;

import com.example.portfolio.configuration.PortfolioProperties;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class SecurityConfiguration {
    @Bean
    DefaultCookieSerializer sessionCookieSerializer(@Value("${SESSION_COOKIE_SECURE:false}") boolean secure) {
        var serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SESSION");
        serializer.setUseHttpOnlyCookie(true);
        serializer.setSameSite("Lax");
        serializer.setUseSecureCookie(secure);
        return serializer;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService users(PortfolioProperties properties, PasswordEncoder encoder) {
        var user = User.withUsername(properties.security().devUser())
                .password(encoder.encode(properties.security().devPassword()))
                .roles("OWNER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthSecurityStore authEvents) throws Exception {
        return http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/instruments",
                                "/api/v1/instruments/**",
                                "/api/v1/market/regime",
                                "/api/v1/market/data-health")
                        .permitAll()
                        .requestMatchers(
                                "/actuator/health/**",
                                "/api/v1/version",
                                "/api/v1/auth/session",
                                "/api/v1/auth/csrf",
                                "/v3/api-docs/**")
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .formLogin(form -> form.loginProcessingUrl("/api/v1/auth/login")
                        .successHandler((request, response, authentication) -> {
                            authEvents.record(
                                    authentication.getName(), "LOGIN_SUCCEEDED", request.getRemoteAddr(), null);
                            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                        })
                        .failureHandler((request, response, exception) -> {
                            authEvents.record(
                                    request.getParameter("username"), "LOGIN_FAILED", request.getRemoteAddr(), null);
                            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                        }))
                .logout(logout -> logout.logoutUrl("/api/v1/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT))
                        .invalidateHttpSession(true)
                        .deleteCookies("SESSION"))
                .httpBasic(Customizer.withDefaults())
                .addFilterBefore(new LoginRateLimitFilter(authEvents), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
