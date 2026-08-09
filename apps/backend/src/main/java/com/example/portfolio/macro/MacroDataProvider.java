package com.example.portfolio.macro;

import com.example.portfolio.market.provider.ProviderModels;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface MacroDataProvider {
    MacroSeriesResult fetch(String seriesCode, LocalDate from, LocalDate to);

    record Observation(LocalDate date, BigDecimal value) {}

    record MacroSeriesResult(
            String seriesCode,
            List<Observation> observations,
            String provider,
            Instant dataAsOf,
            ProviderModels.QualityStatus quality) {
        public MacroSeriesResult {
            observations = List.copyOf(observations);
        }
    }
}
