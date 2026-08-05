package com.example.portfolio.strategy.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.strategy.RuleIds;
import com.example.portfolio.strategy.market.EvidenceQuality;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class HoldingPolicyTest {
    private static final Instant NOW = Instant.parse("2026-08-05T12:00:00Z");

    @Test
    void dxyzIsNotGuessedAsQualityAndUnvestedHasNoAction() {
        assertThat(HoldingClassifier.suggest("DXYZ", "EQUITY", false, false).classification())
                .isEqualTo(HoldingClassification.UNKNOWN);
        var rsu = analyze(HoldingClassification.UNVESTED_COMPENSATION, true, "0", "0", "0", EvidenceQuality.HEALTHY);
        assertThat(rsu.allowed()).isFalse();
        assertThat(rsu.ruleIds()).contains(RuleIds.POSITION_UNVESTED_BLOCK);
    }

    @Test
    void decimalHardCapsAndRiskBudgetsNeverRoundUp() {
        var speculative = analyze(
                HoldingClassification.SPECULATIVE, true, "0.019", "0.02000001", "0.00200001", EvidenceQuality.HEALTHY);
        assertThat(speculative.allowed()).isFalse();
        assertThat(speculative.weightCap()).isEqualByComparingTo("0.02");
        assertThat(speculative.tradeRiskCap()).isEqualByComparingTo("0.002");
        assertThat(speculative.ruleIds()).contains(RuleIds.RISK_WEIGHT_CAP, RuleIds.RISK_TRADE_BUDGET);
    }

    @Test
    void clusterTotalCoolingAveragingAndAnchoringAreBlocking() {
        var result = HoldingPolicy.analyze(new HoldingPolicy.Input(
                HoldingClassification.QUALITY_STOCK,
                true,
                new BigDecimal("0.08"),
                new BigDecimal("0.09"),
                new BigDecimal("0.004"),
                new BigDecimal("0.018"),
                new BigDecimal("0.005"),
                true,
                false,
                true,
                NOW.minusSeconds(3600),
                NOW,
                EvidenceQuality.HEALTHY));
        assertThat(result.allowed()).isFalse();
        assertThat(result.ruleIds())
                .contains(
                        RuleIds.RISK_TOTAL_OPEN,
                        RuleIds.RISK_CLUSTER_CAP,
                        RuleIds.RISK_COOLING_PERIOD,
                        RuleIds.RISK_INVALID_AVERAGING,
                        RuleIds.RISK_ANCHORING);
    }

    @Test
    void staleEvidenceNeverReturnsPreciseQuantity() {
        var result = analyze(HoldingClassification.QUALITY_STOCK, true, "0.05", "0.06", "0.003", EvidenceQuality.STALE);
        assertThat(result.preciseQuantityAllowed()).isFalse();
        assertThat(result.ruleIds()).contains(RuleIds.DATA_STALE_QUANTITY_BLOCK);
    }

    private static HoldingPolicy.Analysis analyze(
            HoldingClassification classification,
            boolean confirmed,
            String currentWeight,
            String projectedWeight,
            String tradeRisk,
            EvidenceQuality quality) {
        return HoldingPolicy.analyze(new HoldingPolicy.Input(
                classification,
                confirmed,
                new BigDecimal(currentWeight),
                new BigDecimal(projectedWeight),
                new BigDecimal(tradeRisk),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                true,
                false,
                null,
                NOW,
                quality));
    }
}
