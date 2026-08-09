package com.example.portfolio.macro;

import com.example.portfolio.market.provider.ProviderModels;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
final class UnavailableMacroDataProvider implements MacroDataProvider {
    private final Clock clock;

    UnavailableMacroDataProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public MacroSeriesResult fetch(String seriesCode, LocalDate from, LocalDate to) {
        return new MacroSeriesResult(
                seriesCode, List.of(), "unavailable", clock.instant(), ProviderModels.QualityStatus.MISSING);
    }
}
