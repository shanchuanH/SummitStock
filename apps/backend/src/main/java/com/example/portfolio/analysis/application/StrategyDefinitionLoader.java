package com.example.portfolio.analysis.application;

import com.example.portfolio.analysis.domain.StrategyDefinition;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public final class StrategyDefinitionLoader {
    private static final Set<String> CONSUMED_STRATEGY_KEYS = Set.of(
            "strategyVersion",
            "publishState",
            "profile.manualExecutionOnly",
            "profile.benchmarks.primaryGrowth",
            "profile.benchmarks.broadMarket",
            "capital.emergencyFloorUsd",
            "capital.emergencyExcludedFromInvestableAssets",
            "capital.tacticalReserveTargetPct",
            "drawdown.stopNewSpeculationAt",
            "drawdown.reduceTacticalCapacityAt",
            "drawdown.etfDipSetupAt",
            "drawdown.marketDrivenEtfDeploymentAt",
            "drawdown.painLineAt",
            "market.volatility.vixTermFlatLower",
            "market.volatility.vixTermBackwardation",
            "market.volatility.techPremiumElevatedRatio",
            "decision.maxDailyMustAct",
            "decision.exactQuantityRequiresHealthyPrice",
            "decision.exactQuantityRequiresReadyRisk",
            "decision.riskPriorityOverTax",
            "decision.underweightAloneCanTriggerAdd",
            "risk.absoluteSingleTradeMax",
            "risk.totalOpenStockRiskMax",
            "risk.clusterOpenRiskMax",
            "risk.socialMediaCoolingHours",
            "risk.liquidityParticipationMax",
            "risk.thematicAtrRiskMultiple",
            "risk.thematicFallbackRiskFraction",
            "allocation.broadUsCore",
            "allocation.techCore",
            "allocation.internationalCore",
            "allocation.quality",
            "allocation.thematic",
            "allocation.tactical",
            "allocation.speculative",
            "allocation.broadUsCorePrimary",
            "allocation.techCorePrimary",
            "allocation.internationalCorePrimary",
            "qualityStock.targetPct",
            "qualityStock.normalMaxPct",
            "qualityStock.hardMaxPct",
            "qualityStock.tradeRiskPct",
            "qualityStock.starterFractionOfTarget",
            "qualityStock.deepDiscountStarterEnabled",
            "thematicEtf.targetPct",
            "thematicEtf.hardMaxPct",
            "thematicEtf.tradeRiskPct",
            "tacticalStock.targetPct",
            "tacticalStock.hardMaxPct",
            "tacticalStock.tradeRiskPct",
            "speculative.targetPct",
            "speculative.hardMaxPct",
            "speculative.tradeRiskPct",
            "speculative.averageDownAllowed",
            "speculative.timeStopTradingDays",
            "stops.structureBufferAtr",
            "stops.qualityVolatilityAtr",
            "stops.tacticalVolatilityAtr",
            "stops.speculativeVolatilityAtr",
            "stops.trailingEmaBufferAtr",
            "stops.softAlertAtr",
            "stops.catastrophicAtr",
            "cashflow.broadCoreWithoutSignal",
            "cashflow.broadCoreWithSignal",
            "cashflow.techCore",
            "cashflow.internationalCore",
            "cashflow.tacticalReserve",
            "cashflow.qualityOpportunityWithSignal",
            "etfDip.setupScoreMin",
            "etfDip.requiredReversalSignals",
            "etfDip.tranchePctOfReserve",
            "etfDip.cooldownTradingDays",
            "etfDip.requiresMarketDrivenDrawdown",
            "freshness.eodPriceTradingSessions",
            "freshness.financialQuarterDays",
            "freshness.estimatesDays",
            "freshness.earningsCalendarDays",
            "freshness.etfProfileDays",
            "freshness.macroDailyDays",
            "financialHealth.revenueGrowthStrong",
            "financialHealth.revenueGrowthHealthy",
            "financialHealth.marginDeteriorationWarningPctPoints",
            "financialHealth.fcfMarginHealthy",
            "financialHealth.dilutionWarning",
            "financialHealth.netDebtToFcfWarning");
    private static final Set<String> NON_DECISION_METADATA_KEYS = Set.of("profile.broker");

    private final ResourceLoader resources;

    public StrategyDefinitionLoader(ResourceLoader resources) {
        this.resources = resources;
    }

    public StrategyDefinition load(String configuredPath) {
        try {
            var resource = resource(configuredPath);
            var bytes = resource.getInputStream().readAllBytes();
            var values = flatten(new String(bytes, StandardCharsets.UTF_8));
            return new StrategyDefinition(
                    required(values, "strategyVersion"),
                    sha256(bytes),
                    required(values, "publishState"),
                    decimal(values, "capital.emergencyFloorUsd"),
                    bool(values, "capital.emergencyExcludedFromInvestableAssets"),
                    decimal(values, "drawdown.painLineAt"),
                    decimal(values, "risk.absoluteSingleTradeMax"),
                    decimal(values, "risk.totalOpenStockRiskMax"),
                    decimal(values, "risk.clusterOpenRiskMax"),
                    integer(values, "risk.socialMediaCoolingHours"),
                    integer(values, "decision.maxDailyMustAct"),
                    bool(values, "decision.underweightAloneCanTriggerAdd"),
                    decimal(values, "qualityStock.starterFractionOfTarget"),
                    decimal(values, "allocation.broadUsCore"),
                    decimal(values, "allocation.techCore"),
                    required(values, "allocation.broadUsCorePrimary"),
                    required(values, "allocation.techCorePrimary"),
                    policy(
                            values,
                            "qualityStock.targetPct",
                            "qualityStock.normalMaxPct",
                            "qualityStock.hardMaxPct",
                            "qualityStock.tradeRiskPct"),
                    policy(
                            values,
                            "thematicEtf.targetPct",
                            "thematicEtf.hardMaxPct",
                            "thematicEtf.hardMaxPct",
                            "thematicEtf.tradeRiskPct"),
                    policy(
                            values,
                            "tacticalStock.targetPct",
                            "tacticalStock.hardMaxPct",
                            "tacticalStock.hardMaxPct",
                            "tacticalStock.tradeRiskPct"),
                    policy(
                            values,
                            "speculative.targetPct",
                            "speculative.hardMaxPct",
                            "speculative.hardMaxPct",
                            "speculative.tradeRiskPct"),
                    new StrategyDefinition.EtfDipPolicy(
                            integer(values, "etfDip.setupScoreMin"),
                            integer(values, "etfDip.requiredReversalSignals"),
                            decimals(values, "etfDip.tranchePctOfReserve"),
                            integer(values, "etfDip.cooldownTradingDays"),
                            bool(values, "etfDip.requiresMarketDrivenDrawdown")),
                    new StrategyDefinition.FreshnessPolicy(
                            integer(values, "freshness.eodPriceTradingSessions"),
                            integer(values, "freshness.financialQuarterDays"),
                            integer(values, "freshness.estimatesDays"),
                            integer(values, "freshness.earningsCalendarDays"),
                            integer(values, "freshness.etfProfileDays"),
                            integer(values, "freshness.macroDailyDays")),
                    new StrategyDefinition.FinancialHealthPolicy(
                            decimal(values, "financialHealth.revenueGrowthStrong"),
                            decimal(values, "financialHealth.revenueGrowthHealthy"),
                            decimal(values, "financialHealth.marginDeteriorationWarningPctPoints"),
                            decimal(values, "financialHealth.fcfMarginHealthy"),
                            decimal(values, "financialHealth.dilutionWarning"),
                            decimal(values, "financialHealth.netDebtToFcfWarning")),
                    bool(values, "profile.manualExecutionOnly"),
                    required(values, "profile.benchmarks.primaryGrowth"),
                    required(values, "profile.benchmarks.broadMarket"),
                    decimals(values, "capital.tacticalReserveTargetPct"),
                    decimal(values, "drawdown.stopNewSpeculationAt"),
                    decimal(values, "drawdown.reduceTacticalCapacityAt"),
                    decimal(values, "drawdown.etfDipSetupAt"),
                    decimal(values, "drawdown.marketDrivenEtfDeploymentAt"),
                    bool(values, "decision.exactQuantityRequiresHealthyPrice"),
                    bool(values, "decision.exactQuantityRequiresReadyRisk"),
                    bool(values, "decision.riskPriorityOverTax"),
                    bool(values, "qualityStock.deepDiscountStarterEnabled"),
                    bool(values, "speculative.averageDownAllowed"),
                    integer(values, "speculative.timeStopTradingDays"),
                    new StrategyDefinition.SleeveAllocationTargets(
                            decimal(values, "allocation.broadUsCore"),
                            decimal(values, "allocation.techCore"),
                            decimal(values, "allocation.internationalCore"),
                            decimal(values, "allocation.quality"),
                            decimal(values, "allocation.thematic"),
                            decimal(values, "allocation.tactical"),
                            decimal(values, "allocation.speculative"),
                            decimals(values, "capital.tacticalReserveTargetPct").get(1),
                            required(values, "allocation.internationalCorePrimary")),
                    new StrategyDefinition.ExecutionRiskPolicy(
                            decimal(values, "risk.liquidityParticipationMax"),
                            decimal(values, "risk.thematicAtrRiskMultiple"),
                            decimal(values, "risk.thematicFallbackRiskFraction")),
                    new StrategyDefinition.StopPolicy(
                            decimal(values, "stops.structureBufferAtr"),
                            decimal(values, "stops.qualityVolatilityAtr"),
                            decimal(values, "stops.tacticalVolatilityAtr"),
                            decimal(values, "stops.speculativeVolatilityAtr"),
                            decimal(values, "stops.trailingEmaBufferAtr"),
                            decimal(values, "stops.softAlertAtr"),
                            decimal(values, "stops.catastrophicAtr")),
                    new StrategyDefinition.CashflowPolicy(
                            decimal(values, "cashflow.broadCoreWithoutSignal"),
                            decimal(values, "cashflow.broadCoreWithSignal"),
                            decimal(values, "cashflow.techCore"),
                            decimal(values, "cashflow.internationalCore"),
                            decimal(values, "cashflow.tacticalReserve"),
                            decimal(values, "cashflow.qualityOpportunityWithSignal")));
        } catch (IOException exception) {
            throw new IllegalStateException("Published strategy configuration is unavailable", exception);
        }
    }

    Set<String> configuredKeys(String configuredPath) {
        try {
            var bytes = resource(configuredPath).getInputStream().readAllBytes();
            return Set.copyOf(flatten(new String(bytes, StandardCharsets.UTF_8)).keySet());
        } catch (IOException exception) {
            throw new IllegalStateException("Strategy configuration is unavailable", exception);
        }
    }

    public VolatilityResearchPolicy loadVolatilityResearchPolicy(String configuredPath) {
        try {
            var values = flatten(
                    new String(resource(configuredPath).getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            return new VolatilityResearchPolicy(
                    decimal(values, "market.volatility.vixTermFlatLower"),
                    decimal(values, "market.volatility.vixTermBackwardation"),
                    decimal(values, "market.volatility.techPremiumElevatedRatio"));
        } catch (IOException exception) {
            throw new IllegalStateException("Volatility research configuration is unavailable", exception);
        }
    }

    public record VolatilityResearchPolicy(
            BigDecimal vixTermFlatLower, BigDecimal vixTermBackwardation, BigDecimal techPremiumElevatedRatio) {
        public VolatilityResearchPolicy {
            if (vixTermFlatLower.compareTo(vixTermBackwardation) >= 0) {
                throw new IllegalArgumentException("VIX term thresholds must be ordered");
            }
        }
    }

    static Set<String> consumedStrategyKeys() {
        return CONSUMED_STRATEGY_KEYS;
    }

    static Set<String> nonDecisionMetadataKeys() {
        return NON_DECISION_METADATA_KEYS;
    }

    private Resource resource(String configuredPath) {
        var location = configuredPath.contains(":") ? configuredPath : "classpath:" + configuredPath;
        return resources.getResource(location);
    }

    private static StrategyDefinition.PositionPolicy policy(
            Map<String, String> values, String target, String normalMax, String hardMax, String risk) {
        var targets = decimals(values, target);
        if (targets.size() != 2) throw new IllegalStateException("Strategy target must contain two values: " + target);
        return new StrategyDefinition.PositionPolicy(
                targets.get(0),
                targets.get(1),
                decimal(values, normalMax),
                decimal(values, hardMax),
                decimal(values, risk));
    }

    private static Map<String, String> flatten(String yaml) {
        var values = new LinkedHashMap<String, String>();
        var parents = new ArrayDeque<Parent>();
        for (var raw : yaml.lines().toList()) {
            if (raw.isBlank() || raw.stripLeading().startsWith("#")) continue;
            int indent = raw.length() - raw.stripLeading().length();
            var line = raw.strip();
            int separator = line.indexOf(':');
            if (separator <= 0) throw new IllegalStateException("Unsupported strategy YAML line: " + line);
            while (!parents.isEmpty() && parents.peek().indent() >= indent) parents.pop();
            var key = line.substring(0, separator).strip();
            var value = line.substring(separator + 1).strip();
            var path = new ArrayList<String>();
            parents.descendingIterator().forEachRemaining(parent -> path.add(parent.key()));
            path.add(key);
            if (value.isEmpty()) parents.push(new Parent(indent, key));
            else values.put(String.join(".", path), unquote(value));
        }
        return values;
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String required(Map<String, String> values, String key) {
        var value = values.get(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing strategy value: " + key);
        return value;
    }

    private static BigDecimal decimal(Map<String, String> values, String key) {
        return new BigDecimal(required(values, key));
    }

    private static int integer(Map<String, String> values, String key) {
        return Integer.parseInt(required(values, key));
    }

    private static boolean bool(Map<String, String> values, String key) {
        return Boolean.parseBoolean(required(values, key));
    }

    private static List<BigDecimal> decimals(Map<String, String> values, String key) {
        var value = required(values, key);
        if (!value.startsWith("[") || !value.endsWith("]")) {
            throw new IllegalStateException("Strategy list is invalid: " + key);
        }
        if (value.length() == 2) return List.of();
        return java.util.Arrays.stream(value.substring(1, value.length() - 1).split(","))
                .map(String::strip)
                .map(StrategyDefinitionLoader::unquote)
                .map(BigDecimal::new)
                .toList();
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Parent(int indent, String key) {}
}
