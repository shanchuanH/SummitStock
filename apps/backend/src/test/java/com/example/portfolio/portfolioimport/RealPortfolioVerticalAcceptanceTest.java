package com.example.portfolio.portfolioimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.portfolio.runtime.AnalysisRunOrchestrator;
import com.example.portfolio.runtime.DurableJobCoordinator;
import com.example.portfolio.runtime.DurableJobStore;
import com.example.portfolio.runtime.JobHandlerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

@TestPropertySource(properties = "portfolio.test.complete-provider-fixtures=true")
class RealPortfolioVerticalAcceptanceTest extends PortfolioImportIntegrationSupport {
    private static final Map<String, String> CLASSIFICATIONS = Map.ofEntries(
            Map.entry("GOOGL", "QUALITY_STOCK"),
            Map.entry("DRAM", "THEMATIC_ETF"),
            Map.entry("DXYZ", "SPECULATIVE"),
            Map.entry("MSFT", "QUALITY_STOCK"),
            Map.entry("QQQM", "CORE_TECH_ETF"),
            Map.entry("VGT", "CORE_TECH_ETF"),
            Map.entry("NOK", "TURNAROUND_TACTICAL"),
            Map.entry("AAOI", "TACTICAL_STOCK"),
            Map.entry("VOO", "CORE_BROAD_ETF"),
            Map.entry("CSIQ", "CYCLICAL_TACTICAL"),
            Map.entry("TSLA", "QUALITY_GROWTH_HIGH_VOL"),
            Map.entry("SNDK", "CYCLICAL_TACTICAL"),
            Map.entry("NVDA", "QUALITY_GROWTH_HIGH_VOL"));

    @Autowired
    DurableJobStore jobs;

    @Autowired
    JobHandlerRegistry handlers;

    @Autowired
    AnalysisRunOrchestrator orchestrator;

    @Autowired
    MeterRegistry meters;

    @Autowired
    Clock clock;

    @Test
    void completePortfolioRunsThroughFormalServicesWithTruthfulAssetAndPortfolioEvidence() throws Exception {
        var preview = preview("full-real-portfolio.csv");
        assertThat(preview.path("summary").path("rowCount").asInt()).isEqualTo(15);
        var confirmed =
                confirm(uuid(preview, "batchId"), preview.path("version").asLong(), "[]");
        var runId = uuid(confirmed, "analysisRunId");
        assertThat(confirmed.path("analysisState").asString()).isEqualTo("ANALYSIS_QUEUED");
        assertThat(confirmed.path("openPositionCount").asInt()).isEqualTo(13);
        assertThat(confirmed.path("cashRowCount").asInt()).isEqualTo(1);
        assertThat(confirmed.path("compensationRowCount").asInt()).isEqualTo(1);

        confirmClassifications();
        clearSharedMarketEvidence();
        seedCompleteInputEvidence();
        seedTechnologyCluster();
        var coordinator = new DurableJobCoordinator(jobs, handlers, orchestrator, meters, clock);
        for (int iteration = 0; iteration < 50 && !terminal(runId); iteration++) {
            assertThat(coordinator.runOnce())
                    .as("pipeline iteration " + iteration)
                    .isTrue();
        }
        assertThat(runStatus(runId)).isIn("SUCCEEDED", "PARTIAL");

        var brief = getJson("/api/v1/brief/today");
        assertThat(brief.path("state").asString()).isEqualTo("BLOCKED");
        assertThat(brief.path("mustAct").size()).isLessThanOrEqualTo(3);
        var summary = brief.path("summary");
        assertThat(decimal(summary, "totalLiquidAssets"))
                .as("brief liquid assets use canonical marks plus tracked cash")
                .isEqualByComparingTo(canonicalLiquidAssets());
        assertThat(summary.path("trackedCash").asString()).isEqualTo("16000");
        assertThat(count("SELECT COUNT(*) FROM broker_cash_snapshot s JOIN app_user u ON u.id=s.user_id "
                        + "WHERE u.email='" + EMAIL + "' AND s.cash_amount=22000"))
                .isEqualTo(1);
        assertThat(summary.path("unvestedCompensationValue").asString()).isEqualTo("4000");
        assertThat(decimal(summary, "coreExposureFraction")).isPositive().isLessThanOrEqualTo(BigDecimal.ONE);
        assertThat(decimal(summary, "tacticalExposureFraction")).isPositive();
        assertThat(decimal(summary, "technologyExposureFraction")).isPositive().isLessThanOrEqualTo(BigDecimal.ONE);
        assertThat(decimal(summary, "employerExposureFraction")).isPositive();
        assertThat(decimal(summary, "clusterRiskFraction")).isNotNegative();
        assertThat(decimal(summary, "openPlannedRiskFraction")).isNotNegative();
        assertThat(count("SELECT COUNT(*) FROM risk_cluster_snapshot s JOIN app_user u ON u.id=s.user_id "
                        + "WHERE u.email='" + EMAIL + "'"))
                .isPositive();
        assertThat(count("SELECT COUNT(*) FROM position_risk_snapshot r JOIN position p ON p.id=r.position_id "
                        + "JOIN investment_account a ON a.id=p.account_id JOIN app_user u ON u.id=a.user_id "
                        + "WHERE u.email='" + EMAIL + "'"))
                .isGreaterThanOrEqualTo(13);
        assertThat(decimal(summary, "portfolioDrawdownFraction")).isNotNegative();
        assertThat(summary.path("drawdownSource").asString()).isNotBlank();
        assertThat(count("SELECT COUNT(*) FROM position p JOIN investment_account a ON a.id=p.account_id "
                        + "JOIN app_user u ON u.id=a.user_id WHERE u.email='" + EMAIL + "' AND p.status='OPEN'"))
                .isEqualTo(13);
        assertThat(count("SELECT COUNT(*) FROM compensation_holding c JOIN app_user u ON u.id=c.user_id "
                        + "WHERE u.email='" + EMAIL + "' AND c.symbol='AMZN' AND c.vesting_status='UNVESTED'"))
                .isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM position p JOIN investment_account a ON a.id=p.account_id "
                        + "JOIN app_user u ON u.id=a.user_id JOIN instrument i ON i.id=p.instrument_id "
                        + "WHERE u.email='" + EMAIL + "' AND i.symbol='AMZN'"))
                .isZero();

        var googl = report("GOOGL");
        assertThat(googl.at("/assetEvidence/company/companyModelApplied").asBoolean())
                .isTrue();
        assertThat(googl.at("/assetEvidence/company/growthProfitabilityCashFlowStatus")
                        .asString())
                .isNotBlank();
        assertThat(googl.at("/assetEvidence/company/valuationStatus").asString())
                .isNotBlank();
        assertThat(googl.at("/assetEvidence/company/earningsRiskStatus").asString())
                .isNotBlank();
        assertThat(googl.at("/assetEvidence/portfolioContext/currentWeight").asString())
                .isNotBlank();
        assertThat(googl.at("/auditEvidence/exactQuantityAllowed").asBoolean()).isFalse();
        assertThat(googl.at("/recommendation/quantityMax").isNull()).isTrue();

        var changedClassification = mockMvc.perform(post(
                                "/api/v1/positions/{id}/classify",
                                googl.at("/position/id").asString())
                        .with(httpBasic(EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"classification\":\"SPECULATIVE\",\"expectedVersion\":1}"))
                .andReturn();
        assertThat(changedClassification.getResponse().getStatus()).isEqualTo(200);
        assertThat(report("GOOGL").at("/position/classification").asString())
                .as("completed reports keep the classification bound to their analysis run")
                .isEqualTo("QUALITY_STOCK");

        var dram = report("DRAM");
        assertThat(dram.at("/assetEvidence/etf/etfModelApplied").asBoolean()).isTrue();
        assertThat(dram.at("/assetEvidence/etf/companyEarningsModelApplied").asBoolean())
                .isFalse();
        assertThat(dram.at("/assetEvidence/etf/trendStatus").asString()).isNotBlank();

        var dxyz = report("DXYZ");
        assertThat(dxyz.at("/position/classification").asString()).isEqualTo("SPECULATIVE");
        assertThat(dxyz.at("/assetEvidence/speculative/speculativePolicyApplied")
                        .asBoolean())
                .isTrue();
        assertThat(dxyz.at("/assetEvidence/speculative/confidenceCeiling").asString())
                .isEqualTo("LOW");
        assertThat(dxyz.at("/assetEvidence/speculative/stopStatus").asString()).isEqualTo("MISSING");
        assertThat(dxyz.at("/assetEvidence/speculative/tickerOrPriceCanUpgradeQuality")
                        .asBoolean())
                .isFalse();

        var recommendationId = googl.at("/recommendation/id").asString();
        var acknowledgement = mockMvc.perform(
                        post("/api/v1/recommendations/{id}/acknowledge", recommendationId)
                                .with(httpBasic(EMAIL, PASSWORD))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"idempotencyKey\":\"real-portfolio-ack\",\"decisionType\":\"HANDLED\",\"rationale\":\"Acceptance review\"}"))
                .andReturn();
        assertThat(acknowledgement.getResponse().getStatus()).isEqualTo(200);
        var ack = json.readTree(acknowledgement.getResponse().getContentAsString());
        assertThat(ack.path("executionSubmitted").asBoolean()).isFalse();
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM recommendation_acknowledgement WHERE idempotency_key='real-portfolio-ack'"))
                .isEqualTo(1);
    }

    @AfterEach
    void removeAcceptanceEvidence() {
        clearSharedMarketEvidence();
        update("DELETE p FROM instrument_analysis_profile p JOIN instrument i ON i.id=p.instrument_id "
                + "WHERE p.source='IMPORTED_MAPPING' AND i.symbol IN ('DRAM','QQQM','VGT','VOO') "
                + "AND p.evidence_checksum=SHA2(CONCAT('t15-fund-profile-',i.symbol),256)");
        update("DELETE FROM company_event WHERE source='fixture-calendar'");
        update("DELETE FROM earnings_event WHERE source='fixture-calendar'");
        update(
                "DELETE r FROM estimate_revision_snapshot r JOIN instrument i ON i.id=r.instrument_id "
                        + "WHERE i.symbol IN ('GOOGL','MSFT','TSLA','NVDA') AND r.evidence_checksum=SHA2(CONCAT('t15-revision-',i.symbol),256)");
        update(
                "DELETE t FROM position_thesis t JOIN position p ON p.id=t.position_id JOIN instrument i ON i.id=p.instrument_id "
                        + "WHERE i.symbol='DXYZ' AND t.summary='T15 bounded speculative thesis'");
        update("DELETE h FROM financial_health_snapshot h JOIN financial_period p ON p.id=h.period_id "
                + "WHERE p.source LIKE 'https://fixture.sec/%'");
        update("DELETE m FROM financial_metric_snapshot m JOIN financial_period p ON p.id=m.period_id "
                + "WHERE p.source LIKE 'https://fixture.sec/%'");
        update("DELETE FROM financial_fact_observation WHERE source LIKE 'https://fixture.sec/%'");
        update("DELETE FROM financial_period WHERE source LIKE 'https://fixture.sec/%'");
    }

    private void confirmClassifications() throws Exception {
        var positions = getJson("/api/v1/positions");
        assertThat(positions.size()).isEqualTo(CLASSIFICATIONS.size());
        for (var position : positions) {
            var symbol = position.path("symbol").asString();
            var classification = CLASSIFICATIONS.get(symbol);
            assertThat(classification).as("classification input for " + symbol).isNotNull();
            var result = mockMvc.perform(post(
                                    "/api/v1/positions/{id}/classify",
                                    position.path("id").asString())
                            .with(httpBasic(EMAIL, PASSWORD))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"classification\":\"" + classification + "\",\"expectedVersion\":"
                                    + position.path("version").asLong() + "}"))
                    .andReturn();
            assertThat(result.getResponse().getStatus())
                    .as("classification " + symbol)
                    .isEqualTo(200);
        }
    }

    private void seedTechnologyCluster() {
        update(
                "INSERT INTO risk_cluster (id,user_id,cluster_code,display_name,risk_cap_fraction,created_at,updated_at) "
                        + "SELECT UUID_TO_BIN('71000000-0000-0000-0000-000000000001'),u.id,'TECHNOLOGY','Technology',0.08,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6) "
                        + "FROM app_user u WHERE u.email='" + EMAIL + "'");
        update("INSERT INTO risk_cluster_membership (id,risk_cluster_id,position_id,contribution_weight,created_at) "
                + "SELECT UUID_TO_BIN(UUID()),UUID_TO_BIN('71000000-0000-0000-0000-000000000001'),p.id,1,UTC_TIMESTAMP(6) "
                + "FROM position p JOIN investment_account a ON a.id=p.account_id JOIN app_user u ON u.id=a.user_id "
                + "JOIN instrument i ON i.id=p.instrument_id WHERE u.email='" + EMAIL
                + "' AND i.symbol IN ('GOOGL','MSFT','QQQM','VGT','NVDA','TSLA')");
    }

    private void seedCompleteInputEvidence() {
        update("UPDATE cash_bucket c JOIN app_user u ON u.id=c.user_id SET c.current_amount=2000 " + "WHERE u.email='"
                + EMAIL + "' AND c.bucket_type='ALLOCATED_TRADE'");
        update("INSERT INTO instrument_analysis_profile (id,instrument_id,profile_type,fund_profile_available,thematic,"
                + "top_holding_concentration,fund_liquidity_status,portfolio_overlap_fraction,source,evidence_checksum,data_as_of,created_at) "
                + "SELECT UUID_TO_BIN(UUID()),i.id,'FUND',TRUE,i.symbol='DRAM',0.12,'HEALTHY',0.18,'IMPORTED_MAPPING',"
                + "SHA2(CONCAT('t15-fund-profile-',i.symbol),256),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6) FROM instrument i "
                + "WHERE i.symbol IN ('DRAM','QQQM','VGT','VOO')");
        update(
                "INSERT INTO estimate_revision_snapshot (id,instrument_id,period_end,horizon,revision_7d,revision_30d,revision_90d,"
                        + "overall_revision,analyst_count,quality,evidence_checksum,data_as_of,created_at) "
                        + "SELECT UUID_TO_BIN(UUID()),i.id,DATE_ADD(CURRENT_DATE,INTERVAL 90 DAY),'NEXT_QUARTER','FLAT','FLAT','FLAT','FLAT',"
                        + "20,'HEALTHY',SHA2(CONCAT('t15-revision-',i.symbol),256),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6) FROM instrument i "
                        + "WHERE i.symbol IN ('GOOGL','MSFT','TSLA','NVDA')");
        update(
                "INSERT INTO position_thesis (id,position_id,summary,confirmation_signals,invalidation_signals,status,expires_at,user_confirmed,created_at,updated_at) "
                        + "SELECT UUID_TO_BIN(UUID()),p.id,'T15 bounded speculative thesis',JSON_ARRAY('risk remains bounded'),"
                        + "JSON_ARRAY('formal stop'), 'HEALTHY',DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 90 DAY),TRUE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6) "
                        + "FROM position p JOIN instrument i ON i.id=p.instrument_id JOIN investment_account a ON a.id=p.account_id "
                        + "JOIN app_user u ON u.id=a.user_id WHERE u.email='" + EMAIL + "' AND i.symbol='DXYZ'");
    }

    private void clearSharedMarketEvidence() {
        var ownedSymbols = "('GOOGL','DRAM','DXYZ','MSFT','QQQM','VGT','NOK','AAOI','VOO','CSIQ','TSLA','SNDK','NVDA')";
        update("DELETE s FROM price_state_snapshot s JOIN instrument i ON i.id=s.instrument_id WHERE i.symbol IN "
                + ownedSymbols);
        update("DELETE s FROM indicator_snapshot s JOIN instrument i ON i.id=s.instrument_id WHERE i.symbol IN "
                + ownedSymbols);
        update("DELETE q FROM quote q JOIN instrument i ON i.id=q.instrument_id WHERE i.symbol IN " + ownedSymbols);
        update("DELETE b FROM price_bar b JOIN instrument i ON i.id=b.instrument_id WHERE i.symbol IN " + ownedSymbols);
        update("DELETE a FROM corporate_action a JOIN instrument i ON i.id=a.instrument_id WHERE i.symbol IN "
                + ownedSymbols);
        update("DELETE FROM market_regime_snapshot");
        update("DELETE FROM macro_factor_snapshot");
        update("DELETE FROM macro_observation");
    }

    private JsonNode report(String symbol) throws Exception {
        var positionId = jdbc.sql(
                        "SELECT BIN_TO_UUID(p.id) FROM position p JOIN investment_account a ON a.id=p.account_id "
                                + "JOIN app_user u ON u.id=a.user_id JOIN instrument i ON i.id=p.instrument_id "
                                + "WHERE u.email=:email AND i.symbol=:symbol")
                .param("email", EMAIL)
                .param("symbol", symbol)
                .query(String.class)
                .single();
        return getJson("/api/v1/positions/" + positionId + "/report");
    }

    private JsonNode getJson(String path) throws Exception {
        var result = mockMvc.perform(get(path).with(httpBasic(EMAIL, PASSWORD))).andReturn();
        assertThat(result.getResponse().getStatus()).as(path).isEqualTo(200);
        return json.readTree(result.getResponse().getContentAsString());
    }

    private BigDecimal decimal(JsonNode node, String field) {
        return new BigDecimal(node.path(field).asString());
    }

    private BigDecimal canonicalLiquidAssets() {
        return jdbc.sql(
                        """
                        SELECT COALESCE(SUM(m.marked_market_value),0)
                          + COALESCE((SELECT SUM(c.current_amount) FROM cash_bucket c
                            JOIN app_user u ON u.id=c.user_id WHERE u.email=:email),0)
                        FROM current_position_mark m JOIN position p ON p.id=m.position_id
                        JOIN investment_account a ON a.id=p.account_id JOIN app_user u ON u.id=a.user_id
                        WHERE u.email=:email AND p.status='OPEN'
                        """)
                .param("email", EMAIL)
                .query(BigDecimal.class)
                .single();
    }

    private boolean terminal(java.util.UUID runId) {
        return java.util.Set.of("SUCCEEDED", "PARTIAL", "FAILED", "BLOCKED").contains(runStatus(runId));
    }

    private String runStatus(java.util.UUID runId) {
        return jdbc.sql("SELECT status FROM portfolio_analysis_run WHERE id=UUID_TO_BIN(:id)")
                .param("id", runId.toString())
                .query(String.class)
                .single();
    }
}
