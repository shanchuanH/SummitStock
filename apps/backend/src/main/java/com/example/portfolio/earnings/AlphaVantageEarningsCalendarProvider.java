package com.example.portfolio.earnings;

import com.example.portfolio.market.provider.ProviderCallException;
import com.example.portfolio.market.provider.ProviderHttpClient;
import com.example.portfolio.market.provider.ProviderModels;
import com.example.portfolio.market.provider.ProviderProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test & !local-fixture")
@ConditionalOnProperty(name = "portfolio.providers.earnings-calendar.type", havingValue = "alpha-vantage")
public final class AlphaVantageEarningsCalendarProvider implements EarningsCalendarProvider {
    private final ProviderHttpClient http;
    private final ProviderProperties properties;
    private final Clock clock;

    public AlphaVantageEarningsCalendarProvider(
            @Qualifier("earningsCalendarProviderHttpClient") ProviderHttpClient http,
            ProviderProperties properties,
            Clock clock) {
        this.http = http;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public CalendarResult fetch(String symbol, LocalDate from, LocalDate to) {
        var normalized = symbol.strip().toUpperCase(java.util.Locale.ROOT);
        var config = properties.earningsCalendar();
        var separator = config.baseUrl().contains("?") ? "&" : "?";
        var uri = URI.create(config.baseUrl() + separator + "function=EARNINGS_CALENDAR&symbol=" + encode(normalized)
                + "&horizon=3month&apikey=" + encode(config.apiKey()));
        var raw = http.getText(uri, Map.of("Accept", "text/csv"));
        var rows = csv(raw);
        if (rows.isEmpty() || !rows.getFirst().contains("reportDate")) {
            throw new ProviderCallException(
                    "PROVIDER_MALFORMED", "Earnings calendar CSV header is missing", 200, false);
        }
        var header = rows.getFirst();
        int reportDate = header.indexOf("reportDate");
        int fiscalDate = header.indexOf("fiscalDateEnding");
        var events = new ArrayList<CalendarEvent>();
        for (int index = 1; index < rows.size(); index++) {
            var row = rows.get(index);
            if (row.size() <= reportDate || row.get(reportDate).isBlank()) continue;
            try {
                var marketDate = LocalDate.parse(row.get(reportDate));
                if (marketDate.isBefore(from) || marketDate.isAfter(to)) continue;
                var fiscalPeriod = fiscalDate >= 0 && row.size() > fiscalDate ? row.get(fiscalDate) : null;
                events.add(new CalendarEvent(
                        marketDate.atTime(12, 0).toInstant(ZoneOffset.UTC),
                        marketDate,
                        Timing.UNKNOWN,
                        fiscalPeriod,
                        true,
                        config.baseUrl()));
            } catch (RuntimeException exception) {
                throw new ProviderCallException("PROVIDER_MALFORMED", "Invalid earnings calendar row", 200, false);
            }
        }
        var quality = events.isEmpty() ? ProviderModels.QualityStatus.MISSING : ProviderModels.QualityStatus.HEALTHY;
        return new CalendarResult(normalized, events, "alpha-vantage", clock.instant(), quality);
    }

    static List<List<String>> csv(String raw) {
        var rows = new ArrayList<List<String>>();
        for (var line : raw.split("\\R")) {
            if (line.isBlank()) continue;
            var cells = new ArrayList<String>();
            var value = new StringBuilder();
            boolean quoted = false;
            for (int index = 0; index < line.length(); index++) {
                char current = line.charAt(index);
                if (current == '"') {
                    if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                        value.append('"');
                        index++;
                    } else {
                        quoted = !quoted;
                    }
                } else if (current == ',' && !quoted) {
                    cells.add(value.toString());
                    value.setLength(0);
                } else {
                    value.append(current);
                }
            }
            cells.add(value.toString());
            rows.add(cells);
        }
        return rows;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
