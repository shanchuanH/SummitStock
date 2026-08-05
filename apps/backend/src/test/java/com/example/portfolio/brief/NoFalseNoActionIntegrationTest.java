package com.example.portfolio.brief;

import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.portfolio.MySqlIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "portfolio.security.dev-user=no-action-guard@example.local")
@AutoConfigureMockMvc
class NoFalseNoActionIntegrationTest extends MySqlIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void emptyActionsCannotBecomeNoUrgentActionUnlessAnalysisIsReady() throws Exception {
        mockMvc.perform(get("/api/v1/brief/today")
                        .with(httpBasic("no-action-guard@example.local", "change-before-use")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state", not("ANALYSIS_READY")))
                .andExpect(jsonPath("$.headline", not("NO URGENT ACTION")))
                .andExpect(jsonPath("$.mustAct.length()").value(0))
                .andExpect(jsonPath("$.doNot.length()").value(0))
                .andExpect(jsonPath("$.watch.length()").value(0));
    }
}
