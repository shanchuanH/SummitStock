package com.example.portfolio.valuation;

import com.example.portfolio.estimates.EstimateRevisionEngine;
import com.example.portfolio.market.provider.ProviderModels;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ValuationEngineV2 {
    public static final int MINIMUM_WEEKLY_HISTORY_OBSERVATIONS = 220;

    public Assessment assess(Input input) {
        var percentile3y = medianPercentile(input.current(), input.history3y());
        var percentile5y = medianPercentile(input.current(), input.history5y());
        if (percentile3y == null && percentile5y == null) return Assessment.missing();
        var percentile = percentile3y != null ? percentile3y : percentile5y;
        var count = input.history5y().size();
        var confidence = count >= MINIMUM_WEEKLY_HISTORY_OBSERVATIONS
                        && historicalFamilyCount(input.current(), input.history3y()) >= 2
                ? Confidence.HIGH
                : Confidence.LOW;
        var state = state(percentile);
        if (state == ValuationState.DEEP_DISCOUNT
                && (confidence != Confidence.HIGH
                        || !healthy(input.health())
                        || input.revision() == EstimateRevisionEngine.RevisionState.STRONGLY_NEGATIVE)) {
            state = ValuationState.ATTRACTIVE;
        }
        return new Assessment(state, confidence, percentile3y, percentile5y, count, input.quality());
    }

    public static boolean historySufficient(int observations) {
        return observations >= MINIMUM_WEEKLY_HISTORY_OBSERVATIONS;
    }

    public static int availableFamilyCount(Metrics metrics) {
        int count = 0;
        if (metrics.trailingPe() != null || metrics.forwardPe() != null) count++;
        if (metrics.evSales() != null || metrics.priceSales() != null) count++;
        if (metrics.fcfYield() != null) count++;
        return count;
    }

    private static int historicalFamilyCount(Metrics current, List<Metrics> history) {
        int count = 0;
        if (HistoricalValuationPercentile.lowerIsCheaper(
                                current.trailingPe(),
                                history.stream().map(Metrics::trailingPe).toList())
                        != null
                || HistoricalValuationPercentile.lowerIsCheaper(
                                current.forwardPe(),
                                history.stream().map(Metrics::forwardPe).toList())
                        != null) count++;
        if (HistoricalValuationPercentile.lowerIsCheaper(
                                current.evSales(),
                                history.stream().map(Metrics::evSales).toList())
                        != null
                || HistoricalValuationPercentile.lowerIsCheaper(
                                current.priceSales(),
                                history.stream().map(Metrics::priceSales).toList())
                        != null) count++;
        if (HistoricalValuationPercentile.higherIsCheaper(
                        current.fcfYield(),
                        history.stream().map(Metrics::fcfYield).toList())
                != null) count++;
        return count;
    }

    private static BigDecimal medianPercentile(Metrics current, List<Metrics> history) {
        var percentiles = metricPercentiles(current, history);
        if (percentiles.isEmpty()) return null;
        percentiles.sort(Comparator.naturalOrder());
        return percentiles.get(percentiles.size() / 2);
    }

    private static ArrayList<BigDecimal> metricPercentiles(Metrics current, List<Metrics> history) {
        var percentiles = new ArrayList<BigDecimal>();
        add(
                percentiles,
                HistoricalValuationPercentile.lowerIsCheaper(
                        current.forwardPe(),
                        history.stream().map(Metrics::forwardPe).toList()));
        add(
                percentiles,
                HistoricalValuationPercentile.lowerIsCheaper(
                        current.trailingPe(),
                        history.stream().map(Metrics::trailingPe).toList()));
        add(
                percentiles,
                HistoricalValuationPercentile.lowerIsCheaper(
                        current.evSales(),
                        history.stream().map(Metrics::evSales).toList()));
        add(
                percentiles,
                HistoricalValuationPercentile.higherIsCheaper(
                        current.fcfYield(),
                        history.stream().map(Metrics::fcfYield).toList()));
        add(
                percentiles,
                HistoricalValuationPercentile.lowerIsCheaper(
                        current.priceSales(),
                        history.stream().map(Metrics::priceSales).toList()));
        return percentiles;
    }

    private static ValuationState state(BigDecimal percentile) {
        if (percentile.compareTo(new BigDecimal("0.20")) <= 0) return ValuationState.DEEP_DISCOUNT;
        if (percentile.compareTo(new BigDecimal("0.35")) <= 0) return ValuationState.ATTRACTIVE;
        if (percentile.compareTo(new BigDecimal("0.65")) <= 0) return ValuationState.FAIR;
        if (percentile.compareTo(new BigDecimal("0.85")) <= 0) return ValuationState.RICH;
        return ValuationState.EXTREME;
    }

    private static boolean healthy(CompanyHealth status) {
        return status == CompanyHealth.STRONG || status == CompanyHealth.HEALTHY;
    }

    private static void add(List<BigDecimal> values, BigDecimal value) {
        if (value != null) values.add(value);
    }

    public enum ValuationState {
        DEEP_DISCOUNT,
        ATTRACTIVE,
        FAIR,
        RICH,
        EXTREME,
        MISSING
    }

    public enum Confidence {
        HIGH,
        LOW,
        MISSING
    }

    public enum CompanyHealth {
        STRONG,
        HEALTHY,
        STABLE,
        WEAKENING,
        BROKEN,
        MISSING
    }

    public record Metrics(
            BigDecimal trailingPe,
            BigDecimal forwardPe,
            BigDecimal evSales,
            BigDecimal fcfYield,
            BigDecimal priceSales,
            BigDecimal marketCap) {}

    public record Input(
            Metrics current,
            List<Metrics> history3y,
            List<Metrics> history5y,
            CompanyHealth health,
            EstimateRevisionEngine.RevisionState revision,
            ProviderModels.QualityStatus quality) {
        public Input {
            history3y = List.copyOf(history3y);
            history5y = List.copyOf(history5y);
        }
    }

    public record Assessment(
            ValuationState state,
            Confidence confidence,
            BigDecimal ownHistoryPercentile3y,
            BigDecimal ownHistoryPercentile5y,
            int observationCount,
            ProviderModels.QualityStatus quality) {
        static Assessment missing() {
            return new Assessment(
                    ValuationState.MISSING, Confidence.MISSING, null, null, 0, ProviderModels.QualityStatus.MISSING);
        }
    }
}
