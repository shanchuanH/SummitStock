package com.example.portfolio.market.provider;

import java.math.BigDecimal;
import java.math.MathContext;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
@Profile("!test & !local-fixture")
@ConditionalOnProperty(name = "portfolio.providers.market.type", havingValue = "yahoo")
public final class YahooMarketDataProvider implements MarketDataProvider {
    private static final String PROVIDER = "yahoo-finance-chart";
    private static final String NORMALIZATION_VERSION = "yahoo-chart-v1";
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final Duration DAILY_FRESHNESS = Duration.ofDays(7);
    private static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");
    private static final Map<String, String> HEADERS = Map.of(
            "User-Agent", "SummitStock/1.0 personal-portfolio-analysis",
            "Accept", "application/json");

    private final ProviderHttpClient http;
    private final ProviderProperties properties;
    private final Clock clock;
    private final DataQualityPolicy quality = new DataQualityPolicy();
    private final MarketSessionFreshnessPolicy marketFreshness =
            new MarketSessionFreshnessPolicy(new UsEquityTradingCalendar());
    private final Map<String, CachedChart> cache = new HashMap<>();

    public YahooMarketDataProvider(ProviderHttpClient http, ProviderProperties properties, Clock clock) {
        this.http = http;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String providerId() {
        return PROVIDER;
    }

    @Override
    public ProviderModels.DailyBarsResult fetchDailyBars(String symbol, LocalDate from, LocalDate to) {
        requireRange(from, to);
        var normalized = normalizeSymbol(symbol);
        var chart = chart(normalized, from, to);
        var result = result(chart.payload().json());
        var timestamps = requiredArray(result, "timestamp");
        var indicators = requiredObject(result, "indicators");
        var quotes = requiredArray(indicators, "quote");
        if (quotes.isEmpty()) throw malformed("Yahoo quote indicators are missing");
        var quote = quotes.get(0);
        var adjustedSets = requiredArray(indicators, "adjclose");
        if (adjustedSets.isEmpty()) throw malformed("Yahoo adjusted-close indicators are missing");
        var adjusted = requiredArray(adjustedSets.get(0), "adjclose");

        var warnings = new ArrayList<String>();
        var bars = new ArrayList<ProviderModels.DailyBar>();
        boolean invalid = false;
        for (int index = 0; index < timestamps.size(); index++) {
            var date = marketDate(timestamps.get(index));
            if (date.isBefore(from) || date.isAfter(to)) continue;
            try {
                var rawClose = decimalAt(quote.get("close"), index);
                var adjustedClose = decimalAt(adjusted, index);
                var open = decimalAt(quote.get("open"), index);
                var high = decimalAt(quote.get("high"), index);
                var low = decimalAt(quote.get("low"), index);
                var volume = decimalAt(quote.get("volume"), index);
                if (rawClose == null
                        || adjustedClose == null
                        || open == null
                        || high == null
                        || low == null
                        || volume == null
                        || rawClose.signum() == 0) {
                    invalid = true;
                    continue;
                }
                var factor = adjustedClose.divide(rawClose, MathContext.DECIMAL128);
                var inverseFactor = BigDecimal.ONE.divide(factor, MathContext.DECIMAL128);
                var adjustedOpen = open.multiply(factor, MathContext.DECIMAL128);
                var adjustedHigh = high.multiply(factor, MathContext.DECIMAL128);
                var adjustedLow = low.multiply(factor, MathContext.DECIMAL128);
                var adjustedVolume = volume.multiply(inverseFactor, MathContext.DECIMAL128);
                if (!validOhlc(adjustedOpen, adjustedHigh, adjustedLow, adjustedClose) || adjustedVolume.signum() < 0) {
                    invalid = true;
                    continue;
                }
                bars.add(new ProviderModels.DailyBar(
                        date,
                        adjustedOpen,
                        adjustedHigh,
                        adjustedLow,
                        adjustedClose,
                        adjustedVolume,
                        true,
                        date.isBefore(LocalDate.now(clock.withZone(MARKET_ZONE)))));
            } catch (ArithmeticException | NumberFormatException exception) {
                invalid = true;
            }
        }
        bars.sort(Comparator.comparing(ProviderModels.DailyBar::marketDate));
        warnings.add("UNOFFICIAL_YAHOO_FINANCE_CHART_ENDPOINT");
        warnings.add("OHLC_ADJUSTED_USING_PROVIDER_CLOSE_FACTOR");
        if (invalid) warnings.add("INVALID_OR_INCOMPLETE_OHLC_ROW_SKIPPED");
        if (bars.isEmpty()) warnings.add("NO_BARS_IN_REQUESTED_RANGE");
        var sourceTimestamp = bars.isEmpty()
                ? clock.instant()
                : bars.getLast().marketDate().atStartOfDay(MARKET_ZONE).toInstant();
        var status = quality.assess(
                new DataQualityPolicy.Evidence(
                        1, bars.isEmpty() ? 0 : 1, invalid, false, sourceTimestamp, DAILY_FRESHNESS),
                clock.instant());
        return new ProviderModels.DailyBarsResult(
                normalized, bars, provenance(chart.payload().raw(), sourceTimestamp, status, warnings));
    }

    @Override
    public ProviderModels.QuoteResult fetchQuote(String symbol) {
        var normalized = normalizeSymbol(symbol);
        var today = LocalDate.now(clock.withZone(MARKET_ZONE));
        var chart = chart(normalized, today.minusDays(400), today.plusDays(1));
        var root = result(chart.payload().json());
        var meta = requiredObject(root, "meta");
        var last = decimal(meta.get("regularMarketPrice"));
        var timestamps = requiredArray(root, "timestamp");
        LocalDate date = timestamps.isEmpty() ? null : marketDate(timestamps.get(timestamps.size() - 1));
        var status = marketFreshness.decisionPriceQuality(date, last, clock.instant());
        var sourceTimestamp =
                date == null ? clock.instant() : date.atStartOfDay(MARKET_ZONE).toInstant();
        var warnings = List.of("UNOFFICIAL_YAHOO_FINANCE_CHART_ENDPOINT", "BID_AND_ASK_NOT_SUPPLIED_BY_PROVIDER");
        var provenance = provenance(chart.payload().raw(), sourceTimestamp, status, warnings);
        return new ProviderModels.QuoteResult(
                normalized,
                null,
                null,
                last,
                text(meta, "currency", "USD"),
                new ProviderModels.DecisionPriceEvidence(last, date, status),
                ProviderModels.ExecutionLiquidityEvidence.from(null, null, status),
                provenance);
    }

    @Override
    public ProviderModels.CorporateActionsResult fetchCorporateActions(String symbol, LocalDate from, LocalDate to) {
        requireRange(from, to);
        var normalized = normalizeSymbol(symbol);
        var chart = chart(normalized, from, to);
        var root = result(chart.payload().json());
        var actions = new ArrayList<ProviderModels.CorporateAction>();
        var events = root.get("events");
        if (events != null && events.isObject()) {
            readDividends(events.get("dividends"), from, to, actions);
            readSplits(events.get("splits"), from, to, actions);
        }
        actions.sort(Comparator.comparing(ProviderModels.CorporateAction::effectiveDate));
        var warnings = List.of("UNOFFICIAL_YAHOO_FINANCE_CHART_ENDPOINT");
        return new ProviderModels.CorporateActionsResult(
                normalized,
                actions,
                provenance(chart.payload().raw(), clock.instant(), ProviderModels.QualityStatus.HEALTHY, warnings));
    }

    private synchronized CachedChart chart(String symbol, LocalDate from, LocalDate to) {
        var existing = cache.get(symbol);
        var now = clock.instant();
        if (existing != null
                && now.isBefore(existing.expiresAt())
                && !from.isBefore(existing.from())
                && !to.isAfter(existing.to())) {
            return existing;
        }
        var requestFrom = from.minusDays(1);
        var requestTo = to.plusDays(1);
        var separator = properties.market().baseUrl().endsWith("/") ? "" : "/";
        var uri = properties.market().baseUrl() + separator + encode(symbol) + "?period1=" + epoch(requestFrom)
                + "&period2=" + epoch(requestTo) + "&interval=1d&events=div%2Csplits&includeAdjustedClose=true";
        var loaded = new CachedChart(requestFrom, requestTo, http.get(URI.create(uri), HEADERS), now.plus(CACHE_TTL));
        result(loaded.payload().json());
        cache.put(symbol, loaded);
        return loaded;
    }

    private static JsonNode result(JsonNode payload) {
        var chart = requiredObject(payload, "chart");
        var error = chart.get("error");
        if (error != null && !error.isNull()) {
            throw new ProviderCallException("PROVIDER_UNAVAILABLE", "Yahoo chart returned an error", 200, false);
        }
        var results = requiredArray(chart, "result");
        if (results.isEmpty() || results.get(0) == null || !results.get(0).isObject()) {
            throw malformed("Yahoo chart result is missing");
        }
        return results.get(0);
    }

    private static void readDividends(
            JsonNode dividends, LocalDate from, LocalDate to, List<ProviderModels.CorporateAction> target) {
        if (dividends == null || !dividends.isObject()) return;
        for (var entry : dividends.properties()) {
            var row = entry.getValue();
            var date = eventDate(row);
            var amount = decimal(row.get("amount"));
            if (date != null && amount != null && !date.isBefore(from) && !date.isAfter(to)) {
                target.add(new ProviderModels.CorporateAction("DIVIDEND", date, date, null, amount, "USD"));
            }
        }
    }

    private static void readSplits(
            JsonNode splits, LocalDate from, LocalDate to, List<ProviderModels.CorporateAction> target) {
        if (splits == null || !splits.isObject()) return;
        for (var entry : splits.properties()) {
            var row = entry.getValue();
            var date = eventDate(row);
            var numerator = decimal(row.get("numerator"));
            var denominator = decimal(row.get("denominator"));
            if (date != null
                    && numerator != null
                    && denominator != null
                    && denominator.signum() != 0
                    && !date.isBefore(from)
                    && !date.isAfter(to)) {
                target.add(new ProviderModels.CorporateAction(
                        "SPLIT", date, date, numerator.divide(denominator, MathContext.DECIMAL128), null, null));
            }
        }
    }

    private ProviderModels.Provenance provenance(
            String raw, Instant sourceTimestamp, ProviderModels.QualityStatus status, List<String> warnings) {
        return new ProviderModels.Provenance(
                PROVIDER,
                sourceTimestamp,
                clock.instant(),
                ProviderPayloads.sha256(raw),
                NORMALIZATION_VERSION,
                status,
                warnings);
    }

    private static JsonNode requiredObject(JsonNode node, String field) {
        var value = node == null ? null : node.get(field);
        if (value == null || !value.isObject()) throw malformed("Missing Yahoo object: " + field);
        return value;
    }

    private static JsonNode requiredArray(JsonNode node, String field) {
        var value = node == null ? null : node.get(field);
        if (value == null || !value.isArray()) throw malformed("Missing Yahoo array: " + field);
        return value;
    }

    private static ProviderCallException malformed(String message) {
        return new ProviderCallException("PROVIDER_MALFORMED", message, 200, false);
    }

    private static BigDecimal decimalAt(JsonNode array, int index) {
        if (array == null || !array.isArray() || index >= array.size()) return null;
        return decimal(array.get(index));
    }

    private static BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull() || node.asText().isBlank()) return null;
        return new BigDecimal(node.asText());
    }

    private static LocalDate eventDate(JsonNode row) {
        var date = row == null ? null : row.get("date");
        return date == null || date.isNull() ? null : marketDate(date);
    }

    private static LocalDate marketDate(JsonNode epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds.asLong()).atZone(MARKET_ZONE).toLocalDate();
    }

    private static String text(JsonNode node, String field, String fallback) {
        var value = node.get(field);
        return value == null || value.asText().isBlank() ? fallback : value.asText();
    }

    private static long epoch(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toEpochSecond();
    }

    private static boolean validOhlc(BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close) {
        return open.signum() > 0
                && close.signum() > 0
                && low.signum() > 0
                && high.compareTo(open) >= 0
                && high.compareTo(close) >= 0
                && high.compareTo(low) >= 0
                && low.compareTo(open) <= 0
                && low.compareTo(close) <= 0;
    }

    private static void requireRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) throw new IllegalArgumentException("Invalid date range");
    }

    private static String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        return symbol.strip().toUpperCase(Locale.ROOT);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private record CachedChart(LocalDate from, LocalDate to, ProviderHttpClient.Payload payload, Instant expiresAt) {}
}
