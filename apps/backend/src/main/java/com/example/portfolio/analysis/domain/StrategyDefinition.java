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
        FreshnessPolicy freshness,
        FinancialHealthPolicy financialHealth,
        boolean manualExecutionOnly,
        String primaryGrowthBenchmark,
        String broadMarketBenchmark,
        List<BigDecimal> tacticalReserveTargets,
        BigDecimal stopNewSpeculationAt,
        BigDecimal reduceTacticalCapacityAt,
        BigDecimal etfDipSetupAt,
        BigDecimal marketDrivenEtfDeploymentAt,
        boolean exactQuantityRequiresHealthyPrice,
        boolean exactQuantityRequiresReadyRisk,
        boolean riskPriorityOverTax,
        boolean deepDiscountStarterEnabled,
        boolean speculativeAverageDownAllowed,
        int speculativeTimeStopTradingDays) {
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
        if (!manualExecutionOnly) {
            throw new IllegalArgumentException("Only manual execution is supported");
        }
        if (primaryGrowthBenchmark == null || primaryGrowthBenchmark.isBlank()) {
            throw new IllegalArgumentException("Primary growth benchmark is required");
        }
        if (broadMarketBenchmark == null || broadMarketBenchmark.isBlank()) {
            throw new IllegalArgumentException("Broad market benchmark is required");
        }
        tacticalReserveTargets = List.copyOf(tacticalReserveTargets);
        if (tacticalReserveTargets.size() != 2) {
            throw new IllegalArgumentException("Tactical reserve target must contain two values");
        }
        if (speculativeTimeStopTradingDays < 1) {
            throw new IllegalArgumentException("Speculative time stop must be positive");
        }
    }

    public StrategyDefinition(
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
        this(
                version,
                configHash,
                publishState,
                emergencyCashFloor,
                emergencyExcludedFromInvestableAssets,
                painLine,
                absoluteTradeRiskMax,
                totalOpenRiskMax,
                clusterOpenRiskMax,
                coolingHours,
                maxDailyMustAct,
                underweightAloneCanTriggerAdd,
                qualityStarterFraction,
                broadCoreTarget,
                techCoreTarget,
                broadCorePrimaryInstrument,
                techCorePrimaryInstrument,
                quality,
                thematicEtf,
                tactical,
                speculative,
                etfDip,
                FreshnessPolicy.defaults(),
                financialHealth,
                true,
                "QQQ",
                "SPY",
                List.of(new BigDecimal("0.10"), new BigDecimal("0.15")),
                new BigDecimal("0.08"),
                new BigDecimal("0.10"),
                new BigDecimal("0.12"),
                new BigDecimal("0.15"),
                true,
                true,
                true,
                true,
                false,
                60);
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

    public record FreshnessPolicy(
            int eodPriceTradingSessions,
            int financialQuarterDays,
            int estimatesDays,
            int earningsCalendarDays,
            int etfProfileDays,
            int macroDailyDays) {
        public FreshnessPolicy {
            if (eodPriceTradingSessions < 1
                    || financialQuarterDays < 1
                    || estimatesDays < 1
                    || earningsCalendarDays < 1
                    || etfProfileDays < 1
                    || macroDailyDays < 1) {
                throw new IllegalArgumentException("Freshness thresholds must be positive");
            }
        }

        static FreshnessPolicy defaults() {
            return new FreshnessPolicy(1, 140, 14, 7, 14, 3);
        }
    }
}
