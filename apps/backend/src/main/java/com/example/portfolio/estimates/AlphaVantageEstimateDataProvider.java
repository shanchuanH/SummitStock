package com.example.portfolio.estimates;

import com.example.portfolio.market.provider.ProviderCallException;
import com.example.portfolio.market.provider.ProviderHttpClient;
import com.example.portfolio.market.provider.ProviderModels;
import com.example.portfolio.market.provider.ProviderProperties;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
@Profile("!test & !local-fixture")
@ConditionalOnProperty(name = "portfolio.providers.estimates.type", havingValue = "alpha-vantage")
public final class AlphaVantageEstimateDataProvider implements EstimateDataProvider {
    private final ProviderHttpClient http;
    private final ProviderProperties properties;
    private final Clock clock;

    public AlphaVantageEstimateDataProvider(
            @Qualifier("estimateProviderHttpClient") ProviderHttpClient http,
            ProviderProperties properties,
            Clock clock) {
        this.http = http;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public EarningsEstimateResult fetchEstimates(String symbol) {
        var normalized = symbol.strip().toUpperCase(java.util.Locale.ROOT);
        var config = properties.estimates();
        var separator = config.baseUrl().contains("?") ? "&" : "?";
        var uri = URI.create(config.baseUrl() + separator + "function=EARNINGS_ESTIMATES&symbol=" + encode(normalized)
                + "&apikey=" + encode(config.apiKey()));
        var root = http.get(uri, Map.of()).json();
        var rows = root.get("estimates");
        if (rows == null || !rows.isArray()) {
            throw new ProviderCallException("PROVIDER_MALFORMED", "Earnings estimates array is missing", 200, false);
        }
        var estimates = new ArrayList<EarningsEstimateResult.Estimate>();
        for (var row : rows) {
            var date = date(row, "date");
            var horizonText = text(row, "horizon");
            var period = horizonText.contains("quarter")
                    ? EarningsEstimateResult.PeriodType.QUARTERLY
                    : EarningsEstimateResult.PeriodType.ANNUAL;
            add(estimates, row, date, horizonText, period, EarningsEstimateResult.EstimateType.EPS, "eps_estimate");
            add(
                    estimates,
                    row,
                    date,
                    horizonText,
                    period,
                    EarningsEstimateResult.EstimateType.REVENUE,
                    "revenue_estimate");
        }
        var quality = estimates.isEmpty() ? ProviderModels.QualityStatus.MISSING : ProviderModels.QualityStatus.HEALTHY;
        return new EarningsEstimateResult(normalized, estimates, "alpha-vantage", clock.instant(), quality);
    }

    private static void add(
            List<EarningsEstimateResult.Estimate> target,
            JsonNode row,
            LocalDate date,
            String horizon,
            EarningsEstimateResult.PeriodType period,
            EarningsEstimateResult.EstimateType type,
            String prefix) {
        var mean = decimal(row, prefix + "_average");
        if (mean == null) return;
        target.add(new EarningsEstimateResult.Estimate(
                type,
                period,
                date,
                horizon,
                mean,
                decimal(row, prefix + "_high"),
                decimal(row, prefix + "_low"),
                integer(row, prefix + "_analyst_count")));
    }

    private static String text(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new ProviderCallException("PROVIDER_MALFORMED", "Missing estimate field: " + field, 200, false);
        }
        return value.asText();
    }

    private static LocalDate date(JsonNode node, String field) {
        try {
            return LocalDate.parse(text(node, field));
        } catch (RuntimeException exception) {
            throw new ProviderCallException("PROVIDER_MALFORMED", "Invalid estimate date", 200, false);
        }
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) return null;
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException exception) {
            throw new ProviderCallException("PROVIDER_MALFORMED", "Invalid estimate number: " + field, 200, false);
        }
    }

    private static Integer integer(JsonNode node, String field) {
        var value = decimal(node, field);
        return value == null ? null : value.intValueExact();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
