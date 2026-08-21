package com.example.portfolio.analysis.application;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class PositionReportContractTest extends HoldingAnalysisIntegrationFixture {
    @Autowired
    MockMvc mockMvc;

    @Test
    void reportIsOwnerScopedAndExposesStructuredAuditEvidence() throws Exception {
        recommendations.generateAll(USER_ID);

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
        recommendations.generateAll(USER_ID);

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
        recommendations.generateAll(USER_ID);

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
                .andExpect(jsonPath("$.layers.risk.projectedPositionWeight").doesNotExist())
                .andExpect(
                        jsonPath("$.layers.risk.projectedTotalRiskAfterAction").doesNotExist())
                .andExpect(jsonPath("$.layers.risk.projectedClusterRiskAfterAction")
                        .doesNotExist())
                .andExpect(jsonPath("$.layers.risk.riskPerShare").doesNotExist());
    }
}
