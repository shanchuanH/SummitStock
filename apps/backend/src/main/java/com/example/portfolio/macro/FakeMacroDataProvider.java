package com.example.portfolio.macro;

import com.example.portfolio.market.provider.ProviderModels;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Deterministic five-year demo series. This provider is never active outside local-fixture. */
@Component
@Primary
@Profile("local-fixture")
@ConditionalOnProperty(name = "portfolio.providers.macro.type", havingValue = "fake")
final class FakeMacroDataProvider implements MacroDataProvider {
    private final Clock clock;

    FakeMacroDataProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public MacroSeriesResult fetch(String seriesCode, LocalDate from, LocalDate to) {
        var values = new ArrayList<Observation>();
        var base =
                switch (seriesCode) {
                    case "VIXCLS" -> new BigDecimal("18.0");
                    case "VIX3M" -> new BigDecimal("20.0");
                    case "BAMLH0A0HYM2" -> new BigDecimal("3.25");
                    case "DGS10" -> new BigDecimal("4.20");
                    case "DGS2" -> new BigDecimal("3.95");
                    case "FEDFUNDS" -> new BigDecimal("4.50");
                    default -> BigDecimal.ONE;
                };
        for (var date = from; !date.isAfter(to); date = date.plusDays(7)) {
            var wave = BigDecimal.valueOf(Math.floorMod(date.toEpochDay(), 13)).movePointLeft(2);
            values.add(new Observation(date, base.add(wave)));
        }
        return new MacroSeriesResult(
                seriesCode, values, "fixture-macro", clock.instant(), ProviderModels.QualityStatus.HEALTHY);
    }
}
