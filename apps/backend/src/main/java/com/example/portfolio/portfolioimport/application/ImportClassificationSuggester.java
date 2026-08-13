package com.example.portfolio.portfolioimport.application;

import com.example.portfolio.strategy.portfolio.HoldingClassification;
import com.example.portfolio.strategy.portfolio.HoldingClassifier;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public final class ImportClassificationSuggester {
    private static final Pattern INLINE =
            Pattern.compile("ticker:\\s*([A-Z0-9.]+),\\s*classification:\\s*([A-Z_]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern TICKER =
            Pattern.compile("^-?\\s*ticker:\\s*([A-Z0-9.]+)\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLASSIFICATION =
            Pattern.compile("^\\s*classification:\\s*([A-Z_]+)\\s*$", Pattern.CASE_INSENSITIVE);

    private final Map<String, HoldingClassification> seeded;

    public ImportClassificationSuggester(ResourceLoader resources) {
        try {
            var resource = resources.getResource("classpath:strategy/CURRENT_HOLDING_CLASSIFICATION_SEED.yaml");
            seeded = parse(new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Holding-classification seed is unavailable", exception);
        }
    }

    public Suggestion suggest(String symbol, String assetType, String description, String rowType) {
        if ("UNVESTED_COMPENSATION".equals(rowType)) {
            return new Suggestion(
                    HoldingClassification.UNVESTED_COMPENSATION.name(),
                    "Unvested compensation is excluded from investable assets.");
        }
        var normalizedSymbol = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        var configured = seeded.get(normalizedSymbol);
        if (configured != null) {
            return new Suggestion(
                    configured.name(),
                    "Suggested by the owner-reviewed classification seed; confirmation is required.");
        }
        if (normalizedSymbol.isBlank() || assetType == null) {
            return new Suggestion(HoldingClassification.UNKNOWN.name(), "More instrument evidence is required.");
        }
        var thematic = "ETF".equalsIgnoreCase(assetType)
                && description != null
                && !description.toLowerCase(Locale.ROOT).contains("market");
        var value = HoldingClassifier.suggest(normalizedSymbol, assetType.toUpperCase(Locale.ROOT), thematic, false);
        return new Suggestion(value.classification().name(), value.reason());
    }

    private static Map<String, HoldingClassification> parse(String yaml) {
        var result = new LinkedHashMap<String, HoldingClassification>();
        String pending = null;
        for (var raw : yaml.lines().toList()) {
            var inline = INLINE.matcher(raw);
            if (inline.find()) {
                result.put(
                        inline.group(1).toUpperCase(Locale.ROOT),
                        HoldingClassification.valueOf(inline.group(2).toUpperCase(Locale.ROOT)));
                continue;
            }
            var ticker = TICKER.matcher(raw);
            if (ticker.find()) {
                pending = ticker.group(1).toUpperCase(Locale.ROOT);
                continue;
            }
            var classification = CLASSIFICATION.matcher(raw);
            if (pending != null && classification.find()) {
                result.put(
                        pending,
                        HoldingClassification.valueOf(classification.group(1).toUpperCase(Locale.ROOT)));
                pending = null;
            }
        }
        return Map.copyOf(result);
    }

    public record Suggestion(String classification, String reason) {}
}
