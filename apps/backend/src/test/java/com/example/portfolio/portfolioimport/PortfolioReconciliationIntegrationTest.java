package com.example.portfolio.portfolioimport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PortfolioReconciliationIntegrationTest extends PortfolioImportIntegrationSupport {
    @Test
    void laterFullSnapshotUpdatesPositionsClosesMissingHoldingsAndAppendsEvidence() throws Exception {
        var first = preview("fidelity-positions.csv");
        confirm(uuid(first, "batchId"), 0, "[{\"rowNumber\":7,\"ignored\":true}]");

        var updated = preview("fidelity-positions-updated.csv");
        var result = confirm(uuid(updated, "batchId"), 0, "[]");

        assertThat(result.get("openPositionCount").asInt()).isEqualTo(2);
        assertThat(result.get("closedPositionCount").asInt()).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM position_snapshot")).isEqualTo(5);
        assertThat(count("SELECT COUNT(*) FROM position WHERE import_source='FIDELITY_CSV' AND status='CLOSED'"))
                .isEqualTo(1);
        assertThat(jdbc.sql("SELECT CAST(quantity AS CHAR) FROM position p JOIN instrument i ON i.id=p.instrument_id "
                                + "JOIN investment_account a ON a.id=p.account_id "
                                + "WHERE i.symbol='SPY' AND a.external_account_key LIKE '***5678|%'")
                        .query(String.class)
                        .single())
                .startsWith("11");
        assertThat(jdbc.sql("SELECT CAST(current_amount AS CHAR) FROM cash_bucket c "
                                + "JOIN investment_account a ON a.id=c.account_id "
                                + "WHERE a.external_account_key LIKE '***5678|%'")
                        .query(String.class)
                        .single())
                .startsWith("13000");
        assertThat(jdbc.sql("SELECT CAST(quantity AS CHAR) FROM compensation_holding")
                        .query(String.class)
                        .single())
                .startsWith("18");
    }
}
