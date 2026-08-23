package com.example.portfolio.analysis.infrastructure;

import com.example.portfolio.analysis.domain.HoldingEvidence;
import com.example.portfolio.analysis.replay.DecisionAsOfContext;
import com.example.portfolio.financialaggregation.CanonicalFinancialAggregationService;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PositionAnalystDataStore {
    private static final MathContext CALCULATION = MathContext.DECIMAL64;
    private final JdbcClient jdbc;
    private final CanonicalFinancialAggregationService financialAggregation;

    public PositionAnalystDataStore(JdbcClient jdbc, CanonicalFinancialAggregationService financialAggregation) {
        this.jdbc = jdbc;
        this.financialAggregation = financialAggregation;
    }

    public AnalystData load(HoldingEvidence evidence, DecisionAsOfContext context) {
        var instrumentId = evidence.instrument().id();
        var prices = prices(instrumentId, context);
        return new AnalystData(
                market(evidence, prices),
                fundamentals(instrumentId, context),
                valuation(instrumentId, context),
                estimates(instrumentId, context),
                technical(instrumentId, evidence, prices, context),
                earnings(evidence.position().id(), instrumentId, context),
                risk(evidence.position().id(), context));
    }

    public CurrentPriceChange currentPriceChange(
            UUID instrumentId, BigDecimal decisionPrice, java.time.Instant cutoff) {
        var latest = jdbc.sql(
                        """
                        SELECT last_price price,data_as_of dataAsOf FROM quote
                        WHERE instrument_id=UUID_TO_BIN(:instrumentId)
                        ORDER BY data_as_of DESC,created_at DESC LIMIT 1
                        """)
                .param("instrumentId", instrumentId.toString())
                .query(CurrentPriceRow.class)
                .optional()
                .orElse(null);
        if (latest == null
                || !latest.dataAsOf().toInstant(java.time.ZoneOffset.UTC).isAfter(cutoff)) return null;
        var change = decisionPrice == null || decisionPrice.signum() == 0
                ? null
                : latest.price().divide(decisionPrice, CALCULATION).subtract(BigDecimal.ONE);
        return new CurrentPriceChange(latest.price(), change, latest.dataAsOf());
    }

    private MarketData market(HoldingEvidence evidence, List<HoldingEvidence.PriceBar> bars) {
        var price = evidence.quote().last();
        if (price == null && !bars.isEmpty()) price = bars.getFirst().close();
        var averageCost = evidence.position().averageCost();
        var quantity = evidence.position().quantity();
        return new MarketData(
                price,
                periodReturn(bars, 1),
                periodReturn(bars, 21),
                periodReturn(bars, 63),
                averageCost,
                price == null || averageCost == null || quantity == null
                        ? null
                        : price.subtract(averageCost).multiply(quantity),
                price == null || averageCost == null || averageCost.signum() == 0
                        ? null
                        : price.subtract(averageCost).divide(averageCost, CALCULATION));
    }

    private FundamentalData fundamentals(UUID instrumentId, DecisionAsOfContext context) {
        var metrics = new HashMap<String, BigDecimal>();
        LocalDateTime dataAsOf = null;
        String quality = null;
        for (var row : jdbc.sql(
                        """
                        SELECT metric_code metricCode,value_decimal value,data_as_of dataAsOf,quality
                        FROM (
                          SELECT m.*,ROW_NUMBER() OVER (
                            PARTITION BY metric_code ORDER BY data_as_of DESC,created_at DESC,id DESC) rn
                          FROM financial_metric_snapshot m
                          WHERE instrument_id=UUID_TO_BIN(:instrumentId) AND data_as_of<=:cutoff
                        ) latest WHERE rn=1
                        """)
                .param("instrumentId", instrumentId.toString())
                .param("cutoff", context.dataCutoff())
                .query(MetricRow.class)
                .list()) {
            metrics.put(row.metricCode(), row.value());
            if (dataAsOf == null || row.dataAsOf().isAfter(dataAsOf)) dataAsOf = row.dataAsOf();
            quality = combineQuality(quality, row.quality());
        }
        var ttm = financialAggregation.ttm(instrumentId, context.dataCutoff());
        var revenueTtm = ttm.revenue();
        var epsTtm = ttm.eps();
        var fcfTtm = ttm.freeCashFlow();
        dataAsOf =
                latest(dataAsOf, aggregateDataAsOf(revenueTtm), aggregateDataAsOf(epsTtm), aggregateDataAsOf(fcfTtm));
        return new FundamentalData(
                aggregateValue(revenueTtm),
                metric(metrics, "REVENUE_YOY"),
                metric(metrics, "REVENUE_3Y_CAGR"),
                aggregateValue(epsTtm),
                metric(metrics, "EPS_YOY"),
                metric(metrics, "OPERATING_MARGIN"),
                metric(metrics, "OPERATING_MARGIN_YOY_CHANGE"),
                aggregateValue(fcfTtm),
                metric(metrics, "FCF_MARGIN"),
                metric(metrics, "FCF_CONVERSION"),
                metric(metrics, "NET_CASH"),
                metric(metrics, "NET_DEBT_TO_FCF"),
                metric(metrics, "CURRENT_RATIO"),
                metric(metrics, "SHARE_DILUTION_YOY"),
                dataAsOf,
                quality);
    }

    private ValuationData valuation(UUID instrumentId, DecisionAsOfContext context) {
        var assessment = jdbc.sql(
                        """
                        SELECT valuation_state state,confidence,own_history_percentile_3y percentile3y,
                               own_history_percentile_5y percentile5y,relative_valuation relativeValuation,
                               observation_count observationCount,quality,data_as_of dataAsOf
                        FROM valuation_assessment_snapshot WHERE instrument_id=UUID_TO_BIN(:instrumentId)
                          AND data_as_of<=:cutoff AND strategy_version=:strategyVersion
                        ORDER BY data_as_of DESC,created_at DESC LIMIT 1
                        """)
                .param("instrumentId", instrumentId.toString())
                .param("cutoff", context.dataCutoff())
                .param("strategyVersion", context.strategyVersion())
                .query(ValuationAssessmentRow.class)
                .optional()
                .orElse(null);
        var metrics = jdbc.sql(
                        """
                        SELECT trailing_pe trailingPe,forward_pe forwardPe,ev_sales evSales,
                               price_sales priceSales,fcf_yield fcfYield,data_as_of dataAsOf
                        FROM valuation_metric_history WHERE instrument_id=UUID_TO_BIN(:instrumentId)
                          AND market_date<=:marketDate AND data_as_of<=:cutoff
                        ORDER BY market_date DESC,data_as_of DESC,created_at DESC LIMIT 1
                        """)
                .param("instrumentId", instrumentId.toString())
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .query(ValuationMetricRow.class)
                .optional()
                .orElse(null);
        return new ValuationData(
                assessment == null ? null : assessment.state(),
                metrics == null ? null : metrics.trailingPe(),
                metrics == null ? null : metrics.forwardPe(),
                metrics == null ? null : metrics.evSales(),
                metrics == null ? null : metrics.priceSales(),
                metrics == null ? null : metrics.fcfYield(),
                assessment == null ? null : assessment.percentile3y(),
                assessment == null ? null : assessment.percentile5y(),
                assessment == null ? 0 : assessment.observationCount(),
                assessment == null ? null : assessment.confidence(),
                assessment == null ? null : assessment.relativeValuation(),
                assessment == null ? null : assessment.quality(),
                latest(assessment == null ? null : assessment.dataAsOf(), metrics == null ? null : metrics.dataAsOf()));
    }

    private EstimateData estimates(UUID instrumentId, DecisionAsOfContext context) {
        var observations = jdbc.sql(
                        """
                        SELECT estimate_type estimateType,mean_value meanValue,high_value highValue,
                               low_value lowValue,analyst_count analystCount,quality,data_as_of dataAsOf
                        FROM (
                          SELECT e.*,ROW_NUMBER() OVER (
                            PARTITION BY estimate_type ORDER BY period_end,data_as_of DESC,created_at DESC) rn
                          FROM estimate_observation e WHERE instrument_id=UUID_TO_BIN(:instrumentId)
                            AND period_type='ANNUAL' AND period_end>=:marketDate AND data_as_of<=:cutoff
                        ) latest WHERE rn=1
                        """)
                .param("instrumentId", instrumentId.toString())
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .query(EstimateObservationRow.class)
                .list();
        var byType = new HashMap<String, EstimateObservationRow>();
        observations.forEach(row -> byType.put(row.estimateType(), row));
        var revision = jdbc.sql(
                        """
                        SELECT eps_change_30d eps30d,eps_change_90d eps90d,
                               revenue_change_30d revenue30d,revenue_change_90d revenue90d,
                               analyst_count analystCount,dispersion,overall_revision state,quality,data_as_of dataAsOf
                        FROM estimate_revision_snapshot WHERE instrument_id=UUID_TO_BIN(:instrumentId)
                          AND data_as_of<=:cutoff
                        ORDER BY data_as_of DESC,created_at DESC LIMIT 1
                        """)
                .param("instrumentId", instrumentId.toString())
                .param("cutoff", context.dataCutoff())
                .query(EstimateRevisionRow.class)
                .optional()
                .orElse(null);
        var eps = byType.get("EPS");
        var revenue = byType.get("REVENUE");
        var analystCount = revision == null ? null : revision.analystCount();
        if (analystCount == null && eps != null) analystCount = eps.analystCount();
        return new EstimateData(
                eps == null ? null : eps.meanValue(),
                revenue == null ? null : revenue.meanValue(),
                revision == null ? null : revision.eps30d(),
                revision == null ? null : revision.eps90d(),
                revision == null ? null : revision.revenue30d(),
                revision == null ? null : revision.revenue90d(),
                analystCount,
                eps == null ? null : eps.highValue(),
                eps == null ? null : eps.lowValue(),
                revision == null ? null : revision.dispersion(),
                revision == null ? null : revision.state(),
                combineQuality(
                        revision == null ? null : revision.quality(),
                        eps == null ? null : eps.quality(),
                        revenue == null ? null : revenue.quality()),
                latest(
                        revision == null ? null : revision.dataAsOf(),
                        eps == null ? null : eps.dataAsOf(),
                        revenue == null ? null : revenue.dataAsOf()));
    }

    private TechnicalData technical(
            UUID instrumentId,
            HoldingEvidence evidence,
            List<HoldingEvidence.PriceBar> bars,
            DecisionAsOfContext context) {
        var values = new HashMap<String, BigDecimal>();
        LocalDateTime dataAsOf = null;
        for (var row : jdbc.sql(
                        """
                        SELECT indicator_code code,value_double value,data_as_of dataAsOf
                        FROM indicator_snapshot s WHERE instrument_id=UUID_TO_BIN(:instrumentId)
                          AND status='READY' AND market_date=(SELECT MAX(x.market_date)
                            FROM indicator_snapshot x WHERE x.instrument_id=s.instrument_id
                              AND x.market_date<=:marketDate AND x.data_as_of<=:cutoff)
                          AND s.data_as_of<=:cutoff
                          AND indicator_code IN ('SMA_20','SMA_50','SMA_200','RSI_14','ATR_14',
                                                 'MACD_12_26_9','REALIZED_VOL_20')
                        """)
                .param("instrumentId", instrumentId.toString())
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .query(IndicatorValueRow.class)
                .list()) {
            values.put(row.code(), row.value());
            dataAsOf = latest(dataAsOf, row.dataAsOf());
        }
        var price = evidence.quote().last();
        if (price == null && !evidence.completedBars().isEmpty())
            price = evidence.completedBars().getFirst().close();
        var atr = values.get("ATR_14");
        var macd = values.get("MACD_12_26_9");
        var benchmarkSpy = prices("SPY", context);
        var benchmarkQqq = prices("QQQ", context);
        return new TechnicalData(
                values.get("SMA_20"),
                values.get("SMA_50"),
                values.get("SMA_200"),
                distance(price, values.get("SMA_20")),
                distance(price, values.get("SMA_50")),
                distance(price, values.get("SMA_200")),
                values.get("RSI_14"),
                macd == null ? null : macd.signum() > 0 ? "POSITIVE" : macd.signum() < 0 ? "NEGATIVE" : "NEUTRAL",
                atr,
                price == null || price.signum() == 0 || atr == null ? null : atr.divide(price, CALCULATION),
                values.get("REALIZED_VOL_20"),
                breakout20(bars),
                drawdown52Week(bars),
                relativeReturn(bars, benchmarkSpy, 21),
                relativeReturn(bars, benchmarkSpy, 63),
                relativeReturn(bars, benchmarkSpy, 126),
                relativeReturn(bars, benchmarkQqq, 21),
                relativeReturn(bars, benchmarkQqq, 63),
                relativeReturn(bars, benchmarkQqq, 126),
                dataAsOf);
    }

    private EarningsData earnings(UUID positionId, UUID instrumentId, DecisionAsOfContext context) {
        var risk = jdbc.sql(
                        """
                        SELECT next_event_at nextEventAt,event_risk eventRisk,
                               median_abs_move_fraction medianAbsMove,p75_abs_move_fraction p75AbsMove,
                               worst_downside_gap_fraction worstDownsideGap,profit_cushion_r currentR,
                               action policyAction,
                               CASE WHEN event_count>0 THEN 'HEALTHY' ELSE 'PARTIAL' END quality,
                               data_as_of dataAsOf
                        FROM earnings_risk_snapshot WHERE position_id=UUID_TO_BIN(:positionId)
                          AND data_as_of<=:cutoff AND strategy_version=:strategyVersion
                        ORDER BY data_as_of DESC,created_at DESC LIMIT 1
                        """)
                .param("positionId", positionId.toString())
                .param("cutoff", context.dataCutoff())
                .param("strategyVersion", context.strategyVersion())
                .query(EarningsRiskRow.class)
                .optional()
                .orElse(null);
        var event = jdbc.sql(
                        """
                        SELECT event_at nextEventAt,timing,data_as_of dataAsOf,quality
                        FROM earnings_event WHERE instrument_id=UUID_TO_BIN(:instrumentId)
                          AND event_at>=:cutoff AND data_as_of<=:cutoff ORDER BY event_at LIMIT 1
                        """)
                .param("instrumentId", instrumentId.toString())
                .param("cutoff", context.dataCutoff())
                .query(EarningsEventRow.class)
                .optional()
                .orElse(null);
        var reactions = jdbc.sql(
                        """
                        SELECT return_1d return1d,return_3d return3d,return_5d return5d
                        FROM earnings_reaction_snapshot WHERE instrument_id=UUID_TO_BIN(:instrumentId)
                          AND data_as_of<=:cutoff
                        ORDER BY data_as_of DESC
                        """)
                .param("instrumentId", instrumentId.toString())
                .param("cutoff", context.dataCutoff())
                .query(ReactionRow.class)
                .list();
        return new EarningsData(
                risk != null && risk.nextEventAt() != null
                        ? risk.nextEventAt()
                        : event == null ? null : event.nextEventAt(),
                event == null ? null : event.timing(),
                risk == null ? null : risk.eventRisk(),
                risk == null ? null : risk.medianAbsMove(),
                risk == null ? null : risk.p75AbsMove(),
                risk == null ? null : risk.worstDownsideGap(),
                reactions.stream()
                        .map(ReactionRow::return1d)
                        .filter(java.util.Objects::nonNull)
                        .toList(),
                reactions.stream()
                        .map(ReactionRow::return3d)
                        .filter(java.util.Objects::nonNull)
                        .toList(),
                reactions.stream()
                        .map(ReactionRow::return5d)
                        .filter(java.util.Objects::nonNull)
                        .toList(),
                risk == null ? null : risk.currentR(),
                risk == null ? null : risk.policyAction(),
                combineQuality(risk == null ? null : risk.quality(), event == null ? null : event.quality()),
                latest(risk == null ? null : risk.dataAsOf(), event == null ? null : event.dataAsOf()));
    }

    private RiskData risk(UUID positionId, DecisionAsOfContext context) {
        return jdbc.sql(
                        """
                        SELECT risk_amount plannedRiskDollar,open_risk_fraction plannedRiskFraction,
                               quality_status quality,data_as_of dataAsOf
                        FROM position_risk_snapshot WHERE position_id=UUID_TO_BIN(:positionId)
                          AND data_as_of<=:cutoff AND strategy_version=:strategyVersion
                        ORDER BY data_as_of DESC,created_at DESC LIMIT 1
                        """)
                .param("positionId", positionId.toString())
                .param("cutoff", context.dataCutoff())
                .param("strategyVersion", context.strategyVersion())
                .query(RiskData.class)
                .optional()
                .orElse(new RiskData(null, null, "MISSING", null));
    }

    private List<HoldingEvidence.PriceBar> prices(String symbol, DecisionAsOfContext context) {
        return jdbc
                .sql(
                        """
                        SELECT marketDate,close,volume FROM (
                          SELECT b.market_date marketDate,b.close_price close,b.volume,
                                 ROW_NUMBER() OVER (PARTITION BY b.market_date
                                   ORDER BY b.data_as_of DESC,b.created_at DESC,b.id DESC) rn
                          FROM price_bar b JOIN instrument i ON i.id=b.instrument_id
                          WHERE i.symbol=:symbol AND b.adjusted=TRUE AND b.timeframe='1D'
                            AND b.market_date<=:marketDate AND b.data_as_of<=:cutoff
                        ) canonical WHERE rn=1 ORDER BY marketDate DESC LIMIT 130
                        """)
                .param("symbol", symbol)
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .query(PriceRow.class)
                .list()
                .stream()
                .map(row -> new HoldingEvidence.PriceBar(row.marketDate(), row.close(), row.volume(), true))
                .toList();
    }

    private List<HoldingEvidence.PriceBar> prices(UUID instrumentId, DecisionAsOfContext context) {
        return jdbc
                .sql(
                        """
                        SELECT marketDate,close,volume FROM (
                          SELECT b.market_date marketDate,b.close_price close,b.volume,
                                 ROW_NUMBER() OVER (PARTITION BY b.market_date
                                   ORDER BY b.data_as_of DESC,b.created_at DESC,b.id DESC) rn
                          FROM price_bar b WHERE b.instrument_id=UUID_TO_BIN(:instrumentId)
                            AND b.adjusted=TRUE AND b.timeframe='1D'
                            AND b.market_date<=:marketDate AND b.data_as_of<=:cutoff
                        ) canonical WHERE rn=1 ORDER BY marketDate DESC LIMIT 253
                        """)
                .param("instrumentId", instrumentId.toString())
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .query(PriceRow.class)
                .list()
                .stream()
                .map(row -> new HoldingEvidence.PriceBar(row.marketDate(), row.close(), row.volume(), true))
                .toList();
    }

    static BigDecimal periodReturn(List<HoldingEvidence.PriceBar> bars, int sessions) {
        if (bars.size() <= sessions
                || bars.getFirst().close() == null
                || bars.get(sessions).close().signum() == 0) return null;
        return bars.getFirst()
                .close()
                .divide(bars.get(sessions).close(), CALCULATION)
                .subtract(BigDecimal.ONE);
    }

    static BigDecimal relativeReturn(
            List<HoldingEvidence.PriceBar> asset, List<HoldingEvidence.PriceBar> benchmark, int sessions) {
        var assetReturn = periodReturn(asset, sessions);
        var benchmarkReturn = periodReturn(benchmark, sessions);
        if (assetReturn == null || benchmarkReturn == null) return null;
        return BigDecimal.ONE
                .add(assetReturn)
                .divide(BigDecimal.ONE.add(benchmarkReturn), CALCULATION)
                .subtract(BigDecimal.ONE);
    }

    private static String breakout20(List<HoldingEvidence.PriceBar> bars) {
        if (bars.size() < 21) return null;
        var priorHigh = bars.subList(1, 21).stream()
                .map(HoldingEvidence.PriceBar::close)
                .max(BigDecimal::compareTo)
                .orElse(null);
        return priorHigh == null ? null : bars.getFirst().close().compareTo(priorHigh) > 0 ? "BREAKOUT" : "NO_BREAKOUT";
    }

    private static BigDecimal drawdown52Week(List<HoldingEvidence.PriceBar> bars) {
        if (bars.isEmpty()) return null;
        var sample = bars.subList(0, Math.min(252, bars.size()));
        var high = sample.stream()
                .map(HoldingEvidence.PriceBar::close)
                .max(BigDecimal::compareTo)
                .orElse(null);
        return high == null || high.signum() == 0
                ? null
                : bars.getFirst().close().divide(high, CALCULATION).subtract(BigDecimal.ONE);
    }

    private static BigDecimal distance(BigDecimal price, BigDecimal average) {
        return price == null || average == null || average.signum() == 0
                ? null
                : price.divide(average, CALCULATION).subtract(BigDecimal.ONE);
    }

    private static BigDecimal metric(Map<String, BigDecimal> metrics, String name) {
        return metrics.get(name);
    }

    private static BigDecimal aggregateValue(
            com.example.portfolio.financialaggregation.CanonicalFinancialAggregation.Aggregate value) {
        return value == null ? null : value.value();
    }

    private static LocalDateTime aggregateDataAsOf(
            com.example.portfolio.financialaggregation.CanonicalFinancialAggregation.Aggregate value) {
        return value == null ? null : LocalDateTime.ofInstant(value.dataAsOf(), java.time.ZoneOffset.UTC);
    }

    private static String combineQuality(String... values) {
        String result = null;
        for (var value : values) {
            if (value == null) continue;
            if ("MISSING".equals(value)) return "MISSING";
            if (!"HEALTHY".equals(value)) result = "PARTIAL";
            else if (result == null) result = "HEALTHY";
        }
        return result;
    }

    private static LocalDateTime latest(LocalDateTime... values) {
        LocalDateTime result = null;
        for (var value : values) if (value != null && (result == null || value.isAfter(result))) result = value;
        return result;
    }

    public record AnalystData(
            MarketData market,
            FundamentalData fundamentals,
            ValuationData valuation,
            EstimateData estimates,
            TechnicalData technical,
            EarningsData earnings,
            RiskData risk) {}

    public record MarketData(
            BigDecimal price,
            BigDecimal dayChangePct,
            BigDecimal oneMonthReturn,
            BigDecimal threeMonthReturn,
            BigDecimal averageCost,
            BigDecimal unrealizedPnlDollar,
            BigDecimal unrealizedPnlPct) {}

    public record FundamentalData(
            BigDecimal revenueTtm,
            BigDecimal revenueYoy,
            BigDecimal revenue3yCagr,
            BigDecimal epsTtm,
            BigDecimal epsYoy,
            BigDecimal operatingMargin,
            BigDecimal operatingMarginYoyChange,
            BigDecimal fcfTtm,
            BigDecimal fcfMargin,
            BigDecimal fcfConversion,
            BigDecimal netCash,
            BigDecimal netDebtToFcf,
            BigDecimal currentRatio,
            BigDecimal shareDilutionYoy,
            LocalDateTime dataAsOf,
            String quality) {}

    public record ValuationData(
            String state,
            BigDecimal trailingPeTtm,
            BigDecimal forwardPeFy1,
            BigDecimal evSalesTtm,
            BigDecimal priceSalesTtm,
            BigDecimal fcfYieldTtm,
            BigDecimal historyPercentile3y,
            BigDecimal historyPercentile5y,
            int observationCount,
            String confidence,
            BigDecimal relativeValuation,
            String quality,
            LocalDateTime dataAsOf) {}

    public record EstimateData(
            BigDecimal fy1Eps,
            BigDecimal fy1Revenue,
            BigDecimal epsRevision30d,
            BigDecimal epsRevision90d,
            BigDecimal revenueRevision30d,
            BigDecimal revenueRevision90d,
            Integer analystCount,
            BigDecimal epsHigh,
            BigDecimal epsLow,
            BigDecimal dispersion,
            String state,
            String quality,
            LocalDateTime dataAsOf) {}

    public record TechnicalData(
            BigDecimal sma20,
            BigDecimal sma50,
            BigDecimal sma200,
            BigDecimal distanceFromSma20,
            BigDecimal distanceFromSma50,
            BigDecimal distanceFromSma200,
            BigDecimal rsi14,
            String macdState,
            BigDecimal atr14,
            BigDecimal atrPercent,
            BigDecimal realizedVolatility,
            String breakout20d,
            BigDecimal drawdown52Week,
            BigDecimal relativeStrengthSpy1m,
            BigDecimal relativeStrengthSpy3m,
            BigDecimal relativeStrengthSpy6m,
            BigDecimal relativeStrengthQqq1m,
            BigDecimal relativeStrengthQqq3m,
            BigDecimal relativeStrengthQqq6m,
            LocalDateTime dataAsOf) {}

    public record EarningsData(
            LocalDateTime nextEarningsAt,
            String sessionType,
            String eventRisk,
            BigDecimal historicalMedianAbsMove,
            BigDecimal historicalP75AbsMove,
            BigDecimal worstDownsideGap,
            List<BigDecimal> reaction1d,
            List<BigDecimal> reaction3d,
            List<BigDecimal> reaction5d,
            BigDecimal currentR,
            String policyAction,
            String quality,
            LocalDateTime dataAsOf) {}

    public record RiskData(
            BigDecimal plannedRiskDollar, BigDecimal plannedRiskFraction, String quality, LocalDateTime dataAsOf) {}

    public record CurrentPriceChange(BigDecimal price, BigDecimal changeSinceAnalysis, LocalDateTime dataAsOf) {}

    private record MetricRow(String metricCode, BigDecimal value, LocalDateTime dataAsOf, String quality) {}

    private record CurrentPriceRow(BigDecimal price, LocalDateTime dataAsOf) {}

    private record ValuationAssessmentRow(
            String state,
            String confidence,
            BigDecimal percentile3y,
            BigDecimal percentile5y,
            BigDecimal relativeValuation,
            int observationCount,
            String quality,
            LocalDateTime dataAsOf) {}

    private record ValuationMetricRow(
            BigDecimal trailingPe,
            BigDecimal forwardPe,
            BigDecimal evSales,
            BigDecimal priceSales,
            BigDecimal fcfYield,
            LocalDateTime dataAsOf) {}

    private record EstimateObservationRow(
            String estimateType,
            BigDecimal meanValue,
            BigDecimal highValue,
            BigDecimal lowValue,
            Integer analystCount,
            String quality,
            LocalDateTime dataAsOf) {}

    private record EstimateRevisionRow(
            BigDecimal eps30d,
            BigDecimal eps90d,
            BigDecimal revenue30d,
            BigDecimal revenue90d,
            Integer analystCount,
            BigDecimal dispersion,
            String state,
            String quality,
            LocalDateTime dataAsOf) {}

    private record IndicatorValueRow(String code, BigDecimal value, LocalDateTime dataAsOf) {}

    private record EarningsRiskRow(
            LocalDateTime nextEventAt,
            String eventRisk,
            BigDecimal medianAbsMove,
            BigDecimal p75AbsMove,
            BigDecimal worstDownsideGap,
            BigDecimal currentR,
            String policyAction,
            String quality,
            LocalDateTime dataAsOf) {}

    private record EarningsEventRow(LocalDateTime nextEventAt, String timing, LocalDateTime dataAsOf, String quality) {}

    private record ReactionRow(BigDecimal return1d, BigDecimal return3d, BigDecimal return5d) {}

    private record PriceRow(LocalDate marketDate, BigDecimal close, BigDecimal volume) {}
}
