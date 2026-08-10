package com.example.portfolio.brief;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.portfolio.MySqlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "portfolio.security.dev-user=brief-contract@example.local")
@AutoConfigureMockMvc
class ExecutiveBriefContractTest extends MySqlIntegrationTest {
    private static final String USER_ID = "71000000-0000-0000-0000-000000000001";
    private static final String ACCOUNT_ID = "71000000-0000-0000-0000-000000000002";
    private static final String INSTRUMENT_ID = "71000000-0000-0000-0000-000000000003";
    private static final String POSITION_ID = "71000000-0000-0000-0000-000000000004";
    private static final String ANALYSIS_ID = "71000000-0000-0000-0000-000000000005";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void createContractUser() {
        cleanContractData();
        update(
                """
                INSERT INTO app_user (id, email, password_hash, status, timezone, created_at, updated_at, version)
                VALUES (UUID_TO_BIN('%s'), 'brief-contract@example.local', 'unused', 'ACTIVE', 'UTC', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """
                        .formatted(USER_ID));
    }

    @AfterEach
    void cleanUp() {
        cleanContractData();
    }

    @Test
    void returnsOneBackendOwnedBriefWithDecimalStringsAndExplicitReadiness() throws Exception {
        mockMvc.perform(get("/api/v1/brief/today").with(httpBasic("brief-contract@example.local", "change-before-use")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NO_PORTFOLIO"))
                .andExpect(jsonPath("$.headline").value("No portfolio has been imported."))
                .andExpect(jsonPath("$.summary.investedValue").value("0"))
                .andExpect(jsonPath("$.summary.trackedCash").value("0"))
                .andExpect(jsonPath("$.summary.openPositions").value(0))
                .andExpect(jsonPath("$.market.regime").value("UNKNOWN"))
                .andExpect(jsonPath("$.capital.investableAssets").value("0"))
                .andExpect(jsonPath("$.portfolio.openRisk").value("0"))
                .andExpect(jsonPath("$.mustAct.length()").value(0))
                .andExpect(jsonPath("$.doNot.length()").value(0))
                .andExpect(jsonPath("$.watch.length()").value(0))
                .andExpect(jsonPath("$.opportunities.length()").value(0))
                .andExpect(jsonPath("$.blocked.length()").value(0))
                .andExpect(jsonPath("$.dataReadiness.status").value("NOT_READY"))
                .andExpect(jsonPath("$.dataReadiness.marketCoverage").value("1"));
    }

    @Test
    void returnsReadyActionsAndCashFromPersistedPortfolioEvidence() throws Exception {
        seedReadyPortfolio();

        mockMvc.perform(get("/api/v1/brief/today").with(httpBasic("brief-contract@example.local", "change-before-use")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ANALYSIS_READY"))
                .andExpect(jsonPath("$.headline").value("1 item(s) require action; 0 item(s) require watching."))
                .andExpect(jsonPath("$.summary.investedValue").value("102937"))
                .andExpect(jsonPath("$.summary.trackedCash").value("14000"))
                .andExpect(jsonPath("$.summary.emergencyCash").value("10000"))
                .andExpect(jsonPath("$.summary.tacticalReserve").value("4000"))
                .andExpect(jsonPath("$.capital.totalLiquidAssets").value("116937"))
                .andExpect(jsonPath("$.capital.emergencyReserve").value("10000"))
                .andExpect(jsonPath("$.capital.deployableCash").value("4000"))
                .andExpect(jsonPath("$.capital.investableAssets").value("106937"))
                .andExpect(jsonPath("$.mustAct.length()").value(1))
                .andExpect(jsonPath("$.mustAct[0].symbol").value("BRFT"))
                .andExpect(jsonPath("$.dataReadiness.status").value("HEALTHY"))
                .andExpect(jsonPath("$.dataReadiness.marketCoverage").value("1"));
    }

    private void seedReadyPortfolio() {
        update(
                """
                INSERT INTO instrument (id, symbol, exchange, asset_type, currency, active, metadata, created_at, updated_at, version)
                VALUES (UUID_TO_BIN('%s'), 'BRFT', 'TEST', 'ETF', 'USD', TRUE, JSON_OBJECT(), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """
                        .formatted(INSTRUMENT_ID));
        update(
                """
                INSERT INTO investment_account (id, user_id, account_key, institution, account_type, display_name, currency, active, created_at, updated_at, version)
                VALUES (UUID_TO_BIN('%s'), UUID_TO_BIN('%s'), 'brief-account', 'Test', 'BROKERAGE', 'Brief account', 'USD', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """
                        .formatted(ACCOUNT_ID, USER_ID));
        update(
                """
                INSERT INTO cash_bucket (id, user_id, bucket_type, target_amount, current_amount, currency, as_of, updated_at, version)
                VALUES (UUID_TO_BIN('71000000-0000-0000-0000-000000000010'), UUID_TO_BIN('%s'), 'EMERGENCY', 20000, 10000, 'USD', CURRENT_DATE, UTC_TIMESTAMP(6), 0),
                       (UUID_TO_BIN('71000000-0000-0000-0000-000000000011'), UUID_TO_BIN('%s'), 'TACTICAL_RESERVE', 4000, 4000, 'USD', CURRENT_DATE, UTC_TIMESTAMP(6), 0)
                """
                        .formatted(USER_ID, USER_ID));
        update(
                """
                INSERT INTO position (id, account_id, instrument_id, bucket, classification, classification_confirmed, quantity, average_cost, market_value, status, opened_at, created_at, updated_at, version)
                VALUES (UUID_TO_BIN('%s'), UUID_TO_BIN('%s'), UUID_TO_BIN('%s'), 'CORE', 'CORE_BROAD_ETF', TRUE, 100, 1000, 102937, 'OPEN', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """
                        .formatted(POSITION_ID, ACCOUNT_ID, INSTRUMENT_ID));
        update(
                """
                INSERT INTO price_bar (id, instrument_id, timeframe, bar_start, market_date, open_price, high_price, low_price, close_price, volume, adjusted, provider, source_timestamp, checksum, normalization_version, quality_status, data_as_of, created_at)
                VALUES (UUID_TO_BIN('71000000-0000-0000-0000-000000000012'), UUID_TO_BIN('%s'), '1D', UTC_TIMESTAMP(6), CURRENT_DATE, 100, 102, 99, 101, 1000, TRUE, 'BRIEF_TEST', UTC_TIMESTAMP(6), REPEAT('7',64), 'v1', 'HEALTHY', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """
                        .formatted(INSTRUMENT_ID));
        update(
                """
                INSERT INTO holding_analysis_snapshot (id, position_id, strategy_version, analysis_status, confidence, current_weight, exact_quantity_allowed, reasons, risks, change_conditions, rule_ids, evidence_checksum, data_as_of, valid_until, created_at)
                VALUES (UUID_TO_BIN('%s'), UUID_TO_BIN('%s'), '1.0.0-draft', 'READY', 'HIGH', 1, TRUE, JSON_ARRAY('ready'), JSON_ARRAY(), JSON_ARRAY(), JSON_ARRAY('BRIEF.READY.001'), REPEAT('8',64), UTC_TIMESTAMP(6), '2099-01-01', UTC_TIMESTAMP(6))
                """
                        .formatted(ANALYSIS_ID, POSITION_ID));
        update(
                """
                INSERT INTO recommendation (id, user_id, position_id, holding_analysis_id, strategy_version, action, priority, confidence, reasons, risks, change_conditions, rule_ids, evidence_checksum, data_as_of, valid_until, status, created_at)
                VALUES (UUID_TO_BIN('71000000-0000-0000-0000-000000000013'), UUID_TO_BIN('%s'), UUID_TO_BIN('%s'), UUID_TO_BIN('%s'), '1.0.0-draft', 'REVIEW', 'MUST_ACT', 'HIGH', JSON_ARRAY('review'), JSON_ARRAY(), JSON_ARRAY(), JSON_ARRAY('BRIEF.ACTION.001'), REPEAT('9',64), UTC_TIMESTAMP(6), '2099-01-01', 'ACTIVE', UTC_TIMESTAMP(6))
                """
                        .formatted(USER_ID, POSITION_ID, ANALYSIS_ID));
    }

    private void cleanContractData() {
        update("DELETE FROM recommendation WHERE user_id=UUID_TO_BIN('" + USER_ID + "')");
        update("DELETE FROM holding_analysis_snapshot WHERE position_id=UUID_TO_BIN('" + POSITION_ID + "')");
        update("DELETE FROM price_bar WHERE instrument_id=UUID_TO_BIN('" + INSTRUMENT_ID + "')");
        update("DELETE FROM position WHERE id=UUID_TO_BIN('" + POSITION_ID + "')");
        update("DELETE FROM cash_bucket WHERE user_id=UUID_TO_BIN('" + USER_ID + "')");
        update("DELETE FROM investment_account WHERE id=UUID_TO_BIN('" + ACCOUNT_ID + "')");
        update("DELETE FROM instrument WHERE id=UUID_TO_BIN('" + INSTRUMENT_ID + "')");
        update("DELETE FROM app_user WHERE id=UUID_TO_BIN('" + USER_ID + "')");
    }

    private void update(String sql) {
        jdbc.sql(sql).update();
    }
}
