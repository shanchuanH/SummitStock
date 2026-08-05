package com.example.portfolio.strategy.portfolio;

import com.example.portfolio.strategy.RuleIds;
import com.example.portfolio.strategy.market.DecisionConfidence;
import com.example.portfolio.strategy.market.EvidenceQuality;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class HoldingPolicy {
    private static final BigDecimal ABSOLUTE_TRADE_RISK = new BigDecimal("0.0050");
    private static final BigDecimal TOTAL_OPEN_RISK = new BigDecimal("0.0200");
    private static final BigDecimal CLUSTER_OPEN_RISK = new BigDecimal("0.0075");

    private HoldingPolicy() {}

    public static Analysis analyze(Input input) {
        var rules = new ArrayList<String>();
        var reasons = new ArrayList<String>();
        var risks = new ArrayList<String>();
        var allowed = true;
        if (!input.classificationConfirmed() || input.classification() == HoldingClassification.UNKNOWN) {
            allowed = false;
            rules.add(RuleIds.POSITION_CLASSIFICATION_REQUIRED);
            reasons.add("Classification requires explicit user confirmation.");
        }
        if (input.classification() == HoldingClassification.UNVESTED_COMPENSATION) {
            allowed = false;
            rules.add(RuleIds.POSITION_UNVESTED_BLOCK);
            reasons.add("Unvested compensation is excluded from liquid assets and actions.");
        }
        var weightCap = weightCap(input.classification());
        if (input.projectedWeight().compareTo(weightCap) > 0) {
            allowed = false;
            rules.add(RuleIds.RISK_WEIGHT_CAP);
            risks.add("Projected holding weight exceeds the classification hard cap.");
        }
        var tradeRiskCap = tradeRiskCap(input.classification()).min(ABSOLUTE_TRADE_RISK);
        if (input.proposedTradeRisk().compareTo(tradeRiskCap) > 0) {
            allowed = false;
            rules.add(RuleIds.RISK_TRADE_BUDGET);
            risks.add("Proposed trade risk exceeds its classification budget.");
        }
        if (input.currentOpenStockRisk().add(input.proposedTradeRisk()).compareTo(TOTAL_OPEN_RISK) > 0) {
            allowed = false;
            rules.add(RuleIds.RISK_TOTAL_OPEN);
            risks.add("Projected total open stock risk exceeds 2%.");
        }
        if (input.currentClusterRisk().add(input.proposedTradeRisk()).compareTo(CLUSTER_OPEN_RISK) > 0) {
            allowed = false;
            rules.add(RuleIds.RISK_CLUSTER_CAP);
            risks.add("Projected cluster open risk exceeds 0.75%.");
        }
        if (input.lastDecisionAt() != null
                && Duration.between(input.lastDecisionAt(), input.now()).compareTo(Duration.ofHours(48)) < 0) {
            allowed = false;
            rules.add(RuleIds.RISK_COOLING_PERIOD);
            reasons.add("The 48-hour decision cooling period is active.");
        }
        if (input.averagingDown() && !input.thesisImproving()) {
            allowed = false;
            rules.add(RuleIds.RISK_INVALID_AVERAGING);
            risks.add("A lower price alone is not a valid averaging-down thesis.");
        }
        if (input.anchoredToCostBasis()) {
            allowed = false;
            rules.add(RuleIds.RISK_ANCHORING);
            risks.add("Cost basis is not a valid target or risk anchor.");
        }
        boolean preciseQuantity = input.quality() == EvidenceQuality.HEALTHY;
        if (!preciseQuantity) {
            rules.add(RuleIds.DATA_STALE_QUANTITY_BLOCK);
            reasons.add("Stale, partial, suspect, or missing evidence blocks a precise quantity.");
        }
        if (allowed && reasons.isEmpty()) reasons.add("All confirmed classification and risk limits are satisfied.");
        return new Analysis(
                allowed,
                preciseQuantity,
                weightCap,
                tradeRiskCap,
                input.currentOpenStockRisk().add(input.proposedTradeRisk()),
                input.currentClusterRisk().add(input.proposedTradeRisk()),
                confidence(input.quality()),
                rules,
                reasons,
                risks);
    }

    private static BigDecimal weightCap(HoldingClassification classification) {
        return switch (classification) {
            case QUALITY_STOCK, QUALITY_GROWTH_HIGH_VOL -> new BigDecimal("0.15");
            case THEMATIC_ETF -> new BigDecimal("0.10");
            case TACTICAL_STOCK, CYCLICAL_TACTICAL, TURNAROUND_TACTICAL -> new BigDecimal("0.05");
            case SPECULATIVE -> new BigDecimal("0.02");
            case UNVESTED_COMPENSATION -> BigDecimal.ZERO;
            default -> BigDecimal.ONE;
        };
    }

    private static BigDecimal tradeRiskCap(HoldingClassification classification) {
        return switch (classification) {
            case SPECULATIVE -> new BigDecimal("0.0020");
            case THEMATIC_ETF, TACTICAL_STOCK, CYCLICAL_TACTICAL, TURNAROUND_TACTICAL -> new BigDecimal("0.0030");
            case QUALITY_STOCK, QUALITY_GROWTH_HIGH_VOL -> new BigDecimal("0.0040");
            default -> ABSOLUTE_TRADE_RISK;
        };
    }

    private static DecisionConfidence confidence(EvidenceQuality quality) {
        return switch (quality) {
            case HEALTHY -> DecisionConfidence.HIGH;
            case PARTIAL -> DecisionConfidence.MEDIUM;
            case STALE, SUSPECT -> DecisionConfidence.LOW;
            case MISSING -> DecisionConfidence.WAIT_FOR_DATA;
        };
    }

    public record Input(
            HoldingClassification classification,
            boolean classificationConfirmed,
            BigDecimal currentWeight,
            BigDecimal projectedWeight,
            BigDecimal proposedTradeRisk,
            BigDecimal currentOpenStockRisk,
            BigDecimal currentClusterRisk,
            boolean averagingDown,
            boolean thesisImproving,
            boolean anchoredToCostBasis,
            Instant lastDecisionAt,
            Instant now,
            EvidenceQuality quality) {}

    public record Analysis(
            boolean allowed,
            boolean preciseQuantityAllowed,
            BigDecimal weightCap,
            BigDecimal tradeRiskCap,
            BigDecimal projectedOpenStockRisk,
            BigDecimal projectedClusterRisk,
            DecisionConfidence confidence,
            List<String> ruleIds,
            List<String> reasons,
            List<String> risks) {
        public Analysis {
            ruleIds = List.copyOf(ruleIds);
            reasons = List.copyOf(reasons);
            risks = List.copyOf(risks);
        }
    }
}
