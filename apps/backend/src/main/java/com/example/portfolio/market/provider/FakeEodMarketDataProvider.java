package com.example.portfolio.market.provider;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local-fixture", "test"})
public class FakeEodMarketDataProvider implements MarketDataProvider {
    private final Clock clock;
    private final FailureMode failureMode;

    public FakeEodMarketDataProvider() {
        this(Clock.systemUTC(), FailureMode.NONE);
    }

    public FakeEodMarketDataProvider(Clock clock, FailureMode failureMode) {
        this.clock = clock;
        this.failureMode = failureMode;
    }

    @Override
    public String providerId() {
        return "fake-eod";
    }

    @Override
    public Set<ProviderCapability> capabilities() {
        return Set.of(
                ProviderCapability.EOD_BARS,
                ProviderCapability.ADJUSTED_BARS,
                ProviderCapability.QUOTE_LAST,
                ProviderCapability.QUOTE_BID_ASK,
                ProviderCapability.CORPORATE_ACTIONS);
    }

    @Override
    public ProviderModels.DailyBarsResult fetchDailyBars(String symbol, LocalDate from, LocalDate to) {
        failIfConfigured();
        var bars = new ArrayList<ProviderModels.DailyBar>();
        var date = from;
        int index = 0;
        while (!date.isAfter(to)) {
            if (date.getDayOfWeek().getValue() <= 5) {
                var close = BigDecimal.valueOf(100L + index);
                bars.add(new ProviderModels.DailyBar(
                        date,
                        close.subtract(BigDecimal.ONE),
                        close.add(BigDecimal.ONE),
                        close.subtract(BigDecimal.TWO),
                        close,
                        BigDecimal.valueOf(1_000_000L + index),
                        true,
                        date.isBefore(LocalDate.now(clock.withZone(ZoneOffset.UTC)))));
                index++;
            }
            date = date.plusDays(1);
        }
        return new ProviderModels.DailyBarsResult(symbol, bars, provenance(symbol + from + to));
    }

    @Override
    public ProviderModels.QuoteResult fetchQuote(String symbol) {
        failIfConfigured();
        return new ProviderModels.QuoteResult(
                symbol,
                new BigDecimal("99.90"),
                new BigDecimal("100.10"),
                new BigDecimal("100.00"),
                "USD",
                provenance(symbol + "quote"));
    }

    @Override
    public ProviderModels.CorporateActionsResult fetchCorporateActions(String symbol, LocalDate from, LocalDate to) {
        failIfConfigured();
        return new ProviderModels.CorporateActionsResult(symbol, List.of(), provenance(symbol + "actions"));
    }

    private ProviderModels.Provenance provenance(String content) {
        var now = clock.instant();
        return new ProviderModels.Provenance(
                "fake-eod", now, now, sha256(content), "v1", ProviderModels.QualityStatus.HEALTHY, List.of());
    }

    private void failIfConfigured() {
        switch (failureMode) {
            case TIMEOUT -> throw new ProviderCallException("PROVIDER_TIMEOUT", "fake timeout", null, true);
            case RATE_LIMIT -> throw new ProviderCallException("PROVIDER_RATE_LIMIT", "fake 429", 429, true);
            case MALFORMED ->
                throw new ProviderCallException("PROVIDER_MALFORMED", "fake malformed payload", 200, false);
            case NONE -> {
                // Normal deterministic fixture.
            }
        }
    }

    private static String sha256(String content) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public enum FailureMode {
        NONE,
        TIMEOUT,
        RATE_LIMIT,
        MALFORMED
    }
}
