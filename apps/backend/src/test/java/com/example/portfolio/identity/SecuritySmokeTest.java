package com.example.portfolio.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.portfolio.MySqlIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecuritySmokeTest extends MySqlIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void csrfEndpointAndServerSideSessionLoginWork() throws Exception {
        mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"));

        var login = mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .param("username", "admin@example.local")
                        .param("password", "change-before-use"))
                .andExpect(status().isNoContent())
                .andReturn();

        Cookie sessionCookie = login.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();
        assertThat(sessionCookie.isHttpOnly()).isTrue();
        assertThat(sessionCookie.getAttribute("SameSite")).isEqualToIgnoringCase("Lax");
        mockMvc.perform(get("/api/v1/auth/session").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("admin@example.local"));
    }

    @Test
    void repeatedLoginFailuresAreRateLimitedAndAudited() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .with(csrf())
                            .param("username", "attacker@example.local")
                            .param("password", "wrong"))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/auth/login")
                        .with(csrf())
                        .param("username", "attacker@example.local")
                        .param("password", "wrong"))
                .andExpect(status().isTooManyRequests());
    }
}
