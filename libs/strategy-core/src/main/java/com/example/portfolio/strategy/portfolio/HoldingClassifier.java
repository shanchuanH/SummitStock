package com.example.portfolio.strategy.portfolio;

import java.util.Locale;

public final class HoldingClassifier {
    private HoldingClassifier() {}

    public static Suggestion suggest(String symbol, String assetType, boolean thematic, boolean unvestedCompensation) {
        if (unvestedCompensation) {
            return new Suggestion(
                    HoldingClassification.UNVESTED_COMPENSATION,
                    true,
                    "Unvested compensation is not liquid or tradable.");
        }
        var normalized = symbol.toUpperCase(Locale.ROOT);
        if (assetType.equals("ETF") && java.util.Set.of("SPY", "VTI", "ITOT").contains(normalized)) {
            return new Suggestion(
                    HoldingClassification.CORE_BROAD_ETF,
                    false,
                    "Recognized broad-market ETF; user confirmation is still required.");
        }
        if (assetType.equals("ETF") && normalized.equals("QQQ")) {
            return new Suggestion(
                    HoldingClassification.CORE_TECH_ETF,
                    false,
                    "Recognized technology core ETF; user confirmation is still required.");
        }
        if (assetType.equals("ETF") && thematic) {
            return new Suggestion(
                    HoldingClassification.THEMATIC_ETF, false, "Thematic ETF suggestion requires user confirmation.");
        }
        return new Suggestion(
                HoldingClassification.UNKNOWN, false, "Stock quality cannot be inferred from ticker or price action.");
    }

    public record Suggestion(HoldingClassification classification, boolean blocked, String reason) {}
}
