package com.example.portfolio.analysis.application;

import com.example.portfolio.analysis.decision.AssetDecisionRouter;
import com.example.portfolio.analysis.decision.DecisionContext;
import com.example.portfolio.analysis.decision.PortfolioConstraintEngine;
import com.example.portfolio.analysis.domain.AnalysisReadiness;
import com.example.portfolio.analysis.domain.HoldingAnalysisResult;
import com.example.portfolio.analysis.domain.HoldingEvidence;
import com.example.portfolio.analysis.domain.RecommendationAction;
import com.example.portfolio.analysis.domain.RecommendationCandidate;
import com.example.portfolio.analysis.domain.RecommendationResolution;
import com.example.portfolio.analysis.domain.StrategyDefinition;
import com.example.portfolio.analysis.infrastructure.HoldingAnalysisStore;
import com.example.portfolio.strategy.market.EvidenceQuality;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public final class HoldingAnalysisApplicationService {
    private static final Duration VALIDITY = Duration.ofHours(24);

    private final HoldingEvidenceAssembler evidenceAssembler;
    private final AnalysisFreshnessPolicy freshness;
    private final com.example.portfolio.analysis.decision.RecommendationConflictResolver conflictResolver;
    private final PortfolioConstraintEngine portfolioConstraints;
    private final AssetDecisionRouter assetDecisions;
    private final HoldingAnalysisStore store;
    private final Clock clock;

    public HoldingAnalysisApplicationService(
            HoldingEvidenceAssembler evidenceAssembler,
            AnalysisFreshnessPolicy freshness,
            com.example.portfolio.analysis.decision.RecommendationConflictResolver conflictResolver,
            PortfolioConstraintEngine portfolioConstraints,
            AssetDecisionRouter assetDecisions,
            HoldingAnalysisStore store,
            Clock clock) {
        this.evidenceAssembler = evidenceAssembler;
        this.freshness = freshness;
        this.conflictResolver = conflictResolver;
        this.portfolioConstraints = portfolioConstraints;
        this.assetDecisions = assetDecisions;
        this.store = store;
        this.clock = clock;
    }

    public List<AnalyzedHolding> analyzeAll(UUID userId) {
        return evidenceAssembler.assembleAll(userId).stream()
                .map(this::analyzeAndPersist)
                .toList();
    }

    public AnalyzedHolding analyze(UUID userId, UUID positionId) {
        return analyzeAndPersist(evidenceAssembler.assemble(userId, positionId));
    }

    private AnalyzedHolding analyzeAndPersist(HoldingEvidence evidence) {
        var now = clock.instant();
        var state = HoldingEvidenceReadiness.assess(evidence, now, freshness);
        var policy = policy(evidence.position().classification(), evidence.strategy());
        var candidates = candidates(evidence, state, policy);
        var resolution = conflictResolver.resolve(candidates);
        var sizing = size(evidence, state, policy, resolution.winner().action(), now);
        var confidence = confidence(evidence, state);
        var result = new HoldingAnalysisResult(
                evidence.position().id(),
                analysisStatus(state),
                state,
                confidence,
                evidence.currentWeight(),
                policy.targetMin(),
                policy.targetMax(),
                sizing.exactQuantityAllowed(),
                resolution.winner().action(),
                sizing.quantityMin(),
                sizing.quantityMax(),
                candidates.stream()
                        .map(RecommendationCandidate::reason)
                        .distinct()
                        .toList(),
                candidates.stream()
                        .flatMap(value -> value.risks().stream())
                        .distinct()
                        .toList(),
                changeConditions(evidence, state, resolution.winner().action()),
                candidates.stream().map(RecommendationCandidate::ruleId).toList(),
                evidenceReferences(evidence),
                evidence.strategy().version(),
                evidence.strategy().configHash(),
                evidence.dataAsOf(),
                now.plus(VALIDITY),
                AnalysisChecksum.sha256(evidence + ":" + state + ":" + resolution));
        var snapshotId = store.append(result, now);
        return new AnalyzedHolding(snapshotId, evidence, result, resolution);
    }

    private List<RecommendationCandidate> candidates(HoldingEvidence evidence, AnalysisReadiness state, Policy policy) {
        var context = new DecisionContext(
                evidence, state, policy.targetMin(), policy.targetMax(), policy.normalMax(), policy.hardMax());
        var values = new ArrayList<>(portfolioConstraints.evaluate(context));
        values.addAll(assetDecisions.evaluate(context));
        return List.copyOf(values);
    }

    private PositionSizing.Result size(
            HoldingEvidence evidence,
            AnalysisReadiness state,
            Policy policy,
            RecommendationAction action,
            java.time.Instant now) {
        if ((action != RecommendationAction.ADD && action != RecommendationAction.STARTER_BUY)
                || policy.targetMin() == null
                || policy.hardMax() == null) {
            return unavailableSizing();
        }
        var availableCash = evidence.trackedCash()
                .amount()
                .subtract(evidence.emergencyCash().amount())
                .max(BigDecimal.ZERO);
        var clusterRiskCapacity = evidence.strategy()
                .clusterOpenRiskMax()
                .subtract(evidence.clusterOpenRisk())
                .max(BigDecimal.ZERO);
        var clusterDollarCapacity = policy.tradeRisk().signum() == 0
                ? BigDecimal.ZERO
                : evidence.portfolioEquity()
                        .amount()
                        .multiply(clusterRiskCapacity)
                        .divide(policy.tradeRisk(), MathContext.DECIMAL64);
        var result = PositionSizing.calculate(new PositionSizing.Input(
                evidence.portfolioEquity().amount(),
                evidence.portfolioEquity().amount(),
                policy.tradeRisk(),
                evidence.quote().last(),
                evidence.stop().formalStop(),
                evidence.quote().last(),
                evidence.position().marketValue(),
                policy.targetMin(),
                policy.targetMax(),
                policy.hardMax(),
                availableCash,
                clusterDollarCapacity,
                evidence.quality(),
                state != AnalysisReadiness.STALE
                        && !freshness.stale(evidence.quote().dataAsOf(), now)));
        return action == RecommendationAction.STARTER_BUY
                ? starter(result, evidence.strategy().qualityStarterFraction())
                : result;
    }

    private static PositionSizing.Result unavailableSizing() {
        return new PositionSizing.Result(false, null, null, null, null, null, null);
    }

    private static PositionSizing.Result starter(PositionSizing.Result value, BigDecimal fraction) {
        if (!value.exactQuantityAllowed()) return value;
        return new PositionSizing.Result(
                true,
                scaled(value.quantityMin(), fraction),
                scaled(value.quantityMax(), fraction),
                scaled(value.quantityByRisk(), fraction),
                scaled(value.quantityByWeightCap(), fraction),
                scaled(value.quantityByAvailableCash(), fraction),
                scaled(value.quantityByClusterCap(), fraction));
    }

    private static BigDecimal scaled(BigDecimal value, BigDecimal fraction) {
        return value == null ? null : value.multiply(fraction).setScale(0, java.math.RoundingMode.FLOOR);
    }

    private static Policy policy(HoldingClassification classification, StrategyDefinition strategy) {
        return switch (classification) {
            case CORE_BROAD_ETF ->
                new Policy(
                        strategy.broadCoreTarget(),
                        strategy.broadCoreTarget(),
                        strategy.broadCoreTarget(),
                        null,
                        strategy.absoluteTradeRiskMax());
            case CORE_TECH_ETF ->
                new Policy(
                        strategy.techCoreTarget(),
                        strategy.techCoreTarget(),
                        strategy.techCoreTarget(),
                        null,
                        strategy.absoluteTradeRiskMax());
            case QUALITY_STOCK, QUALITY_GROWTH_HIGH_VOL -> policy(strategy.quality());
            case THEMATIC_ETF -> policy(strategy.thematicEtf());
            case TACTICAL_STOCK, CYCLICAL_TACTICAL, TURNAROUND_TACTICAL -> policy(strategy.tactical());
            case SPECULATIVE -> policy(strategy.speculative());
            default -> new Policy(null, null, null, null, BigDecimal.ZERO);
        };
    }

    private static Policy policy(StrategyDefinition.PositionPolicy value) {
        return new Policy(value.targetMin(), value.targetMax(), value.normalMax(), value.hardMax(), value.tradeRisk());
    }

    private static String confidence(HoldingEvidence evidence, AnalysisReadiness state) {
        if (state != AnalysisReadiness.READY && state != AnalysisReadiness.PARTIAL) return "WAIT_FOR_DATA";
        if (evidence.position().classification() == HoldingClassification.SPECULATIVE) return "LOW";
        if (qualityCompany(evidence.position().classification())
                && evidence.fundamentals().estimateQuality() != EvidenceQuality.HEALTHY) return "LOW";
        if (state == AnalysisReadiness.PARTIAL || evidence.quality() != EvidenceQuality.HEALTHY) return "LOW";
        return evidence.regime().available() && evidence.drawdown().available() ? "HIGH" : "MEDIUM";
    }

    private static String analysisStatus(AnalysisReadiness readiness) {
        return switch (readiness) {
            case READY -> "READY";
            case PARTIAL -> "PARTIAL";
            case BLOCKED -> "BLOCKED";
            default -> "WAIT_FOR_DATA";
        };
    }

    private static boolean companyEventPolicy(HoldingClassification classification) {
        return switch (classification) {
            case QUALITY_STOCK,
                    QUALITY_GROWTH_HIGH_VOL,
                    TACTICAL_STOCK,
                    CYCLICAL_TACTICAL,
                    TURNAROUND_TACTICAL,
                    SPECULATIVE -> true;
            default -> false;
        };
    }

    private static boolean qualityCompany(HoldingClassification classification) {
        return classification == HoldingClassification.QUALITY_STOCK
                || classification == HoldingClassification.QUALITY_GROWTH_HIGH_VOL;
    }

    private static List<String> changeConditions(
            HoldingEvidence evidence, AnalysisReadiness state, RecommendationAction action) {
        var conditions = new ArrayList<String>();
        if (state != AnalysisReadiness.READY) conditions.add("Required evidence becomes complete, healthy, and fresh.");
        if (action == RecommendationAction.DO_NOT_ADD) {
            conditions.add("Emergency cash and portfolio risk capacity return inside configured limits.");
        }
        if (companyEventPolicy(evidence.position().classification())) {
            conditions.add("The next company event or fundamental update changes the thesis evidence.");
        } else {
            conditions.add("Fund liquidity, holdings concentration, overlap, or market regime materially changes.");
        }
        conditions.add("A completed daily bar confirms a formal stop condition.");
        return List.copyOf(conditions);
    }

    private static List<String> evidenceReferences(HoldingEvidence evidence) {
        var references = new ArrayList<String>();
        references.add("strategy:" + evidence.strategy().version() + ":"
                + evidence.strategy().configHash());
        references.add("position:" + evidence.position().id());
        if (evidence.quote().available())
            references.add("quote:" + evidence.instrument().symbol() + ":"
                    + evidence.quote().dataAsOf());
        if (!evidence.completedBars().isEmpty())
            references.add(
                    "completed-bars:last=" + evidence.completedBars().getLast().marketDate());
        if (evidence.fundamentals().available())
            references.add("fundamentals:" + evidence.fundamentals().dataAsOf());
        if (evidence.valuation().available())
            references.add("valuation:" + evidence.valuation().dataAsOf());
        if (evidence.nextEvent().available())
            references.add("event:" + evidence.nextEvent().eventAt());
        if (evidence.thesis().available())
            references.add("thesis:expires=" + evidence.thesis().expiresAt());
        if (evidence.regime().available())
            references.add("regime:" + evidence.regime().dataAsOf());
        if (evidence.drawdown().available())
            references.add("drawdown:" + evidence.drawdown().dataAsOf());
        if (evidence.stop().formalStop() != null)
            references.add("formal-stop:" + evidence.stop().formalStop());
        return List.copyOf(references);
    }

    private record Policy(
            BigDecimal targetMin,
            BigDecimal targetMax,
            BigDecimal normalMax,
            BigDecimal hardMax,
            BigDecimal tradeRisk) {}

    public record AnalyzedHolding(
            UUID snapshotId,
            HoldingEvidence evidence,
            HoldingAnalysisResult analysis,
            RecommendationResolution resolution) {}
}
