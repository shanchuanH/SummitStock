package com.example.portfolio.analysis.domain;

import java.math.BigDecimal;
import java.util.List;

public record StrategyDefinition(
        String version,
        String configHash,
        String publishState,
        BigDecimal emergencyCashFloor,
        boolean emergencyExcludedFromInvestableAssets,
        BigDecimal painLine,
        BigDecimal absoluteTradeRiskMax,
        BigDecimal totalOpenRiskMax,
        BigDecimal clusterOpenRiskMax,
        int coolingHours,
        int maxDailyMustAct,
        boolean underweightAloneCanTriggerAdd,
        BigDecimal qualityStarterFraction,
        BigDecimal broadCoreTarget,
        BigDecimal techCoreTarget,
        String broadCorePrimaryInstrument,
        String techCorePrimaryInstrument,
        PositionPolicy quality,
        PositionPolicy thematicEtf,
        PositionPolicy tactical,
        PositionPolicy speculative,
        EtfDipPolicy etfDip,
        FinancialHealthPolicy financialHealth) {
    public StrategyDefinition {
        if (version == null || version.isBlank()) throw new IllegalArgumentException("Strategy version is required");
        if (configHash == null || !configHash.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("Strategy config hash is invalid");
        }
        if (publishState == null || publishState.isBlank()) {
            throw new IllegalArgumentException("Strategy publish state is required");
        }
        if (broadCorePrimaryInstrument == null || broadCorePrimaryInstrument.isBlank()) {
            throw new IllegalArgumentException("Broad-core primary instrument is required");
        }
        if (techCorePrimaryInstrument == null || techCorePrimaryInstrument.isBlank()) {
            throw new IllegalArgumentException("Tech-core primary instrument is required");
        }
        if (!emergencyExcludedFromInvestableAssets) {
            throw new IllegalArgumentException("Emergency reserve must be excluded from investable assets");
        }
    }

    public record PositionPolicy(
            BigDecimal targetMin,
            BigDecimal targetMax,
            BigDecimal normalMax,
            BigDecimal hardMax,
            BigDecimal tradeRisk) {}

    public record EtfDipPolicy(
            int setupScoreMin,
            int requiredReversalSignals,
            List<BigDecimal> tranches,
            int cooldownTradingDays,
            boolean requiresMarketDrivenDrawdown) {
        public EtfDipPolicy {
            tranches = List.copyOf(tranches);
        }
    }

    public record FinancialHealthPolicy(
            BigDecimal revenueGrowthStrong,
            BigDecimal revenueGrowthHealthy,
            BigDecimal marginDeteriorationWarningPctPoints,
            BigDecimal fcfMarginHealthy,
            BigDecimal dilutionWarning,
            BigDecimal netDebtToFcfWarning) {}
}
