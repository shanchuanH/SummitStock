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
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public final class StrategyDefinitionLoader {
    public StrategyDefinition load() {
        try {
            var bytes = new ClassPathResource("strategy/STRATEGY_CONFIG_V1_DRAFT.yaml")
                    .getInputStream()
                    .readAllBytes();
            var values = flatten(new String(bytes, StandardCharsets.UTF_8));
            return new StrategyDefinition(
                    required(values, "strategyVersion"),
                    sha256(bytes),
                    decimal(values, "cash.emergencyFloorUsd"),
                    decimal(values, "drawdownPolicyPct.painLine"),
                    decimal(values, "riskPct.absoluteSingleTradeMax"),
                    decimal(values, "riskPct.totalOpenStockRiskMax"),
                    decimal(values, "riskPct.clusterOpenRiskMax"),
                    integer(values, "behavior.socialMediaCoolingHours"),
                    integer(values, "behavior.maxDailyMustAct"),
                    decimal(values, "allocationTargetsPct.broadUsCore"),
                    decimal(values, "allocationTargetsPct.techCore"),
                    policy(
                            values,
                            "positionCapsPct.qualityTarget",
                            "positionCapsPct.qualityHardMax",
                            "riskPct.qualityTrade"),
                    policy(
                            values,
                            "positionCapsPct.thematicEtfTarget",
                            "positionCapsPct.thematicEtfMax",
                            "riskPct.themeTacticalTrade"),
                    policy(
                            values,
                            "positionCapsPct.tacticalStockTarget",
                            "positionCapsPct.tacticalStockMax",
                            "riskPct.themeTacticalTrade"),
                    policy(
                            values,
                            "positionCapsPct.speculativeTarget",
                            "positionCapsPct.speculativeMax",
                            "riskPct.speculativeTrade"),
                    new StrategyDefinition.EtfDipPolicy(
                            integer(values, "etfDip.setupScoreMin"),
                            integer(values, "etfDip.requiredReversalSignals"),
                            decimals(values, "etfDip.tranchePctOfReserve"),
                            integer(values, "etfDip.cooldownTradingDays"),
                            bool(values, "etfDip.requiresMarketDrivenDrawdown")));
        } catch (IOException exception) {
            throw new IllegalStateException("Published strategy configuration is unavailable", exception);
        }
    }

    private static StrategyDefinition.PositionPolicy policy(
            Map<String, String> values, String target, String hardMax, String risk) {
        var targets = decimals(values, target);
        if (targets.size() != 2) throw new IllegalStateException("Strategy target must contain two values: " + target);
        return new StrategyDefinition.PositionPolicy(
                targets.get(0), targets.get(1), decimal(values, hardMax), decimal(values, risk));
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
