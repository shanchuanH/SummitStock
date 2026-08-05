package com.example.portfolio.strategy.market;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.strategy.RuleIds;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MarketContextGoldenTest {
    @Test
    void healthyNarrowPanicMissingAndHardCapRegimes() {
        var healthy = MarketRegimeEngine.classify(
                regime(0.95, 0.90, 0.90, 0.90, false, false, 16, 0.72, false, 55, false, EvidenceQuality.HEALTHY));
        assertThat(healthy.label()).isEqualTo(MarketRegimeEngine.Label.STRONG_GREEN);
        assertThat(healthy.confidence()).isEqualTo(DecisionConfidence.HIGH);

        var narrow = MarketRegimeEngine.classify(
                regime(0.90, 0.85, 0.45, 0.80, false, false, 18, 0.42, false, 52, true, EvidenceQuality.HEALTHY));
        assertThat(narrow.ruleIds()).contains(RuleIds.REGIME_NARROW_RALLY);

        var panic = MarketRegimeEngine.classify(
                regime(0.70, 0.70, 0.70, 0.70, false, false, 35, 0.22, false, 45, false, EvidenceQuality.HEALTHY));
        assertThat(panic.label()).isEqualTo(MarketRegimeEngine.Label.RED);
        assertThat(panic.ruleIds()).contains(RuleIds.REGIME_PANIC_OVERRIDE);

        var missing = MarketRegimeEngine.classify(
                regime(1, 1, 1, 1, false, false, 12, 0.9, false, 60, false, EvidenceQuality.MISSING));
        assertThat(missing.label()).isEqualTo(MarketRegimeEngine.Label.MISSING_DATA);
        assertThat(missing.confidence()).isEqualTo(DecisionConfidence.WAIT_FOR_DATA);

        var capped = MarketRegimeEngine.classify(
                regime(1, 1, 1, 1, true, true, 20, 0.7, true, 35, false, EvidenceQuality.HEALTHY));
        assertThat(capped.label()).isEqualTo(MarketRegimeEngine.Label.YELLOW);
        assertThat(capped.tacticalCapFivePercent()).isTrue();
    }

    @Test
    void staleCannotProduceStrongGreen() {
        var result = MarketRegimeEngine.classify(
                regime(1, 1, 1, 1, false, false, 12, 0.9, false, 60, false, EvidenceQuality.STALE));
        assertThat(result.label()).isEqualTo(MarketRegimeEngine.Label.GREEN);
        assertThat(result.confidence()).isEqualTo(DecisionConfidence.LOW);
        assertThat(result.ruleIds()).contains(RuleIds.DATA_STALE_CAP);
    }

    @Test
    void fifteenPercentNeedsBroadMarketConfirmation() {
        var qqqOnlyFour = DrawdownEngine.classify(drawdown("85000", "100000", -0.03, -0.04, 0.65, 0.2, 0.2));
        assertThat(qqqOnlyFour.state()).isEqualTo(DrawdownEngine.State.REVIEW_POSITION_SPECIFIC);
        assertThat(qqqOnlyFour.marketDriven()).isFalse();

        var qqqFourteen = DrawdownEngine.classify(drawdown("85000", "100000", -0.12, -0.14, 0.28, 0.8, 0.2));
        assertThat(qqqFourteen.state()).isEqualTo(DrawdownEngine.State.ETF_DIP_MARKET_DRIVEN);
        assertThat(qqqFourteen.marketDriven()).isTrue();
    }

    @Test
    void individualStockLossAndPainLineAreDistinguished() {
        var stockLoss = DrawdownEngine.classify(drawdown("85000", "100000", -0.02, -0.04, 0.7, 0.2, 0.72));
        assertThat(stockLoss.source()).isEqualTo(DrawdownEngine.Source.POSITION_SPECIFIC);
        assertThat(stockLoss.ruleIds()).contains(RuleIds.DRAWDOWN_POSITION_SPECIFIC);

        var pain = DrawdownEngine.classify(drawdown("79000", "100000", -0.18, -0.22, 0.2, 0.9, 0.3));
        assertThat(pain.state()).isEqualTo(DrawdownEngine.State.PAIN_LINE);
        assertThat(pain.ruleIds()).contains(RuleIds.DRAWDOWN_PAIN_LINE);
    }

    @Test
    void staleEvidenceBlocksPreciseCriticalDrawdownAdvice() {
        var input = new DrawdownEngine.Input(
                new BigDecimal("85000"),
                new BigDecimal("100000"),
                -0.12,
                -0.14,
                0.25,
                0.8,
                0.2,
                0.3,
                EvidenceQuality.STALE);
        var result = DrawdownEngine.classify(input);
        assertThat(result.state()).isEqualTo(DrawdownEngine.State.WAIT_FOR_DATA);
        assertThat(result.marketDriven()).isFalse();
        assertThat(result.confidence()).isEqualTo(DecisionConfidence.LOW);
    }

    @Test
    void highWaterMarkNeverMovesDown() {
        var below = DrawdownEngine.classify(drawdown("99000", "100000", 0, 0, 0.8, 0.1, 0.1));
        var above = DrawdownEngine.classify(drawdown("101000", "100000", 0, 0, 0.8, 0.1, 0.1));
        assertThat(below.highWaterMark()).isEqualByComparingTo("100000");
        assertThat(above.highWaterMark()).isEqualByComparingTo("101000");
        assertThat(above.drawdown()).isZero();
    }

    private static MarketRegimeEngine.Input regime(
            double trend,
            double momentum,
            double breadth,
            double stress,
            boolean spyBelow,
            boolean qqqBelow,
            double vix,
            double breadth50,
            boolean macdNegative,
            double rsi,
            boolean narrow,
            EvidenceQuality quality) {
        return new MarketRegimeEngine.Input(
                trend,
                momentum,
                breadth,
                stress,
                spyBelow,
                qqqBelow,
                vix,
                breadth50,
                macdNegative,
                rsi,
                narrow,
                quality);
    }

    private static DrawdownEngine.Input drawdown(
            String current,
            String high,
            double spy,
            double qqq,
            double breadth,
            double stress,
            double positionContribution) {
        return new DrawdownEngine.Input(
                new BigDecimal(current),
                new BigDecimal(high),
                spy,
                qqq,
                breadth,
                stress,
                positionContribution,
                0.2,
                EvidenceQuality.HEALTHY);
    }
}
