package com.example.portfolio.macro;

import com.example.portfolio.market.provider.ProviderModels;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("'${portfolio.providers.macro.type:unavailable}' != 'fred'")
public final class UnavailableMacroDataProvider implements MacroDataProvider {
    private final Clock clock;

    public UnavailableMacroDataProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public MacroSeriesResult fetch(String seriesCode, LocalDate from, LocalDate to) {
        return new MacroSeriesResult(
                seriesCode, List.of(), "unavailable", clock.instant(), ProviderModels.QualityStatus.MISSING);
    }
}
