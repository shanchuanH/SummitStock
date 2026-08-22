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
                .andExpect(jsonPath("$.currentChange.label").value("CURRENT_STATE_NOT_USED_IN_RECOMMENDATION"))
                .andExpect(jsonPath("$.analysisAsOf.analysisRunId").value(REPORT_RUN.toString()));
    }

    @Test
    void historicalReportKeepsRunBoundPositionCapitalAndRiskAfterCurrentStateChanges() throws Exception {
        generateReportRun();

        jdbc.sql("UPDATE position SET quantity=5,average_cost=999,market_value=5000 WHERE id=UUID_TO_BIN(:id)")
                .param("id", GOOGL_POSITION.toString())
                .update();
        jdbc.sql(
                        """
                        INSERT INTO position_snapshot
                          (id,position_id,import_batch_id,quantity,average_cost,market_value,data_as_of,source,evidence_checksum,created_at)
                        VALUES (UUID_TO_BIN('97010000-0000-0000-0000-000000000099'),UUID_TO_BIN(:positionId),NULL,
                          5,999,5000,DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 MINUTE),'TEST',SHA2('future-position',256),
                          DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 MINUTE))
                        """)
                .param("positionId", GOOGL_POSITION.toString())
                .update();
        jdbc.sql(
                        """
                        INSERT INTO position_mark_snapshot
                          (id,position_id,instrument_id,quantity,decision_price,marked_market_value,market_date,
                           source_provider,quality_status,price_data_as_of,strategy_version,strategy_config_hash,
                           evidence_checksum,data_as_of,created_at)
                        VALUES (UUID_TO_BIN('97020000-0000-0000-0000-000000000099'),UUID_TO_BIN(:positionId),
                          UUID_TO_BIN('93000000-0000-0000-0000-000000000001'),5,1000,5000,CURRENT_DATE,'TEST','HEALTHY',
                          DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 MINUTE),'3.0.0-draft',SHA2('config',256),
                          SHA2('future-mark',256),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 MINUTE),
                          DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 MINUTE))
                        """)
                .param("positionId", GOOGL_POSITION.toString())
                .update();
        updateFutureCapitalAndRisk();

        mockMvc.perform(get("/api/v1/holdings/{positionId}/analyst-report", GOOGL_POSITION)
                        .with(httpBasic(USER_EMAIL, "change-before-use")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.layers.market.price").value("200"))
                .andExpect(jsonPath("$.layers.market.averageCost").value("150"))
                .andExpect(jsonPath("$.layers.market.unrealizedPnlDollar").value("5000"))
                .andExpect(jsonPath("$.layers.risk.currentWeight").value("0.625"))
                .andExpect(jsonPath("$.layers.risk.clusterRisk").value("0.0003"))
                .andExpect(jsonPath("$.layers.risk.totalPortfolioPlannedRisk").value("0.0003"))
                .andExpect(jsonPath("$.analysisAsOf.analysisRunId").value(REPORT_RUN.toString()));
    }

    private void updateFutureCapitalAndRisk() {
        jdbc.sql(
                        """
                        INSERT INTO portfolio_capital_snapshot
                          (id,user_id,strategy_version,strategy_config_hash,invested_tradable_assets,tracked_cash,
                           required_emergency_floor,emergency_reserve,deployable_cash,investable_assets,total_liquid_assets,
                           unvested_compensation_value,quality_status,evidence_checksum,data_as_of,created_at)
                        VALUES (UUID_TO_BIN('97030000-0000-0000-0000-000000000099'),UUID_TO_BIN(:userId),'3.0.0-draft',
                          SHA2('config',256),5000,50000,20000,20000,30000,35000,55000,0,'HEALTHY',SHA2('future-capital',256),
                          DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 MINUTE),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 MINUTE))
                        """)
                .param("userId", USER_ID.toString())
                .update();
        jdbc.sql(
                        """
                        INSERT INTO position_risk_snapshot
                          (id,position_id,strategy_version,current_weight,open_risk_fraction,cluster_risk_fraction,risk_amount,
                           quality_status,evidence_checksum,data_as_of,created_at)
                        VALUES (UUID_TO_BIN('97040000-0000-0000-0000-000000000099'),UUID_TO_BIN(:positionId),
                          '3.0.0-draft',0.9,0.4,0,14000,'HEALTHY',SHA2('future-risk',256),
                          DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 MINUTE),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 MINUTE))
                        """)
                .param("positionId", GOOGL_POSITION.toString())
                .update();
        jdbc.sql(
                        """
                        INSERT INTO risk_cluster_snapshot
                          (id,risk_cluster_id,user_id,open_risk_amount,open_risk_fraction,member_count,quality,data_as_of,
                           strategy_version,strategy_config_hash,evidence_checksum,created_at)
                        VALUES (UUID_TO_BIN('97050000-0000-0000-0000-000000000099'),
                          UUID_TO_BIN('99410000-0000-0000-0000-000000000001'),UUID_TO_BIN(:userId),14000,0.4,3,'HEALTHY',
                          DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 MINUTE),'3.0.0-draft',SHA2('config',256),
                          SHA2('future-cluster',256),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 MINUTE))
                        """)
                .param("userId", USER_ID.toString())
                .update();
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
