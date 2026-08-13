package com.example.portfolio.portfolioimport;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.portfolioimport.application.PortfolioCashflowReconciliationService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PortfolioCashflowReconciliationServiceIntegrationTest extends PortfolioImportIntegrationSupport {
    @Autowired
    private PortfolioCashflowReconciliationService reconciliation;

    @Test
    void recordsDefensibleCashOnlyChangeAndRejectsAmbiguousChange() throws Exception {
        var confirmed =
                confirm(uuid(preview("fidelity-positions.csv"), "batchId"), 0, "[{\"rowNumber\":7,\"ignored\":true}]");
        var userId = jdbc.sql("SELECT BIN_TO_UUID(id) FROM app_user WHERE email=:email")
                .param("email", EMAIL)
                .query(UUID.class)
                .single();

        var cashOnlyBefore = reconciliation.before(userId);
        update("UPDATE cash_bucket c JOIN investment_account a ON a.id=c.account_id "
                + "SET c.current_amount=c.current_amount+7000 WHERE a.user_id=UUID_TO_BIN('" + userId
                + "') AND a.import_source='FIDELITY_CSV' AND c.bucket_type='ALLOCATED_TRADE'");
        var recorded = reconciliation.reconcile(userId, uuid(confirmed, "batchId"), cashOnlyBefore);

        assertThat(recorded.status()).isEqualTo("RECORDED");
        assertThat(recorded.cashChange()).isEqualByComparingTo("7000");
        assertThat(count("SELECT COUNT(*) FROM portfolio_external_cashflow_event WHERE amount=7000 "
                        + "AND source='BROKER_IMPORT_RECONCILIATION'"))
                .isEqualTo(1);

        var ambiguousBefore = reconciliation.before(userId);
        update("UPDATE cash_bucket c JOIN investment_account a ON a.id=c.account_id "
                + "SET c.current_amount=c.current_amount+1000 WHERE a.user_id=UUID_TO_BIN('" + userId
                + "') AND a.import_source='FIDELITY_CSV' AND c.bucket_type='ALLOCATED_TRADE'");
        update("UPDATE position SET quantity=quantity+1 WHERE id=(SELECT id FROM (SELECT p.id FROM position p "
                + "JOIN investment_account a ON a.id=p.account_id WHERE a.user_id=UUID_TO_BIN('" + userId
                + "') AND p.status='OPEN' ORDER BY p.id LIMIT 1) selected_position)");
        var ambiguous = reconciliation.reconcile(userId, UUID.randomUUID(), ambiguousBefore);

        assertThat(ambiguous.status()).isEqualTo("NAV_RECONCILIATION_REQUIRED");
        assertThat(count("SELECT COUNT(*) FROM audit_log WHERE event_type='NAV_RECONCILIATION_REQUIRED'"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM portfolio_external_cashflow_event WHERE amount=1000"))
                .isZero();
    }
}
