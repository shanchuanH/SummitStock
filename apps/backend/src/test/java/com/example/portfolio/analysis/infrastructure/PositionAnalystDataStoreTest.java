package com.example.portfolio.analysis.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.analysis.domain.HoldingEvidence;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class PositionAnalystDataStoreTest {
    @Test
    void calculatesCanonicalReturnsWithoutInventingMissingHistory() {
        var asset = bars("110", "100");
        var benchmark = bars("105", "100");

        assertThat(PositionAnalystDataStore.periodReturn(asset, 1)).isEqualByComparingTo("0.1");
        assertThat(PositionAnalystDataStore.relativeReturn(asset, benchmark, 1))
                .isEqualByComparingTo("0.047619047619048");
        assertThat(PositionAnalystDataStore.periodReturn(asset, 21)).isNull();
    }

    private static List<HoldingEvidence.PriceBar> bars(String current, String prior) {
        return List.of(
                new HoldingEvidence.PriceBar(LocalDate.of(2026, 8, 20), new BigDecimal(current), true),
                new HoldingEvidence.PriceBar(LocalDate.of(2026, 8, 19), new BigDecimal(prior), true));
    }
}
