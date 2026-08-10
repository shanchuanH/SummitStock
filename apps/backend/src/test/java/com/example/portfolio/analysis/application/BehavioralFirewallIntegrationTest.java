package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.analysis.domain.RecommendationAction;
import com.example.portfolio.strategy.RuleIds;
import org.junit.jupiter.api.Test;

class BehavioralFirewallIntegrationTest extends HoldingAnalysisIntegrationFixture {
    @Test
    void activeIdeaCooldownWinsOverOtherwiseEligibleNewCapital() {
        execute(
                """
                INSERT INTO investment_idea
                    (id,user_id,instrument_id,idea_created_at,cooldown_until,source_type,created_at)
                VALUES
                    (UUID_TO_BIN(UUID()),UUID_TO_BIN('91000000-0000-0000-0000-000000000001'),
                     UUID_TO_BIN('93000000-0000-0000-0000-000000000002'),UTC_TIMESTAMP(6),
                     DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 48 HOUR),'SOCIAL',UTC_TIMESTAMP(6))
                """);

        var analyzed = analysis.analyze(USER_ID, DRAM_POSITION);

        assertThat(analyzed.resolution().winner().action()).isEqualTo(RecommendationAction.DO_NOT_ADD);
        assertThat(analyzed.resolution().winner().ruleId()).isEqualTo(RuleIds.RISK_COOLING_PERIOD);
    }

    @Test
    void expiredSpeculativeTimeStopBecomesFormalExitAndCoolingCannotSuppressIt() {
        execute("UPDATE position SET opened_at=DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 120 DAY) "
                + "WHERE id=UUID_TO_BIN('94000000-0000-0000-0000-000000000003')");
        execute(
                """
                INSERT INTO investment_idea
                    (id,user_id,instrument_id,idea_created_at,cooldown_until,source_type,created_at)
                VALUES
                    (UUID_TO_BIN(UUID()),UUID_TO_BIN('91000000-0000-0000-0000-000000000001'),
                     UUID_TO_BIN('93000000-0000-0000-0000-000000000003'),UTC_TIMESTAMP(6),
                     DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 48 HOUR),'WATCHLIST',UTC_TIMESTAMP(6))
                """);

        var analyzed = analysis.analyze(USER_ID, DXYZ_POSITION);

        assertThat(analyzed.resolution().winner().action()).isEqualTo(RecommendationAction.EXIT);
        assertThat(analyzed.resolution().winner().ruleId()).isEqualTo(RuleIds.SPECULATIVE_TIME_STOP);
        assertThat(analyzed.resolution().suppressed())
                .anyMatch(candidate -> candidate.ruleId().equals(RuleIds.RISK_COOLING_PERIOD));
    }

    private void execute(String sql) {
        jdbc.sql(sql).update();
    }
}
