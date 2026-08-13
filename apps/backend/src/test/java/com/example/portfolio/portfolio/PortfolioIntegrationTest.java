package com.example.portfolio.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.portfolio.MySqlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PortfolioIntegrationTest extends MySqlIntegrationTest {
    private static final String OWNER = "11111111-1111-1111-1111-111111111111";
    private static final String OTHER = "22222222-2222-2222-2222-222222222222";
    private static final String OWNER_ACCOUNT = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String OTHER_ACCOUNT = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
    private static final String OWNER_POSITION = "cccccccc-cccc-cccc-cccc-cccccccccccc";
    private static final String OTHER_POSITION = "dddddddd-dddd-dddd-dddd-dddddddddddd";
    private static final String SPY = "00000000-0000-0000-0000-000000000101";
    private static final String QQQ = "00000000-0000-0000-0000-000000000102";

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PortfolioStore store;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void seedIsolatedPortfolios() {
        removeSeededPortfolios();
        update(
                """
                INSERT INTO app_user (id, email, password_hash, status, timezone, created_at, updated_at, version)
                VALUES (UUID_TO_BIN('%s'), 'admin@example.local', 'unused', 'ACTIVE', 'UTC', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
                       (UUID_TO_BIN('%s'), 'other@example.local', 'unused', 'ACTIVE', 'UTC', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """
                        .formatted(OWNER, OTHER));
        update(
                """
                INSERT INTO investment_account (id, user_id, account_key, institution, account_type, display_name, currency, active, created_at, updated_at, version)
                VALUES (UUID_TO_BIN('%s'), UUID_TO_BIN('%s'), 'owner-brokerage', 'Example', 'BROKERAGE', 'Primary', 'USD', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
                       (UUID_TO_BIN('%s'), UUID_TO_BIN('%s'), 'other-brokerage', 'Example', 'BROKERAGE', 'Other', 'USD', TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """
                        .formatted(OWNER_ACCOUNT, OWNER, OTHER_ACCOUNT, OTHER));
        update(
                """
                INSERT INTO equity_snapshot (id, account_id, total_equity, cash_balance, provider, checksum, quality_status, data_as_of, created_at)
                VALUES (UUID_TO_BIN('10000000-0000-0000-0000-000000000001'), UUID_TO_BIN('%s'), 12500.12, 2500.12, 'TEST', REPEAT('a',64), 'HEALTHY', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """
                        .formatted(OWNER_ACCOUNT));
        update(
                """
                INSERT INTO position (id, account_id, instrument_id, bucket, classification, classification_confirmed, quantity, average_cost, market_value, status, opened_at, created_at, updated_at, version)
                VALUES (UUID_TO_BIN('%s'), UUID_TO_BIN('%s'), UUID_TO_BIN('%s'), 'CORE', 'UNKNOWN', FALSE, 10.125, 400.25, 5000.125, 'OPEN', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
                       (UUID_TO_BIN('%s'), UUID_TO_BIN('%s'), UUID_TO_BIN('%s'), 'CORE', 'CORE_TECH_ETF', TRUE, 2, 450, 1000, 'OPEN', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """
                        .formatted(OWNER_POSITION, OWNER_ACCOUNT, SPY, OTHER_POSITION, OTHER_ACCOUNT, QQQ));
        update(
                """
                INSERT INTO position_mark_snapshot (id,position_id,instrument_id,quantity,decision_price,
                  marked_market_value,market_date,source_provider,quality_status,price_data_as_of,
                  strategy_version,strategy_config_hash,evidence_checksum,data_as_of,created_at) VALUES
                (UUID_TO_BIN('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee1'),UUID_TO_BIN('%s'),UUID_TO_BIN('%s'),
                  10.125,493.83950617,5000.125,CURRENT_DATE,'TEST_FIXTURE','HEALTHY',UTC_TIMESTAMP(6),
                  'test',REPEAT('e',64),SHA2('owner-mark',256),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
                (UUID_TO_BIN('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeee2'),UUID_TO_BIN('%s'),UUID_TO_BIN('%s'),
                  2,500,1000,CURRENT_DATE,'TEST_FIXTURE','HEALTHY',UTC_TIMESTAMP(6),
                  'test',REPEAT('e',64),SHA2('other-mark',256),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """
                        .formatted(OWNER_POSITION, SPY, OTHER_POSITION, QQQ));
        for (int index = 1; index <= 4; index++) {
            recommendation("20000000-0000-0000-0000-00000000000" + index, "MUST_ACT", "REVIEW_" + index, index);
        }
        recommendation("20000000-0000-0000-0000-000000000005", "DO_NOT", "DO_NOT_AVERAGE", 5);
        recommendation("20000000-0000-0000-0000-000000000006", "WATCH", "WATCH_RISK", 6);
    }

    @AfterEach
    void removeSeededPortfolios() {
        update(
                "DELETE bm FROM backtest_metric bm JOIN backtest_run br ON br.id=bm.backtest_run_id WHERE br.user_id IN (UUID_TO_BIN('"
                        + OWNER + "'), UUID_TO_BIN('" + OTHER + "'))");
        update("DELETE FROM backtest_run WHERE user_id IN (UUID_TO_BIN('" + OWNER + "'), UUID_TO_BIN('" + OTHER
                + "'))");
        update(
                "DELETE ra FROM recommendation_acknowledgement ra JOIN app_user u ON u.id=ra.user_id WHERE u.email IN ('admin@example.local','other@example.local')");
        update("DELETE FROM etf_dip_tranche WHERE user_id IN (UUID_TO_BIN('" + OWNER + "'), UUID_TO_BIN('" + OTHER
                + "'))");
        update("DELETE FROM etf_dip_event WHERE user_id IN (UUID_TO_BIN('" + OWNER + "'), UUID_TO_BIN('" + OTHER
                + "'))");
        update("DELETE FROM cashflow_allocation_snapshot WHERE user_id IN (UUID_TO_BIN('" + OWNER + "'), UUID_TO_BIN('"
                + OTHER + "'))");
        update("DELETE FROM active_sleeve_review_snapshot WHERE user_id IN (UUID_TO_BIN('" + OWNER + "'), UUID_TO_BIN('"
                + OTHER + "'))");
        update(
                "DELETE ts FROM thesis_source ts JOIN position_thesis t ON t.id = ts.thesis_id WHERE t.position_id IN (UUID_TO_BIN('"
                        + OWNER_POSITION + "'), UUID_TO_BIN('" + OTHER_POSITION + "'))");
        update("DELETE FROM stop_alert WHERE position_id IN (UUID_TO_BIN('" + OWNER_POSITION + "'), UUID_TO_BIN('"
                + OTHER_POSITION + "'))");
        update("DELETE FROM stop_snapshot WHERE position_id IN (UUID_TO_BIN('" + OWNER_POSITION + "'), UUID_TO_BIN('"
                + OTHER_POSITION + "'))");
        update("DELETE FROM valuation_snapshot WHERE position_id IN (UUID_TO_BIN('" + OWNER_POSITION
                + "'), UUID_TO_BIN('" + OTHER_POSITION + "'))");
        update("DELETE FROM earnings_risk_snapshot WHERE position_id IN (UUID_TO_BIN('" + OWNER_POSITION
                + "'), UUID_TO_BIN('" + OTHER_POSITION + "'))");
        update("DELETE FROM trade_journal WHERE position_id IN (UUID_TO_BIN('" + OWNER_POSITION + "'), UUID_TO_BIN('"
                + OTHER_POSITION + "'))");
        update("DELETE FROM position_thesis WHERE position_id IN (UUID_TO_BIN('" + OWNER_POSITION + "'), UUID_TO_BIN('"
                + OTHER_POSITION + "'))");
        update("DELETE FROM audit_log WHERE user_id IN (UUID_TO_BIN('" + OWNER + "'), UUID_TO_BIN('" + OTHER + "'))");
        update("DELETE FROM recommendation WHERE user_id IN (UUID_TO_BIN('" + OWNER + "'), UUID_TO_BIN('" + OTHER
                + "'))");
        update("DELETE FROM portfolio_drawdown_snapshot WHERE user_id IN (UUID_TO_BIN('" + OWNER + "'), UUID_TO_BIN('"
                + OTHER + "'))");
        update("DELETE FROM position_mark_snapshot WHERE position_id IN (UUID_TO_BIN('" + OWNER_POSITION
                + "'), UUID_TO_BIN('" + OTHER_POSITION + "'))");
        update("DELETE FROM position WHERE account_id IN (UUID_TO_BIN('" + OWNER_ACCOUNT + "'), UUID_TO_BIN('"
                + OTHER_ACCOUNT + "'))");
        update("DELETE FROM equity_snapshot WHERE account_id IN (UUID_TO_BIN('" + OWNER_ACCOUNT + "'), UUID_TO_BIN('"
                + OTHER_ACCOUNT + "'))");
        update("DELETE FROM investment_account WHERE id IN (UUID_TO_BIN('" + OWNER_ACCOUNT + "'), UUID_TO_BIN('"
                + OTHER_ACCOUNT + "'))");
        update("DELETE FROM app_user WHERE id IN (UUID_TO_BIN('" + OWNER + "'), UUID_TO_BIN('" + OTHER + "'))");
    }

    @Test
    void portfolioReadsAreOwnerScopedAndDecimalsAreStrings() throws Exception {
        assertThat(store.positions("admin@example.local"))
                .extracting(PortfolioStore.PositionView::symbol)
                .containsExactly("SPY");
        assertThat(store.positions("other@example.local"))
                .extracting(PortfolioStore.PositionView::symbol)
                .containsExactly("QQQ");

        mockMvc.perform(get("/api/v1/positions").with(httpBasic("admin@example.local", "change-before-use")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].symbol").value("SPY"))
                .andExpect(jsonPath("$[0].quantity").value("10.125"))
                .andExpect(jsonPath("$[1]").doesNotExist());
        mockMvc.perform(get("/api/v1/portfolio/summary").with(httpBasic("admin@example.local", "change-before-use")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.investedValue").value("5000.125"));
    }

    @Test
    void classificationConfirmationIsVersionedAndAudited() throws Exception {
        var body = "{\"classification\":\"CORE_BROAD_ETF\",\"expectedVersion\":0}";
        mockMvc.perform(post("/api/v1/positions/{id}/classify", OWNER_POSITION)
                        .with(httpBasic("admin@example.local", "change-before-use"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classification").value("CORE_BROAD_ETF"))
                .andExpect(jsonPath("$.bucket").value("CORE"))
                .andExpect(jsonPath("$.classificationConfirmed").value(true))
                .andExpect(jsonPath("$.version").value(1));
        assertThat(jdbc.sql(
                                "SELECT COUNT(*) FROM audit_log WHERE entity_id = :id AND event_type = 'POSITION_CLASSIFIED'")
                        .param("id", OWNER_POSITION)
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
        mockMvc.perform(post("/api/v1/positions/{id}/classify", OWNER_POSITION)
                        .with(httpBasic("admin@example.local", "change-before-use"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void classificationConfirmationSynchronizesTheStrategySleeveBucket() throws Exception {
        mockMvc.perform(post("/api/v1/positions/{id}/classify", OWNER_POSITION)
                        .with(httpBasic("admin@example.local", "change-before-use"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classification\":\"QUALITY_STOCK\",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classification").value("QUALITY_STOCK"))
                .andExpect(jsonPath("$.bucket").value("TACTICAL_OVERLAY"));
    }

    @Test
    void classifierDoesNotInventQualityAndStalePreviewBlocksExactQuantity() throws Exception {
        mockMvc.perform(get("/api/v1/positions/{id}/classification-suggestion", OWNER_POSITION)
                        .with(httpBasic("admin@example.local", "change-before-use")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionId").value(OWNER_POSITION))
                .andExpect(jsonPath("$.symbol").value("SPY"))
                .andExpect(jsonPath("$.classification").value("CORE_BROAD_ETF"))
                .andExpect(jsonPath("$.source").value("SYSTEM_RULE"))
                .andExpect(jsonPath("$.confirmationRequired").value(true));

        mockMvc.perform(
                        post("/api/v1/trade-plans/preview")
                                .with(httpBasic("admin@example.local", "change-before-use"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"classification":"QUALITY_STOCK","classificationConfirmed":true,
                                 "currentWeight":0.08,"projectedWeight":0.09,"proposedTradeRisk":0.002,
                                 "currentOpenStockRisk":0.005,"currentClusterRisk":0.002,
                                 "averagingDown":false,"thesisImproving":false,"anchoredToCostBasis":false,
                                 "quality":"STALE"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.preciseQuantityAllowed").value(false))
                .andExpect(jsonPath("$.ruleIds[0]").value("DATA.STALE.002"));
    }

    @Test
    void todayActionsCapsMustActAtThree() throws Exception {
        mockMvc.perform(get("/api/v1/actions/today").with(httpBasic("admin@example.local", "change-before-use")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustAct.length()").value(3))
                .andExpect(jsonPath("$.doNot.length()").value(1))
                .andExpect(jsonPath("$.watch.length()").value(1));
    }

    @Test
    void positionIntelligenceIsOwnerScopedAndThesisConfirmationIsVersioned() throws Exception {
        seedPositionIntelligence();
        mockMvc.perform(get("/api/v1/positions/{id}/intelligence", OWNER_POSITION)
                        .with(httpBasic("admin@example.local", "change-before-use")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stop.liveStop").value("94"))
                .andExpect(jsonPath("$.thesis.status").value("HEALTHY"))
                .andExpect(jsonPath("$.valuation.action").value("ADD_1_PERCENT_STARTER"))
                .andExpect(jsonPath("$.earnings.eventCount").value(10))
                .andExpect(jsonPath("$.journal[0].realizedR").value("1.5"));
        mockMvc.perform(get("/api/v1/positions/{id}/intelligence", OTHER_POSITION)
                        .with(httpBasic("admin@example.local", "change-before-use")))
                .andExpect(status().isNotFound());

        var body = "{\"expectedVersion\":0}";
        mockMvc.perform(post("/api/v1/positions/{id}/thesis/confirm", OWNER_POSITION)
                        .with(httpBasic("admin@example.local", "change-before-use"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userConfirmed").value(true))
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(post("/api/v1/positions/{id}/thesis/confirm", OWNER_POSITION)
                        .with(httpBasic("admin@example.local", "change-before-use"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void stopPreviewIsMonotonicAndCoreEtfIsExempt() throws Exception {
        var quality =
                """
                {"classification":"QUALITY_STOCK","entry":100,"confirmedSwingLow":92,"atr":4,
                 "previousLiveStop":94,"chandelier":93,"ema20":96,"confirmedHigherLow":95,"dailyClose":93}
                """;
        mockMvc.perform(post("/api/v1/positions/{id}/stops/preview", OWNER_POSITION)
                        .with(httpBasic("admin@example.local", "change-before-use"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quality))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.initialStop").value("90"))
                .andExpect(jsonPath("$.liveStop").value("94"))
                .andExpect(jsonPath("$.closeConfirmed").value(true));
        mockMvc.perform(post("/api/v1/positions/{id}/stops/preview", OWNER_POSITION)
                        .with(httpBasic("admin@example.local", "change-before-use"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(quality.replace("QUALITY_STOCK", "CORE_BROAD_ETF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ordinaryStopApplicable").value(false));
    }

    @Test
    void stopAlertsAreDeduplicatedPerPositionTypeAndMarketDate() {
        seedPositionIntelligence();
        var insert =
                """
                INSERT INTO stop_alert (id, position_id, stop_snapshot_id, event_type, market_date, observed_price, created_at)
                VALUES (UUID_TO_BIN('%s'), UUID_TO_BIN('%s'), UUID_TO_BIN('30000000-0000-0000-0000-000000000001'), 'SOFT_ALERT', '2026-08-05', 94, UTC_TIMESTAMP(6))
                """;
        update(insert.formatted("30000000-0000-0000-0000-000000000002", OWNER_POSITION));
        assertThatThrownBy(() -> update(insert.formatted("30000000-0000-0000-0000-000000000003", OWNER_POSITION)))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @Test
    void dipAndCashflowPreviewsEnforceMarketTriggersCooldownAndEmergencyFirst() throws Exception {
        var dip =
                """
                {"portfolioDrawdown":0.15,"marketDriven":true,"completeData":true,"emergencyCashProtected":true,
                 "drawdownScore":1,"vixPercentileScore":1,"breadthOversoldScore":1,"creditStressScore":1,"volTermScore":1,"trendContextScore":1,
                 "rsiCross40":true,"breakout5Day":true,"aboveEma20":false,"breadthImproving":false,"vixFalling":false,"creditStable":false,
                 "completedTranches":0,"tradingDaysSinceLastTranche":5}
                """;
        mockMvc.perform(post("/api/v1/etf-dip/preview")
                        .with(httpBasic("admin@example.local", "change-before-use"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dip))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("DEPLOY_TRANCHE"))
                .andExpect(jsonPath("$.reserveFraction").value("0.2"))
                .andExpect(jsonPath("$.executionSubmitted").value(false));
        mockMvc.perform(post("/api/v1/etf-dip/preview")
                        .with(httpBasic("admin@example.local", "change-before-use"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dip.replace("\"marketDriven\":true", "\"marketDriven\":false")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("WATCH"));
        mockMvc.perform(
                        post("/api/v1/cashflow/plan")
                                .with(httpBasic("admin@example.local", "change-before-use"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"monthlyTakeHome\":11000,\"monthlyExpenses\":4000,\"emergencyCash\":18000,\"qualitySignal\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emergency").value("2000"))
                .andExpect(jsonPath("$.broadCore").value("5000"));
    }

    @Test
    void acknowledgingRecommendationIsIdempotentAndNeverExecutes() throws Exception {
        var recommendationId = "20000000-0000-0000-0000-000000000001";
        var body = "{\"idempotencyKey\":\"ack-1\",\"decisionType\":\"DEFERRED\",\"rationale\":\"Wait for earnings\"}";
        mockMvc.perform(post("/api/v1/recommendations/{id}/acknowledge", recommendationId)
                        .with(httpBasic("admin@example.local", "change-before-use"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionType").value("DEFERRED"))
                .andExpect(jsonPath("$.newlyAcknowledged").value(true))
                .andExpect(jsonPath("$.executionSubmitted").value(false));
        mockMvc.perform(post("/api/v1/recommendations/{id}/acknowledge", recommendationId)
                        .with(httpBasic("admin@example.local", "change-before-use"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.newlyAcknowledged").value(false))
                .andExpect(jsonPath("$.executionSubmitted").value(false));
        assertThat(jdbc.sql(
                                "SELECT CONCAT(decision_type, ':', rationale) FROM recommendation_acknowledgement WHERE recommendation_id=UUID_TO_BIN(:id)")
                        .param("id", recommendationId)
                        .query(String.class)
                        .single())
                .isEqualTo("DEFERRED:Wait for earnings");
    }

    @Test
    void portfolioListAndChartContractsAreOwnedAndEvidenceBased() throws Exception {
        mockMvc.perform(get("/api/v1/portfolio/holdings").with(httpBasic("admin@example.local", "change-before-use")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(OWNER_POSITION))
                .andExpect(jsonPath("$[0].version").value(0))
                .andExpect(jsonPath("$[0].symbol").value("SPY"))
                .andExpect(jsonPath("$[0].marketValue").value("5000.125"))
                .andExpect(jsonPath("$[0].classificationConfirmed").value(false));
        mockMvc.perform(get("/api/v1/positions/{id}/chart", OWNER_POSITION)
                        .param("range", "1Y")
                        .with(httpBasic("admin@example.local", "change-before-use")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bars").isArray())
                .andExpect(jsonPath("$.entryMarkers").isArray())
                .andExpect(jsonPath("$.stopSeries").isArray())
                .andExpect(jsonPath("$.earningsMarkers").isArray())
                .andExpect(jsonPath("$.tradeMarkers").isArray())
                .andExpect(jsonPath("$.quality").isString());
        mockMvc.perform(get("/api/v1/positions/{id}/chart", OTHER_POSITION)
                        .with(httpBasic("admin@example.local", "change-before-use")))
                .andExpect(status().isNotFound());
    }

    private void seedPositionIntelligence() {
        update(
                """
                INSERT INTO position_thesis (id, position_id, summary, confirmation_signals, invalidation_signals, status, expires_at, user_confirmed, created_at, updated_at, version)
                VALUES (UUID_TO_BIN('30000000-0000-0000-0000-000000000010'), UUID_TO_BIN('%s'), 'Quality compounds with durable margins', JSON_ARRAY('margin stable'), JSON_ARRAY('margin collapse'), 'HEALTHY', '2099-01-01', FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """
                        .formatted(OWNER_POSITION));
        update(
                """
                INSERT INTO thesis_source (id, thesis_id, source_type, source_uri, checksum, created_at)
                VALUES (UUID_TO_BIN('30000000-0000-0000-0000-000000000011'), UUID_TO_BIN('30000000-0000-0000-0000-000000000010'), 'FILING', 'https://example.test/filing', REPEAT('b',64), UTC_TIMESTAMP(6))
                """);
        update(
                """
                INSERT INTO stop_snapshot (id, position_id, strategy_version, entry_price, atr, structure_stop, volatility_stop, initial_stop, live_stop, soft_alert, catastrophic_stop, close_confirmed, rule_ids, quality_status, evidence_checksum, data_as_of, created_at)
                VALUES (UUID_TO_BIN('30000000-0000-0000-0000-000000000001'), UUID_TO_BIN('%s'), '3.0.0-draft', 100, 4, 91, 90, 90, 94, 96, 91, TRUE, JSON_ARRAY('STOP.CLOSE_CONFIRMED.001'), 'HEALTHY', REPEAT('c',64), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """
                        .formatted(OWNER_POSITION));
        update(
                """
                INSERT INTO valuation_snapshot (id, position_id, strategy_version, fundamental_health, valuation_discount, earnings_revisions, price_stabilization, portfolio_capacity, discount_tactical_weight, action, rule_ids, evidence_checksum, data_as_of, valid_until, created_at)
                VALUES (UUID_TO_BIN('30000000-0000-0000-0000-000000000020'), UUID_TO_BIN('%s'), '3.0.0-draft', 'HEALTHY', TRUE, 'IMPROVING', 'CONFIRMED', TRUE, 0, 'ADD_1_PERCENT_STARTER', JSON_ARRAY(), REPEAT('d',64), UTC_TIMESTAMP(6), '2099-01-01', UTC_TIMESTAMP(6))
                """
                        .formatted(OWNER_POSITION));
        update(
                """
                INSERT INTO earnings_risk_snapshot (id, position_id, strategy_version, event_count, next_event_at, downside_tail_fraction, gap_p75_fraction, gap_p90_fraction, profit_cushion_r, action, rule_ids, evidence_checksum, data_as_of, valid_until, created_at)
                VALUES (UUID_TO_BIN('30000000-0000-0000-0000-000000000030'), UUID_TO_BIN('%s'), '3.0.0-draft', 10, '2099-01-01', -0.08, 0.06, 0.10, 1.5, 'HOLD_THROUGH_EVENT', JSON_ARRAY('EARNINGS.QUALITY.001'), REPEAT('e',64), UTC_TIMESTAMP(6), '2099-01-01', UTC_TIMESTAMP(6))
                """
                        .formatted(OWNER_POSITION));
        update(
                """
                INSERT INTO trade_journal (id, user_id, position_id, entry_type, tax_status, planned_risk_amount, planned_r, realized_r, mfe_r, mae_r, exit_reason, notes, occurred_at, created_at, version)
                VALUES (UUID_TO_BIN('30000000-0000-0000-0000-000000000040'), UUID_TO_BIN('%s'), UUID_TO_BIN('%s'), 'INITIAL', 'LONG_TERM', 100, 1, 1.5, 2.5, -0.5, 'TARGET', 'Risk first', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """
                        .formatted(OWNER, OWNER_POSITION));
    }

    private void recommendation(String id, String priority, String action, int suffix) {
        update(
                """
                INSERT INTO recommendation (id, user_id, position_id, strategy_version, action, priority,
                    confidence, reasons, risks, change_conditions, rule_ids, evidence_checksum,
                    data_as_of, valid_until, status, created_at)
                VALUES (UUID_TO_BIN('%s'), UUID_TO_BIN('%s'), UUID_TO_BIN('%s'), '3.0.0-draft', '%s', '%s',
                    'HIGH', JSON_ARRAY('reason'), JSON_ARRAY('risk'), JSON_ARRAY('condition'), JSON_ARRAY('RISK.WEIGHT.001'),
                    SHA2('recommendation-%s', 256), UTC_TIMESTAMP(6), '2099-01-01 00:00:00', 'ACTIVE', UTC_TIMESTAMP(6))
                """
                        .formatted(id, OWNER, OWNER_POSITION, action, priority, suffix));
    }

    private void update(String sql) {
        jdbc.sql(sql).update();
    }
}
