package com.example.portfolio.analysis.application;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class PositionReportContractTest extends HoldingAnalysisIntegrationFixture {
    private static final java.util.UUID REPORT_RUN = java.util.UUID.fromString("90000000-0000-0000-0000-000000000099");

    @Autowired
    MockMvc mockMvc;

    @Test
    void reportIsOwnerScopedAndExposesStructuredAuditEvidence() throws Exception {
        generateReportRun();

        mockMvc.perform(get("/api/v1/positions/{positionId}/report", GOOGL_POSITION)
                        .with(httpBasic(USER_EMAIL, "change-before-use")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position.symbol").value("GOOGL"))
                .andExpect(jsonPath("$.position.classificationSource").value("USER_CONFIRMED"))
                .andExpect(jsonPath("$.readiness").value("READY"))
                .andExpect(jsonPath("$.recommendation.action").value("TRIM"))
                .andExpect(jsonPath("$.recommendation.reasons").isArray())
                .andExpect(jsonPath("$.recommendation.suppressedCandidates").isArray())
                .andExpect(jsonPath("$.evidence.ruleIds").isArray())
                .andExpect(jsonPath("$.evidence.configHash").isString())
                .andExpect(jsonPath("$.dataAsOf").exists());
    }

    @Test
    void canonicalAnalystReportUsesSixLayersAndClassificationSpecificHardLimits() throws Exception {
        generateReportRun();

        assertAnalystReport(GOOGL_POSITION, "GOOGL", "QUALITY_STOCK", "0.15");
        assertAnalystReport(DRAM_POSITION, "DRAM", "THEMATIC_ETF", "0.1");
        assertAnalystReport(DXYZ_POSITION, "DXYZ", "SPECULATIVE", "0.02");
    }

    private void assertAnalystReport(java.util.UUID positionId, String symbol, String classification, String hardMax)
            throws Exception {
        mockMvc.perform(get("/api/v1/holdings/{positionId}/analyst-report", positionId)
                        .with(httpBasic(USER_EMAIL, "change-before-use")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position.symbol").value(symbol))
                .andExpect(jsonPath("$.layers.systemRecommendation.action").isNotEmpty())
                .andExpect(jsonPath("$.layers.portfolioRole.classification").value(classification))
                .andExpect(jsonPath("$.layers.portfolioRole.hardMaxWeight").value(hardMax))
                .andExpect(jsonPath("$.layers.market.price").isNotEmpty())
                .andExpect(jsonPath("$.layers.market.averageCost").isNotEmpty())
                .andExpect(jsonPath("$.layers.market.unrealizedPnlDollar").isNotEmpty())
                .andExpect(jsonPath("$.layers.fundamentals.quality").isNotEmpty())
                .andExpect(jsonPath("$.layers.valuation.state").isNotEmpty())
                .andExpect(jsonPath("$.layers.technical.sma20").isNotEmpty())
                .andExpect(jsonPath("$.layers.risk.positionPlannedRiskDollar").isNotEmpty())
                .andExpect(jsonPath("$.layers.risk.totalPortfolioPlannedRisk").isNotEmpty())
                .andExpect(jsonPath("$.layers.priceRiskEarnings.priceState").isNotEmpty())
                .andExpect(jsonPath("$.layers.rationaleAndEvidence.reasons").isArray())
                .andExpect(jsonPath("$.layers.rationaleAndEvidence.risks").isArray())
                .andExpect(jsonPath("$.layers.rationaleAndEvidence.changeConditions")
                        .isArray())
                .andExpect(jsonPath("$.layers.rationaleAndEvidence.evidenceDrawer.ruleIds")
                        .isArray())
                .andExpect(jsonPath("$.layers.rationaleAndEvidence.evidenceDrawer.configHash")
                        .isString());
    }

    @Test
    void qualityStockReportExposesCanonicalNumbersAndPreservesMissingValues() throws Exception {
        generateReportRun();

        mockMvc.perform(get("/api/v1/holdings/{positionId}/analyst-report", GOOGL_POSITION)
                        .with(httpBasic(USER_EMAIL, "change-before-use")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layers.market.price").value("200"))
                .andExpect(jsonPath("$.layers.market.averageCost").value("150"))
                .andExpect(jsonPath("$.layers.market.unrealizedPnlDollar").value("5000"))
                .andExpect(jsonPath("$.layers.fundamentals.revenueTtm").value("1000000"))
                .andExpect(jsonPath("$.layers.fundamentals.operatingMargin").value("0.27"))
                .andExpect(jsonPath("$.layers.fundamentals.fcfTtm").value("250000"))
                .andExpect(jsonPath("$.layers.valuation.trailingPeTtm").value("21.3"))
                .andExpect(jsonPath("$.layers.valuation.forwardPeFy1").value("19.4"))
                .andExpect(jsonPath("$.layers.valuation.historyPercentile5y").value("0.28"))
                .andExpect(jsonPath("$.layers.estimates.fy1Eps").value("8.42"))
                .andExpect(jsonPath("$.layers.estimates.epsRevision30d").value("0.021"))
                .andExpect(jsonPath("$.layers.estimates.analystCount").value(39))
                .andExpect(jsonPath("$.layers.risk.projectedPositionWeight").value("0.15"))
                .andExpect(
                        jsonPath("$.layers.risk.projectedTotalRiskAfterAction").doesNotExist())
                .andExpect(jsonPath("$.layers.risk.projectedClusterRiskAfterAction")
                        .doesNotExist())
                .andExpect(jsonPath("$.layers.risk.riskPerShare").doesNotExist());
    }

    @Test
    void newerQuoteIsSeparatedFromRunBoundDecisionEvidence() throws Exception {
        generateReportRun();
        jdbc.sql(
                        """
                        INSERT INTO quote (id,instrument_id,last_price,currency,provider,source_timestamp,checksum,
                          quality_status,data_as_of,created_at)
                        VALUES (UUID_TO_BIN('96000000-0000-0000-0000-000000000099'),
                          UUID_TO_BIN('93000000-0000-0000-0000-000000000001'),250,'USD','TEST',
                          DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 MINUTE),SHA2('newer-current-price',256),'HEALTHY',
                          DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 MINUTE),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 MINUTE))
                        """)
                .update();

        mockMvc.perform(get("/api/v1/holdings/{positionId}/analyst-report", GOOGL_POSITION)
                        .with(httpBasic(USER_EMAIL, "change-before-use")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layers.market.price").value("200"))
                .andExpect(jsonPath("$.currentChange.price").value("250"))
                .andExpect(jsonPath("$.currentChange.label").value("CURRENT_CHANGE_NOT_USED_IN_RECOMMENDATION"))
                .andExpect(jsonPath("$.analysisAsOf.analysisRunId").value(REPORT_RUN.toString()));
    }

    private void generateReportRun() {
        jdbc.sql(
                        """
                INSERT INTO portfolio_analysis_run (
                  id,user_id,market_date,strategy_version,status,started_at,completed_at,data_as_of,
                  run_key,created_at,updated_at,version)
                VALUES (UUID_TO_BIN('90000000-0000-0000-0000-000000000099'),
                  UUID_TO_BIN('91000000-0000-0000-0000-000000000001'),CURRENT_DATE,'3.0.0-draft','SUCCEEDED',
                  UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'test:position-report',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)
                """)
                .update();
        analysis.analyzeAll(USER_ID, REPORT_RUN);
        recommendations.generateForRun(USER_ID, REPORT_RUN);
    }
}
