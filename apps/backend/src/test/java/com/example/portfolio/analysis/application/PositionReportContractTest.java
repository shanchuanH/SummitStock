package com.example.portfolio.analysis.application;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class PositionReportContractTest extends HoldingAnalysisIntegrationFixture {
    @Autowired
    MockMvc mockMvc;

    @Test
    void reportIsOwnerScopedAndExposesStructuredAuditEvidence() throws Exception {
        recommendations.generateAll(USER_ID);

        mockMvc.perform(get("/api/v1/positions/{positionId}/report", GOOGL_POSITION)
                        .with(httpBasic(USER_EMAIL, "change-before-use")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position.symbol").value("GOOGL"))
                .andExpect(jsonPath("$.position.classificationSource").value("USER_CONFIRMED"))
                .andExpect(jsonPath("$.readiness").value("READY"))
                .andExpect(jsonPath("$.recommendation.action").value("TRIM"))
                .andExpect(jsonPath("$.recommendation.reasons").isArray())
                .andExpect(jsonPath("$.recommendation.suppressedCandidates").isArray())
                .andExpect(jsonPath("$.evidence.ruleIds").isArray())
                .andExpect(jsonPath("$.evidence.configHash").isString())
                .andExpect(jsonPath("$.dataAsOf").exists());
    }
}
