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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
@Profile("!test & !local-fixture")
@ConditionalOnProperty(name = "portfolio.providers.market.type", havingValue = "alpha-vantage")
public final class ProductionMarketDataProvider implements MarketDataProvider {
    private static final String PROVIDER = "alpha-vantage";
    private static final String NORMALIZATION_VERSION = "alpha-vantage-v1";
    private static final Duration DAILY_FRESHNESS = Duration.ofDays(7);
    private final ProviderHttpClient http;
    private final ProviderProperties properties;
    private final Clock clock;
    private final DataQualityPolicy quality = new DataQualityPolicy();
    private final MarketSessionFreshnessPolicy marketFreshness =
            new MarketSessionFreshnessPolicy(new UsEquityTradingCalendar());

    public ProductionMarketDataProvider(ProviderHttpClient http, ProviderProperties properties, Clock clock) {
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
        var payload = get("TIME_SERIES_DAILY_ADJUSTED", symbol);
        ProviderPayloads.rejectProviderError(payload.json());
        var series = payload.json().get("Time Series (Daily)");
        if (series == null || !series.isObject()) {
            throw new ProviderCallException("PROVIDER_MALFORMED", "Daily series is missing", 200, false);
        }
        var warnings = new ArrayList<String>();
        var bars = new ArrayList<ProviderModels.DailyBar>();
        boolean invalid = false;
        for (var entry : series.properties()) {
            var date = parseDate(entry.getKey(), "daily bar date");
            if (date.isBefore(from) || date.isAfter(to)) continue;
            try {
                var row = entry.getValue();
                var rawClose = decimal(row, "4. close");
                var adjustedClose = decimal(row, "5. adjusted close");
                var factor = adjustedClose.divide(rawClose, MathContext.DECIMAL128);
                var inverseFactor = BigDecimal.ONE.divide(factor, MathContext.DECIMAL128);
                var open = decimal(row, "1. open").multiply(factor, MathContext.DECIMAL128);
                var high = decimal(row, "2. high").multiply(factor, MathContext.DECIMAL128);
                var low = decimal(row, "3. low").multiply(factor, MathContext.DECIMAL128);
                var volume = decimal(row, "6. volume").multiply(inverseFactor, MathContext.DECIMAL128);
                invalid |= !validOhlc(open, high, low, adjustedClose) || volume.signum() < 0;
                bars.add(new ProviderModels.DailyBar(
                        date,
                        open,
                        high,
                        low,
                        adjustedClose,
                        volume,
                        true,
                        date.isBefore(LocalDate.now(clock.withZone(ZoneOffset.UTC)))));
            } catch (ArithmeticException | NumberFormatException exception) {
                throw new ProviderCallException("PROVIDER_MALFORMED", "Invalid numeric daily bar", 200, false);
            }
        }
        bars.sort(Comparator.comparing(ProviderModels.DailyBar::marketDate));
        if (!bars.isEmpty()) warnings.add("OHLC_ADJUSTED_USING_PROVIDER_CLOSE_FACTOR");
        Instant sourceTimestamp = bars.isEmpty()
                ? clock.instant()
                : bars.getLast().marketDate().atStartOfDay().toInstant(ZoneOffset.UTC);
        var status = quality.assess(
                new DataQualityPolicy.Evidence(
                        1, bars.isEmpty() ? 0 : 1, invalid, false, sourceTimestamp, DAILY_FRESHNESS),
                clock.instant());
        if (bars.isEmpty()) warnings.add("NO_BARS_IN_REQUESTED_RANGE");
        if (invalid) warnings.add("INVALID_OHLC_OR_VOLUME");
        return new ProviderModels.DailyBarsResult(
                normalizeSymbol(symbol), bars, provenance(payload.raw(), sourceTimestamp, status, warnings));
    }

    @Override
    public ProviderModels.QuoteResult fetchQuote(String symbol) {
        var payload = get("GLOBAL_QUOTE", symbol);
        ProviderPayloads.rejectProviderError(payload.json());
        var quote = payload.json().get("Global Quote");
        var warnings = new ArrayList<String>();
        BigDecimal last = null;
        LocalDate marketDate = null;
        Instant sourceTimestamp = clock.instant();
        if (quote != null && quote.isObject() && !quote.isEmpty()) {
            last = decimal(quote, "05. price");
            var dateNode = quote.get("07. latest trading day");
            if (dateNode != null && !dateNode.asText().isBlank()) {
                marketDate = parseDate(dateNode.asText(), "quote trading date");
                sourceTimestamp = marketDate.atStartOfDay().toInstant(ZoneOffset.UTC);
            }
            warnings.add("BID_AND_ASK_NOT_SUPPLIED_BY_PROVIDER");
        } else {
            warnings.add("EMPTY_GLOBAL_QUOTE");
        }
        var status = marketFreshness.decisionPriceQuality(marketDate, last, clock.instant());
        var liquidity = ProviderModels.ExecutionLiquidityEvidence.from(null, null, status);
        return new ProviderModels.QuoteResult(
                normalizeSymbol(symbol),
                null,
                null,
                last,
                "USD",
                new ProviderModels.DecisionPriceEvidence(last, marketDate, status),
                liquidity,
                provenance(payload.raw(), sourceTimestamp, status, warnings));
    }

    @Override
    public ProviderModels.CorporateActionsResult fetchCorporateActions(String symbol, LocalDate from, LocalDate to) {
        requireRange(from, to);
        var dividends = get("DIVIDENDS", symbol);
        var splits = get("SPLITS", symbol);
        ProviderPayloads.rejectProviderError(dividends.json());
        ProviderPayloads.rejectProviderError(splits.json());
        var actions = new ArrayList<ProviderModels.CorporateAction>();
        readDividends(dividends.json(), from, to, actions);
        readSplits(splits.json(), from, to, actions);
        actions.sort(Comparator.comparing(
                action -> action.effectiveDate() == null ? action.exDate() : action.effectiveDate()));
        var now = clock.instant();
        return new ProviderModels.CorporateActionsResult(
                normalizeSymbol(symbol),
                actions,
                provenance(
                        dividends.raw() + "\n" + splits.raw(), now, ProviderModels.QualityStatus.HEALTHY, List.of()));
    }

    private ProviderHttpClient.Payload get(String function, String symbol) {
        var outputSize = "TIME_SERIES_DAILY_ADJUSTED".equals(function) ? "&outputsize=full" : "";
        var query = "function=" + encode(function) + "&symbol=" + encode(normalizeSymbol(symbol)) + outputSize
                + "&apikey=" + encode(properties.market().apiKey());
        var separator = properties.market().baseUrl().contains("?") ? "&" : "?";
        return http.get(URI.create(properties.market().baseUrl() + separator + query), Map.of());
    }

    private void readDividends(
            JsonNode root, LocalDate from, LocalDate to, List<ProviderModels.CorporateAction> target) {
        var data = requiredArray(root, "data");
        for (var row : data) {
            var date = parseDate(ProviderPayloads.requiredText(row, "ex_dividend_date"), "dividend ex date");
            if (!date.isBefore(from) && !date.isAfter(to)) {
                target.add(new ProviderModels.CorporateAction(
                        "DIVIDEND", date, date, null, decimal(row, "amount"), "USD"));
            }
        }
    }

    private void readSplits(JsonNode root, LocalDate from, LocalDate to, List<ProviderModels.CorporateAction> target) {
        var data = requiredArray(root, "data");
        for (var row : data) {
            var date = parseDate(ProviderPayloads.requiredText(row, "effective_date"), "split effective date");
            if (!date.isBefore(from) && !date.isAfter(to)) {
                target.add(new ProviderModels.CorporateAction(
                        "SPLIT", date, date, decimal(row, "split_factor"), null, null));
            }
        }
    }

    private static JsonNode requiredArray(JsonNode root, String field) {
        var node = root.get(field);
        if (node == null || !node.isArray()) {
            throw new ProviderCallException("PROVIDER_MALFORMED", "Missing provider array: " + field, 200, false);
        }
        return node;
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

    private static BigDecimal decimal(JsonNode node, String field) {
        return new BigDecimal(ProviderPayloads.requiredText(node, field));
    }

    private static LocalDate parseDate(String value, String label) {
        try {
            return LocalDate.parse(value);
        } catch (java.time.format.DateTimeParseException exception) {
            throw new ProviderCallException("PROVIDER_MALFORMED", "Invalid " + label, 200, false);
        }
    }

    private static void requireRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) throw new IllegalArgumentException("Invalid date range");
    }

    private static String normalizeSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("symbol is required");
        return symbol.strip().toUpperCase(java.util.Locale.ROOT);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
