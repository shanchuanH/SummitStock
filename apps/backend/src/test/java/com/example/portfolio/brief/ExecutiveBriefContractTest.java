package com.example.portfolio.brief;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.portfolio.MySqlIntegrationTest;
import com.example.portfolio.analysis.mark.PositionMarkService;
import com.example.portfolio.market.provider.TradingCalendar;
import java.time.Clock;
import java.util.UUID;
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

    @Autowired
    private PositionMarkService positionMarks;

    @Autowired
    private TradingCalendar tradingCalendar;

    @Autowired
    private Clock clock;

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
                .andExpect(jsonPath("$.confirmedNoAction").value(false))
                .andExpect(jsonPath("$.headline").value("No portfolio has been imported."))
                .andExpect(jsonPath("$.summary.investedValue").value("0"))
                .andExpect(jsonPath("$.summary.trackedCash").value("0"))
                .andExpect(jsonPath("$.summary.openPositions").value(0))
                .andExpect(jsonPath("$.market.regime").isNotEmpty())
                .andExpect(jsonPath("$.capital.investableAssets").value("0"))
                .andExpect(jsonPath("$.portfolio.openRisk").value("0"))
                .andExpect(jsonPath("$.mustAct.length()").value(0))
                .andExpect(jsonPath("$.doNot.length()").value(0))
                .andExpect(jsonPath("$.watch.length()").value(0))
                .andExpect(jsonPath("$.opportunities.length()").value(0))
                .andExpect(jsonPath("$.blocked.length()").value(0))
                .andExpect(jsonPath("$.todayPriorities.length()").value(0))
                .andExpect(jsonPath("$.topRisks.length()").value(1))
                .andExpect(jsonPath("$.allHoldings.length()").value(0))
                .andExpect(jsonPath("$.dataReadiness.status").value("NOT_READY"))
                .andExpect(jsonPath("$.dataReadiness.marketCoverage").value("1"))
                .andExpect(jsonPath("$.dataReadiness.completeness").value("1"));
    }

    @Test
    void returnsReadyActionsAndCashFromPersistedPortfolioEvidence() throws Exception {
        seedReadyPortfolio();

        mockMvc.perform(get("/api/v1/brief/today").with(httpBasic("brief-contract@example.local", "change-before-use")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ANALYSIS_READY"))
                .andExpect(jsonPath("$.confirmedNoAction").value(false))
                .andExpect(jsonPath("$.headline").value("1 item(s) require action; 0 item(s) require watching."))
                .andExpect(jsonPath("$.summary.investedValue").value("10100"))
                .andExpect(jsonPath("$.summary.trackedCash").value("14000"))
                .andExpect(jsonPath("$.summary.emergencyCash").value("10000"))
                .andExpect(jsonPath("$.summary.tacticalReserve").value("4000"))
                .andExpect(jsonPath("$.capital.totalLiquidAssets").value("24100"))
                .andExpect(jsonPath("$.capital.emergencyReserve").value("10000"))
                .andExpect(jsonPath("$.capital.deployableCash").value("4000"))
                .andExpect(jsonPath("$.capital.investableAssets").value("14100"))
                .andExpect(jsonPath("$.mustAct.length()").value(1))
                .andExpect(jsonPath("$.mustAct[0].symbol").value("BRFT"))
                .andExpect(jsonPath("$.mustAct[0].companyName").value("Brief Fund"))
                .andExpect(jsonPath("$.todayPriorities.length()").value(1))
                .andExpect(jsonPath("$.topRisks.length()").value(1))
                .andExpect(jsonPath("$.topRisks[0].risk").value("Market concentration"))
                .andExpect(jsonPath("$.topRisks[0].meaning").isNotEmpty())
                .andExpect(jsonPath("$.topRisks[0].nowAction").isNotEmpty())
                .andExpect(jsonPath("$.allHoldings.length()").value(1))
                .andExpect(jsonPath("$.allHoldings[0].priority").value("MUST_ACT"))
                .andExpect(jsonPath("$.summary.tacticalSpeculativeExposureFraction")
                        .value("0"))
                .andExpect(jsonPath("$.dataReadiness.status").value("HEALTHY"))
                .andExpect(jsonPath("$.dataReadiness.marketCoverage").value("1"));
    }

    @Test
    void confirmsNoActionOnlyWhenReadyCoverageIsHealthyAndEveryActionQueueIsEmpty() throws Exception {
        seedReadyPortfolio();
        update("DELETE FROM recommendation WHERE user_id=UUID_TO_BIN('" + USER_ID + "')");

        mockMvc.perform(get("/api/v1/brief/today").with(httpBasic("brief-contract@example.local", "change-before-use")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ANALYSIS_READY"))
                .andExpect(jsonPath("$.confirmedNoAction").value(true))
                .andExpect(jsonPath("$.mustAct.length()").value(0))
                .andExpect(jsonPath("$.doNot.length()").value(0))
                .andExpect(jsonPath("$.watch.length()").value(0))
                .andExpect(jsonPath("$.blocked.length()").value(0))
                .andExpect(jsonPath("$.dataReadiness.status").value("HEALTHY"));
    }

    @Test
    void activeLatestRunForcesUpdatingAndNeverConfirmsNoAction() throws Exception {
        seedReadyPortfolio();
        update("DELETE FROM recommendation WHERE user_id=UUID_TO_BIN('" + USER_ID + "')");
        update(
                "INSERT INTO portfolio_analysis_run (id,user_id,market_date,strategy_version,status,run_key,created_at,updated_at,version) "
                        + "VALUES (UUID_TO_BIN('71000000-0000-0000-0000-000000000020'),UUID_TO_BIN('" + USER_ID
                        + "'),CURRENT_DATE,'3.0.0-draft','RUNNING','brief:updating',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)");

        mockMvc.perform(get("/api/v1/brief/today").with(httpBasic("brief-contract@example.local", "change-before-use")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("UPDATING"))
                .andExpect(jsonPath("$.confirmedNoAction").value(false))
                .andExpect(jsonPath("$.recommendationNotice").isNotEmpty());
    }

    @Test
    void tacticalSpeculativeExposureUsesCanonicalClassificationsAndStrategyNav() throws Exception {
        seedReadyPortfolio();
        for (var classification :
                java.util.List.of("TACTICAL_STOCK", "CYCLICAL_TACTICAL", "TURNAROUND_TACTICAL", "SPECULATIVE")) {
            update("UPDATE position SET classification='" + classification + "' WHERE id=UUID_TO_BIN('" + POSITION_ID
                    + "')");
            mockMvc.perform(get("/api/v1/brief/today")
                            .with(httpBasic("brief-contract@example.local", "change-before-use")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.summary.tacticalSpeculativeExposureFraction")
                            .value("0.7163120567"));
        }
    }

    @Test
    void protectedEmergencyAmountAboveFloorIsExcludedConsistently() throws Exception {
        seedReadyPortfolio();
        update("UPDATE cash_bucket SET current_amount=25000 WHERE user_id=UUID_TO_BIN('" + USER_ID
                + "') AND bucket_type='EMERGENCY'");

        mockMvc.perform(get("/api/v1/brief/today").with(httpBasic("brief-contract@example.local", "change-before-use")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capital.requiredEmergencyFloor").value("20000"))
                .andExpect(jsonPath("$.capital.emergencyReserve").value("25000"))
                .andExpect(jsonPath("$.capital.investableAssets").value("14100"));
    }

    private void seedReadyPortfolio() {
        update(
                """
                INSERT INTO instrument (id, symbol, exchange, asset_type, currency, active, metadata, created_at, updated_at, version)
                VALUES (UUID_TO_BIN('%s'), 'BRFT', 'TEST', 'ETF', 'USD', TRUE, JSON_OBJECT('name','Brief Fund'), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
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
        jdbc.sql(
                        "UPDATE price_bar SET market_date=:marketDate WHERE id=UUID_TO_BIN('71000000-0000-0000-0000-000000000012')")
                .param("marketDate", tradingCalendar.latestCompletedSession(clock.instant()))
                .update();
        positionMarks.captureForUser(UUID.fromString(USER_ID), clock.instant());
        update(
                """
                INSERT INTO holding_analysis_snapshot (id, position_id, strategy_version, analysis_status, confidence, current_weight, exact_quantity_allowed, reasons, risks, change_conditions, rule_ids, evidence_checksum, data_as_of, valid_until, created_at)
                VALUES (UUID_TO_BIN('%s'), UUID_TO_BIN('%s'), '3.0.0-draft', 'READY', 'HIGH', 1, TRUE, JSON_ARRAY('ready'), JSON_ARRAY(), JSON_ARRAY(), JSON_ARRAY('BRIEF.READY.001'), REPEAT('8',64), UTC_TIMESTAMP(6), '2099-01-01', UTC_TIMESTAMP(6))
                """
                        .formatted(ANALYSIS_ID, POSITION_ID));
        update(
                """
                INSERT INTO recommendation (id, user_id, position_id, holding_analysis_id, strategy_version, action, priority, confidence, reasons, risks, change_conditions, rule_ids, evidence_checksum, data_as_of, valid_until, status, created_at)
                VALUES (UUID_TO_BIN('71000000-0000-0000-0000-000000000013'), UUID_TO_BIN('%s'), UUID_TO_BIN('%s'), UUID_TO_BIN('%s'), '3.0.0-draft', 'REVIEW', 'MUST_ACT', 'HIGH', JSON_ARRAY('review'), JSON_ARRAY('Market concentration'), JSON_ARRAY('Concentration falls below the configured limit'), JSON_ARRAY('BRIEF.ACTION.001'), REPEAT('9',64), UTC_TIMESTAMP(6), '2099-01-01', 'ACTIVE', UTC_TIMESTAMP(6))
                """
                        .formatted(USER_ID, POSITION_ID, ANALYSIS_ID));
    }

    private void cleanContractData() {
        update("DELETE FROM portfolio_analysis_run WHERE user_id=UUID_TO_BIN('" + USER_ID + "')");
        update("DELETE FROM recommendation WHERE user_id=UUID_TO_BIN('" + USER_ID + "')");
        update("DELETE FROM holding_analysis_snapshot WHERE position_id=UUID_TO_BIN('" + POSITION_ID + "')");
        update("DELETE FROM position_mark_snapshot WHERE position_id=UUID_TO_BIN('" + POSITION_ID + "')");
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
