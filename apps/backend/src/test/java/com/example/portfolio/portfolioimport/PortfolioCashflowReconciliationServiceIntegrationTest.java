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
    void requiresOwnerConfirmationWithoutReliableExecutionEvidence() throws Exception {
        var confirmed =
                confirm(uuid(preview("fidelity-positions.csv"), "batchId"), 0, "[{\"rowNumber\":7,\"ignored\":true}]");
        var userId = jdbc.sql("SELECT BIN_TO_UUID(id) FROM app_user WHERE email=:email")
                .param("email", EMAIL)
                .query(UUID.class)
                .single();

        var cashOnlyBefore = reconciliation.before(userId);
        update(
                "UPDATE broker_cash_snapshot SET cash_amount=cash_amount+7000,data_as_of=DATE_ADD(data_as_of,INTERVAL 1 SECOND) "
                        + "WHERE user_id=UUID_TO_BIN('" + userId + "')");
        var required = reconciliation.reconcile(userId, uuid(confirmed, "batchId"), cashOnlyBefore);

        assertThat(required.status()).isEqualTo("REQUIRED");
        assertThat(required.cashChange()).isEqualByComparingTo("7000");
        var recorded = reconciliation.confirm(
                EMAIL,
                uuid(confirmed, "batchId"),
                PortfolioCashflowReconciliationService.ConfirmationType.EXTERNAL_CASHFLOW);
        assertThat(recorded.status()).isEqualTo("RECORDED_EXTERNAL_CASHFLOW");
        assertThat(count("SELECT COUNT(*) FROM portfolio_external_cashflow_event WHERE amount=7000 "
                        + "AND source='USER_CONFIRMED_BROKER_CASHFLOW'"))
                .isEqualTo(1);
        assertThat(jdbc.sql(
                                "SELECT effective_date FROM portfolio_external_cashflow_event WHERE amount=7000 AND source='USER_CONFIRMED_BROKER_CASHFLOW'")
                        .query(java.time.LocalDate.class)
                        .single())
                .isEqualTo(jdbc.sql(
                                "SELECT DATE(COALESCE(data_as_of,created_at)) FROM portfolio_import_batch WHERE id=UUID_TO_BIN(:id)")
                        .param("id", uuid(confirmed, "batchId").toString())
                        .query(java.time.LocalDate.class)
                        .single());

        var internalBefore = reconciliation.before(userId);
        update(
                "UPDATE broker_cash_snapshot SET cash_amount=cash_amount-120,data_as_of=DATE_ADD(data_as_of,INTERVAL 1 SECOND) "
                        + "WHERE user_id=UUID_TO_BIN('" + userId + "')");
        update("UPDATE position SET quantity=quantity+1,market_value=market_value+120 "
                + "WHERE id=(SELECT id FROM (SELECT p.id FROM position p "
                + "JOIN investment_account a ON a.id=p.account_id WHERE a.user_id=UUID_TO_BIN('" + userId
                + "') AND p.status='OPEN' ORDER BY p.id LIMIT 1) selected_position)");
        var internal = reconciliation.reconcile(userId, uuid(confirmed, "batchId"), internalBefore);

        assertThat(internal.status()).isEqualTo("REQUIRED");
        assertThat(count("SELECT COUNT(*) FROM portfolio_external_cashflow_event WHERE amount=-120"))
                .isZero();
    }
}
