package com.example.portfolio.valuation;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.estimates.EstimateRevisionEngine;
import com.example.portfolio.market.provider.ProviderModels;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class HistoricalValuationPercentileTest {
    @Test
    void computesCheapTailForLowerMultiplesAndHigherFcfYield() {
        var history =
                IntStream.rangeClosed(1, 300).mapToObj(BigDecimal::valueOf).toList();

        assertThat(HistoricalValuationPercentile.lowerIsCheaper(new BigDecimal("30"), history))
                .isEqualByComparingTo("0.1");
        assertThat(HistoricalValuationPercentile.higherIsCheaper(new BigDecimal("270"), history))
                .isEqualByComparingTo("0.1");
        assertThat(HistoricalValuationPercentile.lowerIsCheaper(BigDecimal.ONE, List.of()))
                .isNull();
    }

    @Test
    void insufficientHistoryCannotProduceHighConfidenceDeepDiscount() {
        var current = new ValuationEngineV2.Metrics(
                new BigDecimal("1"),
                new BigDecimal("1"),
                new BigDecimal("1"),
                new BigDecimal("100"),
                new BigDecimal("1"),
                new BigDecimal("1000"));
        var history = IntStream.rangeClosed(2, 220)
                .mapToObj(value -> new ValuationEngineV2.Metrics(
                        BigDecimal.valueOf(value),
                        BigDecimal.valueOf(value),
                        BigDecimal.valueOf(value),
                        BigDecimal.ONE.divide(BigDecimal.valueOf(value), java.math.MathContext.DECIMAL128),
                        BigDecimal.valueOf(value),
                        BigDecimal.valueOf(1000)))
                .toList();

        var assessment = new ValuationEngineV2()
                .assess(new ValuationEngineV2.Input(
                        current,
                        history,
                        history,
                        ValuationEngineV2.CompanyHealth.HEALTHY,
                        EstimateRevisionEngine.RevisionState.FLAT,
                        ProviderModels.QualityStatus.HEALTHY));

        assertThat(assessment.confidence()).isEqualTo(ValuationEngineV2.Confidence.LOW);
        assertThat(assessment.state()).isEqualTo(ValuationEngineV2.ValuationState.ATTRACTIVE);
    }

    @Test
    void fiveYearsOfWeeklyPointInTimeHistoryCanSupportHighConfidence() {
        var current = metrics("1");
        var history = IntStream.rangeClosed(2, 221)
                .mapToObj(value -> metrics(Integer.toString(value)))
                .toList();

        var assessment = new ValuationEngineV2()
                .assess(new ValuationEngineV2.Input(
                        current,
                        history,
                        history,
                        ValuationEngineV2.CompanyHealth.HEALTHY,
                        EstimateRevisionEngine.RevisionState.FLAT,
                        ProviderModels.QualityStatus.HEALTHY));

        assertThat(assessment.observationCount()).isEqualTo(220);
        assertThat(assessment.confidence()).isEqualTo(ValuationEngineV2.Confidence.HIGH);
        assertThat(ValuationEngineV2.historySufficient(220)).isTrue();
    }

    private static ValuationEngineV2.Metrics metrics(String value) {
        var multiple = new BigDecimal(value);
        return new ValuationEngineV2.Metrics(
                multiple,
                multiple,
                multiple,
                BigDecimal.ONE.divide(multiple, java.math.MathContext.DECIMAL128),
                multiple,
                new BigDecimal("1000"));
    }
}
