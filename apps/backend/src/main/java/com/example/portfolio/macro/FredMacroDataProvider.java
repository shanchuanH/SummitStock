package com.example.portfolio.macro;

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
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test & !local-fixture")
@ConditionalOnProperty(name = "portfolio.providers.macro.type", havingValue = "fred")
public final class FredMacroDataProvider implements MacroDataProvider {
    private final ProviderHttpClient http;
    private final ProviderProperties properties;
    private final Clock clock;

    public FredMacroDataProvider(
            @Qualifier("macroProviderHttpClient") ProviderHttpClient http, ProviderProperties properties, Clock clock) {
        this.http = http;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public MacroSeriesResult fetch(String seriesCode, LocalDate from, LocalDate to) {
        var code = seriesCode.strip().toUpperCase(java.util.Locale.ROOT);
        var providerCode = "VIX3M".equals(code) ? "VXVCLS" : code;
        var config = properties.macro();
        var base = config.baseUrl().replaceAll("/$", "") + "/series/observations";
        var query = "series_id=" + encode(providerCode) + "&observation_start=" + from + "&observation_end=" + to
                + "&file_type=json&api_key=" + encode(config.apiKey());
        var root = http.get(URI.create(base + "?" + query), Map.of()).json();
        var rows = root.get("observations");
        if (rows == null || !rows.isArray()) {
            throw new ProviderCallException("PROVIDER_MALFORMED", "FRED observations array is missing", 200, false);
        }
        var observations = new ArrayList<Observation>();
        for (var row : rows) {
            var date = row.get("date");
            var value = row.get("value");
            if (date == null || value == null || value.isNull() || ".".equals(value.asText())) continue;
            try {
                observations.add(new Observation(LocalDate.parse(date.asText()), new BigDecimal(value.asText())));
            } catch (RuntimeException exception) {
                throw new ProviderCallException("PROVIDER_MALFORMED", "Invalid FRED observation", 200, false);
            }
        }
        var quality =
                observations.isEmpty() ? ProviderModels.QualityStatus.MISSING : ProviderModels.QualityStatus.HEALTHY;
        return new MacroSeriesResult(code, observations, "fred", clock.instant(), quality);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
