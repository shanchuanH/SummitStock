package com.example.portfolio.portfolioimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.portfolio.portfolioimport.application.PortfolioImportPreviewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class PortfolioImportOwnershipTest extends PortfolioImportIntegrationSupport {
    @Autowired
    private PortfolioImportPreviewService previews;

    @AfterEach
    void removeOtherOwner() {
        update("DELETE b FROM portfolio_import_batch b JOIN app_user u ON u.id=b.user_id "
                + "WHERE u.email='other-import-owner@example.local'");
        update("DELETE FROM app_user WHERE email='other-import-owner@example.local'");
    }

    @Test
    void anotherUsersBatchCannotBeReadOrConfirmed() throws Exception {
        var other = previews.previewFidelity(
                "other-import-owner@example.local", fixture("fidelity-positions-updated.csv"), "other.csv");

        mockMvc.perform(get("/api/v1/portfolio-imports/{batchId}", other.batchId())
                        .with(httpBasic(EMAIL, PASSWORD)))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/portfolio-imports/{batchId}/confirm", other.batchId())
                        .with(httpBasic(EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0,\"accountMappings\":[],\"rowOverrides\":[],"
                                + "\"cashSetup\":{\"location\":\"IN_FIDELITY\",\"amount\":\"0\"}}"))
                .andExpect(status().isNotFound());
        assertThat(count("SELECT COUNT(*) FROM portfolio_analysis_run")).isZero();
    }
}
