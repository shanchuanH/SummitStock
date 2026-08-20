package com.example.portfolio.valuation;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ValuationEvidenceStore {
    private final JdbcClient jdbc;
    private final Clock clock;

    public ValuationEvidenceStore(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public List<InputRow> inputs() {
        return jdbc.sql(
                        """
                        WITH metric_versions AS (
                          SELECT m.instrument_id,m.period_id,m.metric_code,m.value_decimal,p.period_type,p.end_date,
                                 ROW_NUMBER() OVER (PARTITION BY m.instrument_id,m.period_id,m.metric_code
                                                    ORDER BY m.data_as_of DESC,m.created_at DESC) version_rank
                          FROM financial_metric_snapshot m JOIN financial_period p ON p.id=m.period_id
                        ), quarterly AS (
                          SELECT instrument_id,metric_code,value_decimal,end_date,
                                 DENSE_RANK() OVER (PARTITION BY instrument_id,metric_code ORDER BY end_date DESC) quarter_rank
                          FROM metric_versions WHERE version_rank=1 AND period_type='QUARTERLY'
                        ), ttm AS (
                          SELECT instrument_id,
                                 CASE WHEN COUNT(CASE WHEN metric_code='DILUTED_EPS' THEN 1 END)=4
                                      THEN SUM(CASE WHEN metric_code='DILUTED_EPS' THEN value_decimal ELSE 0 END) END eps_ttm,
                                 CASE WHEN COUNT(CASE WHEN metric_code='REVENUE' THEN 1 END)=4
                                      THEN SUM(CASE WHEN metric_code='REVENUE' THEN value_decimal ELSE 0 END) END revenue_ttm,
                                 CASE WHEN COUNT(CASE WHEN metric_code='FREE_CASH_FLOW' THEN 1 END)=4
                                      THEN SUM(CASE WHEN metric_code='FREE_CASH_FLOW' THEN value_decimal ELSE 0 END) END fcf_ttm
                          FROM quarterly WHERE quarter_rank<=4
                          GROUP BY instrument_id
                        )
                        SELECT BIN_TO_UUID(i.id) instrumentId, i.symbol,
                          (SELECT q.decision_market_date FROM quote q WHERE q.instrument_id=i.id AND q.decision_quality_status='HEALTHY' ORDER BY q.data_as_of DESC LIMIT 1) marketDate,
                          (SELECT q.last_price FROM quote q WHERE q.instrument_id=i.id AND q.decision_quality_status='HEALTHY' ORDER BY q.data_as_of DESC LIMIT 1) price,
                          t.eps_ttm trailingEps,
                          (SELECT e.mean_value FROM estimate_observation e WHERE e.instrument_id=i.id
                             AND e.estimate_type='EPS' AND e.period_type='ANNUAL'
                             AND e.period_end>=(SELECT q.decision_market_date FROM quote q WHERE q.instrument_id=i.id
                                                AND q.decision_quality_status='HEALTHY' ORDER BY q.data_as_of DESC LIMIT 1)
                           ORDER BY e.period_end,e.data_as_of DESC LIMIT 1) forwardEps,
                          t.revenue_ttm revenue,
                          t.fcf_ttm freeCashFlow,
                          (SELECT m.value_decimal FROM financial_metric_snapshot m WHERE m.instrument_id=i.id AND m.metric_code='CASH' ORDER BY m.data_as_of DESC LIMIT 1) cash,
                          (SELECT m.value_decimal FROM financial_metric_snapshot m WHERE m.instrument_id=i.id AND m.metric_code='TOTAL_DEBT' ORDER BY m.data_as_of DESC LIMIT 1) totalDebt,
                          (SELECT m.value_decimal FROM financial_metric_snapshot m
                             JOIN financial_period mp ON mp.id=m.period_id
                           WHERE m.instrument_id=i.id AND m.metric_code='COMMON_SHARES_OUTSTANDING'
                           ORDER BY mp.end_date DESC,m.data_as_of DESC LIMIT 1) shares,
                          (SELECT m.value_decimal FROM financial_metric_snapshot m WHERE m.instrument_id=i.id AND m.metric_code='REVENUE_YOY' ORDER BY m.data_as_of DESC LIMIT 1) revenueGrowth,
                          (SELECT h.overall_status FROM financial_health_snapshot h WHERE h.instrument_id=i.id ORDER BY h.data_as_of DESC LIMIT 1) health,
                          (SELECT r.overall_revision FROM estimate_revision_snapshot r WHERE r.instrument_id=i.id ORDER BY r.data_as_of DESC LIMIT 1) revision
                        FROM instrument i LEFT JOIN ttm t ON t.instrument_id=i.id
                        WHERE i.active=TRUE AND i.asset_type='EQUITY'
                        """)
                .query(InputRow.class)
                .list();
    }

    public List<ValuationInstrument> valuationInstruments() {
        return jdbc.sql("SELECT BIN_TO_UUID(id) id,symbol FROM instrument WHERE active=TRUE AND asset_type='EQUITY'")
                .query(ValuationInstrument.class)
                .list();
    }

    public List<WeeklyPrice> weeklyPrices(UUID instrumentId, LocalDate from, LocalDate to) {
        return jdbc.sql(
                        """
                        WITH ranked AS (
                          SELECT market_date,close_price,
                                 ROW_NUMBER() OVER (PARTITION BY YEARWEEK(market_date,3) ORDER BY market_date DESC,data_as_of DESC) rn
                          FROM price_bar WHERE instrument_id=UUID_TO_BIN(:instrumentId) AND adjusted=TRUE
                            AND market_date BETWEEN :fromDate AND :toDate
                        )
                        SELECT market_date marketDate,close_price price FROM ranked WHERE rn=1 ORDER BY market_date
                        """)
                .param("instrumentId", instrumentId.toString())
                .param("fromDate", from)
                .param("toDate", to)
                .query(WeeklyPrice.class)
                .list();
    }

    public List<PointInTimeValuationAssembler.MetricPoint> pointInTimeMetrics(UUID instrumentId) {
        return jdbc.sql(
                        """
                        SELECT p.period_type periodType,p.end_date periodEnd,m.metric_code metricCode,
                               m.value_decimal value,m.data_as_of dataAsOf
                        FROM financial_metric_snapshot m JOIN financial_period p ON p.id=m.period_id
                        WHERE m.instrument_id=UUID_TO_BIN(:instrumentId) AND m.metric_code IN
                          ('DILUTED_EPS','REVENUE','FREE_CASH_FLOW','CASH','TOTAL_DEBT','COMMON_SHARES_OUTSTANDING')
                        ORDER BY p.end_date,m.data_as_of
                        """)
                .param("instrumentId", instrumentId.toString())
                .query(PointInTimeValuationAssembler.MetricPoint.class)
                .list();
    }

    public List<PointInTimeValuationAssembler.EstimatePoint> pointInTimeEstimates(UUID instrumentId) {
        return jdbc.sql(
                        """
                        SELECT period_end periodEnd,mean_value meanValue,data_as_of dataAsOf
                        FROM estimate_observation WHERE instrument_id=UUID_TO_BIN(:instrumentId)
                          AND estimate_type='EPS' AND period_type='ANNUAL'
                        ORDER BY period_end,data_as_of
                        """)
                .param("instrumentId", instrumentId.toString())
                .query(PointInTimeValuationAssembler.EstimatePoint.class)
                .list();
    }

    public int saveBootstrapMetrics(UUID instrumentId, LocalDate marketDate, ValuationEngineV2.Metrics value) {
        var checksum = sha256(instrumentId + "|" + marketDate + "|point-in-time-bootstrap-v1|" + value);
        return jdbc.sql(
                        """
                        INSERT IGNORE INTO valuation_metric_history (
                          id,instrument_id,market_date,trailing_pe,forward_pe,ev_sales,fcf_yield,price_sales,
                          market_cap,source,quality,evidence_checksum,data_as_of,created_at
                        ) VALUES (
                          UUID_TO_BIN(:id),UUID_TO_BIN(:instrumentId),:marketDate,:trailingPe,:forwardPe,:evSales,
                          :fcfYield,:priceSales,:marketCap,'point-in-time-bootstrap-v1',:quality,:checksum,:dataAsOf,:now
                        )
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("instrumentId", instrumentId.toString())
                .param("marketDate", marketDate)
                .param("trailingPe", value.trailingPe())
                .param("forwardPe", value.forwardPe())
                .param("evSales", value.evSales())
                .param("fcfYield", value.fcfYield())
                .param("priceSales", value.priceSales())
                .param("marketCap", value.marketCap())
                .param("quality", metricCount(value) >= 2 ? "HEALTHY" : "PARTIAL")
                .param("checksum", checksum)
                .param("dataAsOf", marketDate.atStartOfDay().toInstant(ZoneOffset.UTC))
                .param("now", clock.instant())
                .update();
    }

    public int saveMetrics(UUID instrumentId, LocalDate marketDate, ValuationEngineV2.Metrics value, String quality) {
        var checksum = sha256(instrumentId + "|" + marketDate + "|" + value);
        return jdbc.sql(
                        """
                        INSERT IGNORE INTO valuation_metric_history (
                          id,instrument_id,market_date,trailing_pe,forward_pe,ev_sales,fcf_yield,price_sales,
                          market_cap,source,quality,evidence_checksum,data_as_of,created_at
                        ) VALUES (
                          UUID_TO_BIN(:id),UUID_TO_BIN(:instrumentId),:marketDate,:trailingPe,:forwardPe,:evSales,:fcfYield,:priceSales,
                          :marketCap,'canonical-financials-v2',:quality,:checksum,:now,:now
                        )
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("instrumentId", instrumentId.toString())
                .param("marketDate", marketDate)
                .param("trailingPe", value.trailingPe())
                .param("forwardPe", value.forwardPe())
                .param("evSales", value.evSales())
                .param("fcfYield", value.fcfYield())
                .param("priceSales", value.priceSales())
                .param("marketCap", value.marketCap())
                .param("quality", quality)
                .param("checksum", checksum)
                .param("now", clock.instant())
                .update();
    }

    public List<ValuationEngineV2.Metrics> history(UUID instrumentId, LocalDate from) {
        return jdbc.sql(
                        """
                        SELECT trailing_pe trailingPe,forward_pe forwardPe,ev_sales evSales,fcf_yield fcfYield,
                               price_sales priceSales,market_cap marketCap
                        FROM valuation_metric_history WHERE instrument_id=UUID_TO_BIN(:instrumentId) AND market_date>=:from
                        ORDER BY market_date
                        """)
                .param("instrumentId", instrumentId.toString())
                .param("from", from)
                .query(ValuationEngineV2.Metrics.class)
                .list();
    }

    public int saveAssessment(
            UUID instrumentId,
            ValuationEngineV2.Assessment assessment,
            BigDecimal growthAdjusted,
            String strategyVersion,
            String configHash) {
        var checksum = sha256(instrumentId + "|" + assessment + "|" + growthAdjusted);
        return jdbc.sql(
                        """
                        INSERT IGNORE INTO valuation_assessment_snapshot (
                          id,instrument_id,valuation_state,confidence,own_history_percentile_3y,
                          own_history_percentile_5y,relative_valuation,growth_adjusted_valuation,observation_count,
                          quality,strategy_version,config_hash,evidence_checksum,data_as_of,created_at
                        ) VALUES (
                          UUID_TO_BIN(:id),UUID_TO_BIN(:instrumentId),:state,:confidence,:percentile3,
                          :percentile5,NULL,:growthAdjusted,:count,:quality,:strategyVersion,:configHash,:checksum,:now,:now
                        )
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("instrumentId", instrumentId.toString())
                .param("state", assessment.state().name())
                .param("confidence", assessment.confidence().name())
                .param("percentile3", assessment.ownHistoryPercentile3y())
                .param("percentile5", assessment.ownHistoryPercentile5y())
                .param("growthAdjusted", growthAdjusted)
                .param("count", assessment.observationCount())
                .param("quality", assessment.quality().name())
                .param("strategyVersion", strategyVersion)
                .param("configHash", configHash)
                .param("checksum", checksum)
                .param("now", clock.instant())
                .update();
    }

    public String configHash(String strategyVersion) {
        return jdbc.sql("SELECT config_hash FROM strategy_version WHERE version_code=:version")
                .param("version", strategyVersion)
                .query(String.class)
                .optional()
                .orElse("0".repeat(64));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static int metricCount(ValuationEngineV2.Metrics value) {
        int count = 0;
        if (value.trailingPe() != null) count++;
        if (value.forwardPe() != null) count++;
        if (value.evSales() != null) count++;
        if (value.fcfYield() != null) count++;
        if (value.priceSales() != null) count++;
        return count;
    }

    public record ValuationInstrument(UUID id, String symbol) {}

    public record WeeklyPrice(LocalDate marketDate, BigDecimal price) {}

    public record InputRow(
            UUID instrumentId,
            String symbol,
            LocalDate marketDate,
            BigDecimal price,
            BigDecimal trailingEps,
            BigDecimal forwardEps,
            BigDecimal revenue,
            BigDecimal freeCashFlow,
            BigDecimal cash,
            BigDecimal totalDebt,
            BigDecimal shares,
            BigDecimal revenueGrowth,
            String health,
            String revision) {}
}
