package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RecommendationGenerationIntegrationTest extends HoldingAnalysisIntegrationFixture {
    @Test
    void persistsResolvedRecommendationsWithAuditEvidence() {
        var generated = recommendations.generateAll(USER_ID);

        assertThat(generated).hasSize(3);
        var evidence = jdbc.sql(
                        """
                        SELECT COUNT(*) FROM recommendation
                        WHERE user_id=UUID_TO_BIN(:userId) AND status='ACTIVE'
                          AND winning_rule<>'' AND JSON_VALID(suppressed_candidates)
                          AND resolution_reason<>'' AND config_hash<>REPEAT('0',64)
                        """)
                .param("userId", USER_ID.toString())
                .query(Integer.class)
                .single();
        assertThat(evidence).isEqualTo(3);
    }
}
