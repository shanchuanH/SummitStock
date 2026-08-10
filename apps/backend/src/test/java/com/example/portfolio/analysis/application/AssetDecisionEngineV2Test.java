package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.analysis.decision.CoreEtfDecisionEngine;
import com.example.portfolio.analysis.decision.DecisionContext;
import com.example.portfolio.analysis.decision.PortfolioConstraintEngine;
import com.example.portfolio.analysis.decision.QualityStockDecisionEngine;
import com.example.portfolio.analysis.decision.RecommendationConflictResolver;
import com.example.portfolio.analysis.decision.SpeculativeDecisionEngine;
import com.example.portfolio.analysis.decision.ThematicEtfDecisionEngine;
import com.example.portfolio.analysis.domain.AnalysisReadiness;
import com.example.portfolio.analysis.domain.HoldingEvidence;
import com.example.portfolio.analysis.domain.RecommendationAction;
import com.example.portfolio.analysis.domain.RecommendationCandidate;
import com.example.portfolio.strategy.market.EvidenceQuality;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssetDecisionEngineV2Test {
    private final PortfolioConstraintEngine constraints = new PortfolioConstraintEngine();
    private final RecommendationConflictResolver resolver = new RecommendationConflictResolver();
    private final QualityStockDecisionEngine quality = new QualityStockDecisionEngine();

    @Test
    void underweightAloneNeverBuys() {
        var evidence = qualityEvidence("HEALTHY", "RICH", "FLAT", "DOWNTREND", "0.01");

        assertThat(resolve(evidence, context(evidence), quality.evaluate(context(evidence))))
                .isEqualTo(RecommendationAction.HOLD);
    }

    @Test
    void cheapButBrokenNeverBuys() {
        var evidence = qualityEvidence("BROKEN", "DEEP_DISCOUNT", "POSITIVE", "REVERSAL_CONFIRMED", "0.01");

        assertThat(resolve(evidence, context(evidence), quality.evaluate(context(evidence))))
                .isEqualTo(RecommendationAction.EXIT);
    }

    @Test
    void strongButExtremeValuationDoesNotAdd() {
        var evidence = qualityEvidence("STRONG", "EXTREME", "POSITIVE", "UPTREND", "0.01");

        assertThat(resolve(evidence, context(evidence), quality.evaluate(context(evidence))))
                .isEqualTo(RecommendationAction.DO_NOT_ADD);
    }

    @Test
    void qualityAtNormalMaximumDoesNotAddBeforeHardCap() {
        var evidence = qualityEvidence("STRONG", "FAIR", "POSITIVE", "UPTREND", "0.13");
        var context = new DecisionContext(
                evidence,
                AnalysisReadiness.READY,
                new BigDecimal("0.04"),
                new BigDecimal("0.08"),
                new BigDecimal("0.12"),
                new BigDecimal("0.15"));

        assertThat(resolve(evidence, context, quality.evaluate(context)))
                .isEqualTo(RecommendationAction.DO_NOT_ADD);
    }

    @Test
    void deepDiscountWithWeakTrendPermitsOnlyStarter() {
        var evidence = qualityEvidence("STRONG", "DEEP_DISCOUNT", "FLAT", "DOWNTREND", "0.01");

        assertThat(resolve(evidence, context(evidence), quality.evaluate(context(evidence))))
                .isEqualTo(RecommendationAction.STARTER_BUY);
    }

    @Test
    void attractivePositiveRevisionAndReversalPermitsAdd() {
        var evidence = qualityEvidence("STRONG", "ATTRACTIVE", "POSITIVE", "REVERSAL_CONFIRMED", "0.01");

        assertThat(resolve(evidence, context(evidence), quality.evaluate(context(evidence))))
                .isEqualTo(RecommendationAction.ADD);
    }

    @Test
    void cheapButWeakeningWithStronglyNegativeRevisionsDoesNotAdd() {
        var evidence = qualityEvidence(
                "WEAKENING", "DEEP_DISCOUNT", "STRONGLY_NEGATIVE", "REVERSAL_CONFIRMED", "0.01");

        assertThat(resolve(evidence, context(evidence), quality.evaluate(context(evidence))))
                .isEqualTo(RecommendationAction.DO_NOT_ADD);
    }

    @Test
    void missingEstimateCannotProduceAnExactNewBuyQuantity() {
        var base = qualityEvidence("STRONG", "ATTRACTIVE", "MISSING", "REVERSAL_CONFIRMED", "0.01");
        var evidence = copy(
                base,
                base.currentWeight(),
                base.indicators(),
                new HoldingEvidence.FundamentalSnapshot(
                        true,
                        EvidenceQuality.HEALTHY,
                        base.dataAsOf(),
                        "STRONG",
                        "MISSING",
                        EvidenceQuality.MISSING),
                base.valuation(),
                base.nextEvent(),
                base.drawdown(),
                base.stop());
        var action = resolve(evidence, context(evidence), quality.evaluate(context(evidence)));

        assertThat(action).isEqualTo(RecommendationAction.DO_NOT_ADD);
        assertThat(PositionSizing.calculate(PositionSizingV2Fixtures.valid(action)).exactQuantityAllowed())
                .isFalse();
    }

    @Test
    void hardCapBeatsOpportunity() {
        var evidence = qualityEvidence("STRONG", "ATTRACTIVE", "POSITIVE", "UPTREND", "0.10");
        var context = new DecisionContext(
                evidence,
                AnalysisReadiness.READY,
                new BigDecimal("0.04"),
                new BigDecimal("0.08"),
                new BigDecimal("0.20"),
                new BigDecimal("0.05"));

        assertThat(resolve(evidence, context, quality.evaluate(context))).isEqualTo(RecommendationAction.TRIM);
    }

    @Test
    void painLineBeatsStockOpportunity() {
        var evidence = withDrawdown(
                qualityEvidence("STRONG", "ATTRACTIVE", "POSITIVE", "UPTREND", "0.01"),
                new BigDecimal("0.20"),
                "PAIN_LINE",
                true);

        assertThat(resolve(evidence, context(evidence), quality.evaluate(context(evidence))))
                .isEqualTo(RecommendationAction.PAUSE_NEW_RISK);
    }

    @Test
    void marketDrivenFifteenPercentCanDeployCoreEtfDip() {
        var evidence = withDrawdown(
                HoldingEvidenceFixtures.evidence("QQQM", "ETF", HoldingClassification.CORE_TECH_ETF),
                new BigDecimal("0.15"),
                "ETF_DIP_MARKET_DRIVEN",
                false);
        var context = new DecisionContext(
                evidence,
                AnalysisReadiness.READY,
                evidence.strategy().broadCoreTarget(),
                evidence.strategy().broadCoreTarget(),
                evidence.strategy().broadCoreTarget(),
                null);

        assertThat(resolve(evidence, context, new CoreEtfDecisionEngine().evaluate(context)))
                .isEqualTo(RecommendationAction.DEPLOY_DIP_TRANCHE);
    }

    @Test
    void speculativeStopBeatsOptimism() {
        var evidence =
                withStop(HoldingEvidenceFixtures.evidence("DXYZ", "EQUITY", HoldingClassification.SPECULATIVE), true);
        var policy = evidence.strategy().speculative();
        var context = new DecisionContext(
                evidence,
                AnalysisReadiness.PARTIAL,
                policy.targetMin(),
                policy.targetMax(),
                policy.normalMax(),
                policy.hardMax());

        assertThat(resolve(evidence, context, new SpeculativeDecisionEngine().evaluate(context)))
                .isEqualTo(RecommendationAction.EXIT);
    }

    @Test
    void thematicEtfIgnoresSingleCompanyEarnings() {
        var evidence = withEventRisk(
                HoldingEvidenceFixtures.evidence("DRAM", "ETF", HoldingClassification.THEMATIC_ETF), "EXTREME");
        var policy = evidence.strategy().thematicEtf();
        var context = new DecisionContext(
                evidence,
                AnalysisReadiness.READY,
                policy.targetMin(),
                policy.targetMax(),
                policy.normalMax(),
                policy.hardMax());
        var candidates = new ThematicEtfDecisionEngine().evaluate(context);

        assertThat(resolve(evidence, context, candidates)).isEqualTo(RecommendationAction.ADD);
        assertThat(candidates)
                .extracting(RecommendationCandidate::ruleId)
                .noneMatch(rule -> rule.contains("EARNINGS") || rule.contains("COMPANY_EVENT"));
    }

    private RecommendationAction resolve(
            HoldingEvidence evidence, DecisionContext context, List<RecommendationCandidate> assetCandidates) {
        var candidates = new ArrayList<>(constraints.evaluate(context));
        candidates.addAll(assetCandidates);
        return resolver.resolve(candidates).winner().action();
    }

    private static DecisionContext context(HoldingEvidence evidence) {
        var policy = evidence.strategy().quality();
        return new DecisionContext(
                evidence,
                AnalysisReadiness.READY,
                policy.targetMin(),
                policy.targetMax(),
                policy.normalMax(),
                policy.hardMax());
    }

    private static HoldingEvidence qualityEvidence(
            String health, String valuation, String revision, String priceState, String currentWeight) {
        var value = HoldingEvidenceFixtures.evidence("GOOGL", "EQUITY", HoldingClassification.QUALITY_STOCK);
        return copy(
                value,
                new BigDecimal(currentWeight),
                new HoldingEvidence.IndicatorSet(true, true, 55.0, 3.0, priceState),
                new HoldingEvidence.FundamentalSnapshot(
                        true, EvidenceQuality.HEALTHY, value.dataAsOf(), health, revision, EvidenceQuality.HEALTHY),
                new HoldingEvidence.ValuationSnapshot(true, valuation, "HIGH", 300, 0, false, value.dataAsOf()),
                value.nextEvent(),
                value.drawdown(),
                value.stop());
    }

    private static HoldingEvidence withDrawdown(
            HoldingEvidence value, BigDecimal drawdown, String state, boolean noNewRisk) {
        return copy(
                value,
                value.currentWeight(),
                value.indicators(),
                value.fundamentals(),
                value.valuation(),
                value.nextEvent(),
                new HoldingEvidence.PortfolioDrawdownSnapshot(true, drawdown, state, noNewRisk, value.dataAsOf()),
                value.stop());
    }

    private static HoldingEvidence withStop(HoldingEvidence value, boolean closeConfirmed) {
        return copy(
                value,
                value.currentWeight(),
                value.indicators(),
                value.fundamentals(),
                value.valuation(),
                value.nextEvent(),
                value.drawdown(),
                new HoldingEvidence.StopEvidence(
                        value.stop().formalStop(), value.stop().liveStop(), closeConfirmed, false));
    }

    private static HoldingEvidence withEventRisk(HoldingEvidence value, String risk) {
        return copy(
                value,
                value.currentWeight(),
                value.indicators(),
                value.fundamentals(),
                value.valuation(),
                new HoldingEvidence.EarningsEvent(true, value.nextEvent().eventAt(), risk),
                value.drawdown(),
                value.stop());
    }

    private static HoldingEvidence copy(
            HoldingEvidence value,
            BigDecimal currentWeight,
            HoldingEvidence.IndicatorSet indicators,
            HoldingEvidence.FundamentalSnapshot fundamentals,
            HoldingEvidence.ValuationSnapshot valuation,
            HoldingEvidence.EarningsEvent event,
            HoldingEvidence.PortfolioDrawdownSnapshot drawdown,
            HoldingEvidence.StopEvidence stop) {
        return new HoldingEvidence(
                value.position(),
                value.instrument(),
                value.portfolioEquity(),
                value.trackedCash(),
                value.emergencyCash(),
                value.tacticalReserve(),
                currentWeight,
                value.clusterWeight(),
                value.clusterOpenRisk(),
                value.totalOpenRisk(),
                value.quote(),
                value.completedBars(),
                indicators,
                fundamentals,
                valuation,
                event,
                value.thesis(),
                value.regime(),
                drawdown,
                stop,
                value.profile(),
                value.capitalQuality(),
                value.riskQuality(),
                value.riskDataAsOf(),
                value.providerHardError(),
                value.quality(),
                value.strategy(),
                value.dataAsOf());
    }
}
