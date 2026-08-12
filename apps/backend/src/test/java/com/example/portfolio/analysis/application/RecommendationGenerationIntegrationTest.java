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
        var riskTruth = jdbc.sql(
                        """
                        SELECT COUNT(*) FROM recommendation
                        WHERE user_id=UUID_TO_BIN(:userId)
                          AND risk_calculation_reason<>'LEGACY_NOT_CALCULATED'
                          AND (risk_before_fraction IS NOT NULL
                               OR risk_calculation_reason='PORTFOLIO_RISK_EVIDENCE_UNAVAILABLE')
                        """)
                .param("userId", USER_ID.toString())
                .query(Integer.class)
                .single();
        assertThat(riskTruth).isEqualTo(3);
        assertThat(jdbc.sql(
                                "SELECT COUNT(*) FROM recommendation WHERE user_id=UUID_TO_BIN(:userId) AND tax_lot_status IN ('NOT_APPLICABLE','TAX_DATA_MISSING','TAX_LOTS_AVAILABLE_NOT_OPTIMIZED')")
                        .param("userId", USER_ID.toString())
                        .query(Integer.class)
                        .single())
                .isEqualTo(3);
        var narratives = jdbc.sql(
                        """
                        SELECT COUNT(*) FROM decision_narrative n
                        JOIN recommendation r ON r.id=n.recommendation_id
                        WHERE r.user_id=UUID_TO_BIN(:userId)
                          AND n.source='DETERMINISTIC_FALLBACK'
                          AND n.headline<>'' AND JSON_VALID(n.why_items)
                          AND n.input_checksum<>REPEAT('0',64) AND n.output_checksum<>REPEAT('0',64)
                        """)
                .param("userId", USER_ID.toString())
                .query(Integer.class)
                .single();
        assertThat(narratives).isEqualTo(3);
    }
}
