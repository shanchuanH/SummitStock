package com.example.portfolio.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.quant.QuantBar;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import com.example.portfolio.strategy.position.StopEngine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class HigherLowStopEvidenceTest {
    @Test
    void passesTwoDistinctOrderedPivotsOnlyWhenLatestLowIsHigher() {
        var higher = PortfolioAnalysisPipelineService.swingStructure(
                bars("10", "9", "8", "9", "10", "9.5", "9", "9.5", "10"));
        var lower =
                PortfolioAnalysisPipelineService.swingStructure(bars("10", "9", "8", "9", "10", "8", "7", "8", "9"));
        var single = PortfolioAnalysisPipelineService.swingStructure(bars("10", "9", "8", "9", "10"));

        assertThat(higher.structureSwingLow()).isEqualByComparingTo("9");
        assertThat(higher.confirmedHigherLow()).isEqualByComparingTo("9");
        assertThat(lower.structureSwingLow()).isEqualByComparingTo("7");
        assertThat(lower.confirmedHigherLow()).isNull();
        assertThat(single.structureSwingLow()).isEqualByComparingTo("8");
        assertThat(single.confirmedHigherLow()).isNull();
    }

    @Test
    void stopCalculationAcceptsMissingHigherLowWithoutInventingOne() {
        var result = StopEngine.calculate(new StopEngine.Input(
                HoldingClassification.QUALITY_STOCK,
                new BigDecimal("100"),
                new BigDecimal("90"),
                new BigDecimal("2"),
                new BigDecimal("85"),
                null,
                new BigDecimal("96"),
                null,
                new BigDecimal("98"),
                new BigDecimal("105"),
                null));

        assertThat(result.liveStop()).isNotNull();
    }

    private static java.util.List<QuantBar> bars(String... lows) {
        var result = new ArrayList<QuantBar>();
        var date = LocalDate.of(2026, 7, 1);
        for (var low : lows) {
            var value = new BigDecimal(low);
            result.add(new QuantBar(
                    date,
                    value.add(BigDecimal.ONE),
                    value.add(new BigDecimal("2")),
                    value,
                    value.add(BigDecimal.ONE),
                    BigDecimal.valueOf(1000),
                    true));
            date = date.plusDays(1);
        }
        return result;
    }
}
