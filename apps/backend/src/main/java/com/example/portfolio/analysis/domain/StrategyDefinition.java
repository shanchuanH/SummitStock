package com.example.portfolio.analysis.domain;

import com.example.portfolio.strategy.dip.CashflowAllocator;
import com.example.portfolio.strategy.position.StopEngine;
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
        int speculativeTimeStopTradingDays,
        SleeveAllocationTargets sleeveAllocations,
        ExecutionRiskPolicy executionRisk,
        StopPolicy stops,
        CashflowPolicy cashflow) {
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
        if (tacticalReserveTargets.size() != 3
                || tacticalReserveTargets.get(0).compareTo(tacticalReserveTargets.get(1)) > 0
                || tacticalReserveTargets.get(1).compareTo(tacticalReserveTargets.get(2)) > 0) {
            throw new IllegalArgumentException("Tactical reserve must contain ordered min, target, and max values");
        }
        if (speculativeTimeStopTradingDays < 1) {
            throw new IllegalArgumentException("Speculative time stop must be positive");
        }
        requirePublicationPolicy(
                sleeveAllocations,
                absoluteTradeRiskMax,
                totalOpenRiskMax,
                clusterOpenRiskMax,
                stopNewSpeculationAt,
                reduceTacticalCapacityAt,
                etfDipSetupAt,
                marketDrivenEtfDeploymentAt,
                painLine,
                etfDip,
                maxDailyMustAct,
                manualExecutionOnly,
                quality,
                thematicEtf,
                tactical,
                speculative);
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
                List.of(new BigDecimal("0.08"), new BigDecimal("0.10"), new BigDecimal("0.12")),
                new BigDecimal("0.08"),
                new BigDecimal("0.10"),
                new BigDecimal("0.12"),
                new BigDecimal("0.15"),
                true,
                true,
                true,
                true,
                false,
                25,
                SleeveAllocationTargets.defaults(),
                ExecutionRiskPolicy.defaults(),
                StopPolicy.defaults(),
                CashflowPolicy.defaults());
    }

    private static void requirePublicationPolicy(
            SleeveAllocationTargets allocations,
            BigDecimal absoluteTradeRiskMax,
            BigDecimal totalRisk,
            BigDecimal clusterRisk,
            BigDecimal dd1,
            BigDecimal dd2,
            BigDecimal dd3,
            BigDecimal dd4,
            BigDecimal dd5,
            EtfDipPolicy dip,
            int maxMustAct,
            boolean manualOnly,
            PositionPolicy... positionPolicies) {
        if (allocations == null || allocations.total().compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("Allocation targets must sum to 100%");
        }
        for (var policy : positionPolicies) {
            if (policy.targetMin().compareTo(policy.targetMax()) > 0
                    || policy.targetMax().compareTo(policy.normalMax()) > 0
                    || policy.normalMax().compareTo(policy.hardMax()) > 0) {
                throw new IllegalArgumentException("Position targets must satisfy min <= target <= max");
            }
            if (policy.tradeRisk().compareTo(absoluteTradeRiskMax) > 0) {
                throw new IllegalArgumentException("Trade risk exceeds absolute maximum");
            }
        }
        if (clusterRisk.compareTo(totalRisk) > 0) {
            throw new IllegalArgumentException("Cluster risk cannot exceed total risk");
        }
        if (!(dd1.compareTo(dd2) < 0 && dd2.compareTo(dd3) < 0 && dd3.compareTo(dd4) < 0 && dd4.compareTo(dd5) < 0)) {
            throw new IllegalArgumentException("Drawdown thresholds must be strictly increasing");
        }
        if (dip.tranches().stream().reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("ETF dip tranches must sum to 100%");
        }
        if (maxMustAct > 3) throw new IllegalArgumentException("Maximum Must Act count cannot exceed three");
        if (!manualOnly) throw new IllegalArgumentException("Strategy must remain manual only");
    }

    public StopEngine.Policy stopEnginePolicy() {
        return new StopEngine.Policy(
                stops.structureBufferAtr(),
                stops.qualityVolatilityAtr(),
                stops.tacticalVolatilityAtr(),
                stops.speculativeVolatilityAtr(),
                stops.trailingEmaBufferAtr(),
                stops.softAlertAtr(),
                stops.catastrophicAtr());
    }

    public CashflowAllocator.Policy cashflowAllocatorPolicy() {
        return new CashflowAllocator.Policy(
                emergencyCashFloor,
                cashflow.broadWithoutSignal(),
                cashflow.broadWithSignal(),
                cashflow.tech(),
                cashflow.international(),
                cashflow.tacticalReserve(),
                cashflow.qualityWithSignal());
    }

    public record PositionPolicy(
            BigDecimal targetMin,
            BigDecimal targetMax,
            BigDecimal normalMax,
            BigDecimal hardMax,
            BigDecimal tradeRisk) {}

    public record SleeveAllocationTargets(
            BigDecimal broad,
            BigDecimal tech,
            BigDecimal international,
            BigDecimal quality,
            BigDecimal thematic,
            BigDecimal tactical,
            BigDecimal speculative,
            BigDecimal tacticalReserve,
            String internationalPrimaryInstrument) {
        public BigDecimal total() {
            return broad.add(tech)
                    .add(international)
                    .add(quality)
                    .add(thematic)
                    .add(tactical)
                    .add(speculative)
                    .add(tacticalReserve);
        }

        static SleeveAllocationTargets defaults() {
            return new SleeveAllocationTargets(
                    new BigDecimal("0.35"),
                    new BigDecimal("0.15"),
                    new BigDecimal("0.10"),
                    new BigDecimal("0.15"),
                    new BigDecimal("0.08"),
                    new BigDecimal("0.05"),
                    new BigDecimal("0.02"),
                    new BigDecimal("0.10"),
                    "VXUS");
        }
    }

    public record ExecutionRiskPolicy(
            BigDecimal liquidityParticipationMax,
            BigDecimal thematicAtrRiskMultiple,
            BigDecimal thematicFallbackRiskFraction) {
        static ExecutionRiskPolicy defaults() {
            return new ExecutionRiskPolicy(new BigDecimal("0.10"), new BigDecimal("3.0"), new BigDecimal("0.10"));
        }
    }

    public record StopPolicy(
            BigDecimal structureBufferAtr,
            BigDecimal qualityVolatilityAtr,
            BigDecimal tacticalVolatilityAtr,
            BigDecimal speculativeVolatilityAtr,
            BigDecimal trailingEmaBufferAtr,
            BigDecimal softAlertAtr,
            BigDecimal catastrophicAtr) {
        static StopPolicy defaults() {
            return new StopPolicy(
                    new BigDecimal("0.25"),
                    new BigDecimal("2.5"),
                    new BigDecimal("3.0"),
                    new BigDecimal("3.5"),
                    new BigDecimal("0.5"),
                    new BigDecimal("0.5"),
                    new BigDecimal("0.75"));
        }
    }

    public record CashflowPolicy(
            BigDecimal broadWithoutSignal,
            BigDecimal broadWithSignal,
            BigDecimal tech,
            BigDecimal international,
            BigDecimal tacticalReserve,
            BigDecimal qualityWithSignal) {
        static CashflowPolicy defaults() {
            return new CashflowPolicy(
                    new BigDecimal("0.60"),
                    new BigDecimal("0.50"),
                    new BigDecimal("0.15"),
                    new BigDecimal("0.10"),
                    new BigDecimal("0.15"),
                    new BigDecimal("0.10"));
        }
    }

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
