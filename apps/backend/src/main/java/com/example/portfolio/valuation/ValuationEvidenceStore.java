package com.example.portfolio.valuation;

import com.example.portfolio.analysis.replay.DecisionAsOfContext;
import com.example.portfolio.financialaggregation.CanonicalFinancialAggregationService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ValuationEvidenceStore {
    private final JdbcClient jdbc;
    private final Clock clock;
    private final CanonicalFinancialAggregationService financialAggregation;

    public ValuationEvidenceStore(
            JdbcClient jdbc, Clock clock, CanonicalFinancialAggregationService financialAggregation) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.financialAggregation = financialAggregation;
    }

    public List<InputRow> inputs(DecisionAsOfContext context) {
        return valuationInstruments().stream()
                .map(instrument -> input(instrument, context))
                .toList();
    }

    private InputRow input(ValuationInstrument instrument, DecisionAsOfContext context) {
        var price = jdbc.sql(
                        """
                        SELECT decision_market_date marketDate,last_price price
                        FROM quote WHERE instrument_id=UUID_TO_BIN(:instrumentId)
                          AND decision_quality_status='HEALTHY' AND decision_market_date<=:marketDate
                          AND data_as_of<=:cutoff
                        ORDER BY decision_market_date DESC,data_as_of DESC,created_at DESC LIMIT 1
                        """)
                .param("instrumentId", instrument.id().toString())
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .query(PriceInput.class)
                .optional()
                .orElse(null);
        if (price == null) {
            return new InputRow(
                    instrument.id(),
                    instrument.symbol(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
        }
        var assembled = new PointInTimeValuationAssembler()
                .assemble(
                        price.marketDate(),
                        context.dataCutoff(),
                        context.dataCutoff(),
                        pointInTimeMetrics(instrument.id()),
                        pointInTimeEstimates(instrument.id()))
                .inputs();
        var ttm = financialAggregation.ttm(instrument.id(), context.dataCutoff());
        var support = jdbc.sql(
                        """
                        SELECT
                          (SELECT m.value_decimal FROM financial_metric_snapshot m
                           WHERE m.instrument_id=UUID_TO_BIN(:instrumentId) AND m.metric_code='REVENUE_YOY'
                             AND m.data_as_of<=:cutoff ORDER BY m.data_as_of DESC,m.created_at DESC LIMIT 1) revenueGrowth,
                          (SELECT h.overall_status FROM financial_health_snapshot h
                           WHERE h.instrument_id=UUID_TO_BIN(:instrumentId) AND h.strategy_version=:strategyVersion
                             AND h.data_as_of<=:cutoff ORDER BY h.data_as_of DESC,h.created_at DESC LIMIT 1) health,
                          (SELECT r.overall_revision FROM estimate_revision_snapshot r
                           WHERE r.instrument_id=UUID_TO_BIN(:instrumentId) AND r.data_as_of<=:cutoff
                           ORDER BY r.data_as_of DESC,r.created_at DESC LIMIT 1) revision
                        """)
                .param("instrumentId", instrument.id().toString())
                .param("strategyVersion", context.strategyVersion())
                .param("cutoff", context.dataCutoff())
                .query(SupportInput.class)
                .single();
        return new InputRow(
                instrument.id(),
                instrument.symbol(),
                price.marketDate(),
                price.price(),
                value(ttm.eps()),
                assembled.forwardEps(),
                value(ttm.revenue()),
                value(ttm.freeCashFlow()),
                assembled.cash(),
                assembled.totalDebt(),
                assembled.commonShares(),
                support.revenueGrowth(),
                support.health(),
                support.revision());
    }

    private static BigDecimal value(
            com.example.portfolio.financialaggregation.CanonicalFinancialAggregation.Aggregate value) {
        return value == null ? null : value.value();
    }

    public List<ValuationInstrument> valuationInstruments() {
        return jdbc.sql("SELECT BIN_TO_UUID(id) id,symbol FROM instrument WHERE active=TRUE AND asset_type='EQUITY'")
                .query(ValuationInstrument.class)
                .list();
    }

    public List<WeeklyPrice> weeklyPrices(
            UUID instrumentId, LocalDate from, LocalDate to, java.time.Instant decisionCutoff) {
        return jdbc.sql(
                        """
                        WITH ranked AS (
                          SELECT market_date,close_price,data_as_of,
                                 ROW_NUMBER() OVER (PARTITION BY YEARWEEK(market_date,3) ORDER BY market_date DESC,data_as_of DESC) rn
                          FROM price_bar WHERE instrument_id=UUID_TO_BIN(:instrumentId) AND adjusted=TRUE
                            AND market_date BETWEEN :fromDate AND :toDate AND data_as_of<=:cutoff
                        )
                        SELECT market_date marketDate,close_price price,data_as_of dataAsOf
                        FROM ranked WHERE rn=1 ORDER BY market_date
                        """)
                .param("instrumentId", instrumentId.toString())
                .param("fromDate", from)
                .param("toDate", to)
                .param("cutoff", decisionCutoff)
                .query((result, rowNumber) -> new WeeklyPrice(
                        result.getObject("marketDate", LocalDate.class),
                        result.getBigDecimal("price"),
                        result.getTimestamp("dataAsOf").toInstant()))
                .list();
    }

    public List<PointInTimeValuationAssembler.MetricPoint> pointInTimeMetrics(UUID instrumentId) {
        return jdbc.sql(
                        """
                        SELECT p.period_type periodType,p.end_date periodEnd,p.fiscal_year fiscalYear,
                               p.fiscal_quarter fiscalQuarter,p.quality periodQuality,m.metric_code metricCode,
                               m.value_decimal value,m.quality metricQuality,m.data_as_of dataAsOf
                        FROM financial_metric_snapshot m JOIN financial_period p ON p.id=m.period_id
                        WHERE m.instrument_id=UUID_TO_BIN(:instrumentId) AND m.metric_code IN
                          ('DILUTED_EPS','REVENUE','FREE_CASH_FLOW','CASH','TOTAL_DEBT','COMMON_SHARES_OUTSTANDING')
                        ORDER BY p.end_date,m.data_as_of
                        """)
                .param("instrumentId", instrumentId.toString())
                .query((result, rowNumber) -> new PointInTimeValuationAssembler.MetricPoint(
                        result.getString("periodType"),
                        result.getObject("periodEnd", LocalDate.class),
                        result.getObject("fiscalYear", Integer.class),
                        result.getObject("fiscalQuarter", Integer.class),
                        result.getString("metricCode"),
                        result.getBigDecimal("value"),
                        result.getString("periodQuality"),
                        result.getString("metricQuality"),
                        result.getTimestamp("dataAsOf").toInstant()))
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
                .query((result, rowNumber) -> new PointInTimeValuationAssembler.EstimatePoint(
                        result.getObject("periodEnd", LocalDate.class),
                        result.getBigDecimal("meanValue"),
                        result.getTimestamp("dataAsOf").toInstant()))
                .list();
    }

    public int saveBootstrapMetrics(
            UUID instrumentId,
            LocalDate marketDate,
            ValuationEngineV2.Metrics value,
            java.time.Instant evidenceDataAsOf) {
        var checksum = sha256(
                instrumentId + "|" + marketDate + "|point-in-time-bootstrap-v2|" + value + "|" + evidenceDataAsOf);
        return jdbc.sql(
                        """
                        INSERT IGNORE INTO valuation_metric_history (
                          id,instrument_id,market_date,trailing_pe,forward_pe,ev_sales,fcf_yield,price_sales,
                          market_cap,source,quality,evidence_checksum,data_as_of,created_at
                        ) VALUES (
                          UUID_TO_BIN(:id),UUID_TO_BIN(:instrumentId),:marketDate,:trailingPe,:forwardPe,:evSales,
                          :fcfYield,:priceSales,:marketCap,'point-in-time-bootstrap-v2',:quality,:checksum,:dataAsOf,:now
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
                .param("quality", ValuationEngineV2.availableFamilyCount(value) >= 2 ? "HEALTHY" : "PARTIAL")
                .param("checksum", checksum)
                .param("dataAsOf", evidenceDataAsOf)
                .param("now", clock.instant())
                .update();
    }

    public int saveMetrics(
            UUID instrumentId,
            LocalDate marketDate,
            ValuationEngineV2.Metrics value,
            String quality,
            java.time.Instant dataAsOf) {
        var checksum = sha256(instrumentId + "|" + marketDate + "|" + value);
        return jdbc.sql(
                        """
                        INSERT IGNORE INTO valuation_metric_history (
                          id,instrument_id,market_date,trailing_pe,forward_pe,ev_sales,fcf_yield,price_sales,
                          market_cap,source,quality,evidence_checksum,data_as_of,created_at
                        ) VALUES (
                          UUID_TO_BIN(:id),UUID_TO_BIN(:instrumentId),:marketDate,:trailingPe,:forwardPe,:evSales,:fcfYield,:priceSales,
                          :marketCap,'canonical-financials-v2',:quality,:checksum,:dataAsOf,:now
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
                .param("dataAsOf", dataAsOf)
                .param("now", clock.instant())
                .update();
    }

    public List<ValuationEngineV2.Metrics> history(
            UUID instrumentId, LocalDate from, LocalDate through, java.time.Instant decisionCutoff) {
        return jdbc.sql(
                        """
                        SELECT trailing_pe trailingPe,forward_pe forwardPe,ev_sales evSales,fcf_yield fcfYield,
                               price_sales priceSales,market_cap marketCap
                        FROM valuation_metric_history WHERE instrument_id=UUID_TO_BIN(:instrumentId)
                          AND market_date BETWEEN :from AND :through AND data_as_of<=:cutoff
                        ORDER BY market_date
                        """)
                .param("instrumentId", instrumentId.toString())
                .param("from", from)
                .param("through", through)
                .param("cutoff", decisionCutoff)
                .query(ValuationEngineV2.Metrics.class)
                .list();
    }

    public int saveAssessment(
            UUID instrumentId,
            ValuationEngineV2.Assessment assessment,
            BigDecimal growthAdjusted,
            String strategyVersion,
            String configHash,
            java.time.Instant dataAsOf) {
        var checksum = sha256(instrumentId + "|" + assessment + "|" + growthAdjusted);
        return jdbc.sql(
                        """
                        INSERT IGNORE INTO valuation_assessment_snapshot (
                          id,instrument_id,valuation_state,confidence,own_history_percentile_3y,
                          own_history_percentile_5y,relative_valuation,growth_adjusted_valuation,observation_count,
                          quality,strategy_version,config_hash,evidence_checksum,data_as_of,created_at
                        ) VALUES (
                          UUID_TO_BIN(:id),UUID_TO_BIN(:instrumentId),:state,:confidence,:percentile3,
                           :percentile5,NULL,:growthAdjusted,:count,:quality,:strategyVersion,:configHash,:checksum,:dataAsOf,:now
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
                .param("dataAsOf", dataAsOf)
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

    public DecisionAsOfContext analysisContext(UUID analysisRunId) {
        return jdbc.sql(
                        """
                        SELECT market_date marketDate,decision_cutoff decisionCutoff,strategy_version strategyVersion
                        FROM portfolio_analysis_run WHERE id=UUID_TO_BIN(:runId)
                        """)
                .param("runId", analysisRunId.toString())
                .query(RunContext.class)
                .optional()
                .map(value -> {
                    if (value.decisionCutoff() == null) {
                        throw new IllegalStateException("Analysis run decision cutoff is unavailable");
                    }
                    return new DecisionAsOfContext(
                            value.marketDate(),
                            value.decisionCutoff().toInstant(java.time.ZoneOffset.UTC),
                            value.strategyVersion());
                })
                .orElseThrow(() -> new IllegalArgumentException("Analysis run is unavailable"));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record ValuationInstrument(UUID id, String symbol) {}

    public record WeeklyPrice(LocalDate marketDate, BigDecimal price, java.time.Instant dataAsOf) {}

    record PriceInput(LocalDate marketDate, BigDecimal price) {}

    record SupportInput(BigDecimal revenueGrowth, String health, String revision) {}

    record RunContext(LocalDate marketDate, java.time.LocalDateTime decisionCutoff, String strategyVersion) {}

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
