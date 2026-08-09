package com.example.portfolio.analysis.application;

import com.example.portfolio.analysis.domain.AnalysisReadiness;
import com.example.portfolio.analysis.domain.HoldingAnalysisResult;
import com.example.portfolio.analysis.domain.HoldingEvidence;
import com.example.portfolio.analysis.domain.RecommendationAction;
import com.example.portfolio.analysis.domain.RecommendationCandidate;
import com.example.portfolio.analysis.domain.RecommendationResolution;
import com.example.portfolio.analysis.domain.StrategyDefinition;
import com.example.portfolio.analysis.infrastructure.HoldingAnalysisStore;
import com.example.portfolio.estimates.EstimateRevisionEngine;
import com.example.portfolio.estimates.EstimateRevisionPolicy;
import com.example.portfolio.strategy.market.EvidenceQuality;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import com.example.portfolio.valuation.QualityValuationDecisionRule;
import com.example.portfolio.valuation.ValuationEngineV2;
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
    private final RecommendationConflictResolver conflictResolver;
    private final HoldingAnalysisStore store;
    private final Clock clock;

    public HoldingAnalysisApplicationService(
            HoldingEvidenceAssembler evidenceAssembler,
            AnalysisFreshnessPolicy freshness,
            RecommendationConflictResolver conflictResolver,
            HoldingAnalysisStore store,
            Clock clock) {
        this.evidenceAssembler = evidenceAssembler;
        this.freshness = freshness;
        this.conflictResolver = conflictResolver;
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
                evidence.strategy().version(),
                evidence.strategy().configHash(),
                evidence.dataAsOf(),
                now.plus(VALIDITY),
                AnalysisChecksum.sha256(evidence + ":" + state + ":" + resolution));
        var snapshotId = store.append(result, now);
        return new AnalyzedHolding(snapshotId, evidence, result, resolution);
    }

    private List<RecommendationCandidate> candidates(HoldingEvidence evidence, AnalysisReadiness state, Policy policy) {
        var values = new ArrayList<RecommendationCandidate>();
        if (state != AnalysisReadiness.READY && state != AnalysisReadiness.PARTIAL) {
            values.add(candidate(
                    RecommendationAction.WAIT_FOR_DATA,
                    "DO_NOT",
                    1,
                    "ANALYSIS.EVIDENCE.REQUIRED",
                    "Required evidence is not actionable: " + state + ".",
                    "Acting on incomplete or stale evidence can create false precision."));
        }
        if (!evidence.position().classificationConfirmed()
                || evidence.position().classification() == HoldingClassification.UNKNOWN) {
            values.add(candidate(
                    RecommendationAction.WAIT_FOR_DATA,
                    "DO_NOT",
                    2,
                    "ANALYSIS.CLASSIFICATION.CONFIRM",
                    "The holding classification must be confirmed before strategy rules are applied.",
                    "An incorrect classification applies the wrong risk policy."));
        }
        if (evidence.emergencyCash().amount().compareTo(evidence.strategy().emergencyCashFloor()) < 0
                || evidence.drawdown().noNewRisk()) {
            values.add(candidate(
                    RecommendationAction.DO_NOT_ADD,
                    "DO_NOT",
                    3,
                    "PORTFOLIO.PAIN_OR_CASH.FREEZE",
                    "New risk is frozen by the emergency-cash or portfolio drawdown policy.",
                    "Adding risk could impair the emergency reserve or breach the pain line."));
        }
        if (evidence.stop().catastrophic()
                || evidence.stop().closeConfirmed()
                || evidence.thesis().invalidated()) {
            values.add(candidate(
                    RecommendationAction.EXIT,
                    "MUST_ACT",
                    4,
                    "POSITION.STOP_OR_THESIS.EXIT",
                    "A confirmed formal stop, catastrophic breach, or thesis invalidation requires exit review.",
                    "Delay can increase loss beyond the defined risk budget."));
        }
        if (policy.hardMax() != null && evidence.currentWeight().compareTo(policy.hardMax()) > 0) {
            values.add(candidate(
                    RecommendationAction.TRIM,
                    "MUST_ACT",
                    5,
                    "POSITION.HARD_CAP.TRIM",
                    "Position weight exceeds the configured hard cap.",
                    "Single-position concentration is above the strategy limit."));
        }
        if (companyEventPolicy(evidence.position().classification())
                && evidence.nextEvent().available()
                && "HIGH".equalsIgnoreCase(evidence.nextEvent().riskLevel())) {
            values.add(candidate(
                    RecommendationAction.WAIT_FOR_CONFIRMATION,
                    "WATCH",
                    6,
                    "POSITION.EVENT.RISK",
                    "A high-risk company event is approaching; wait for confirmation before adding.",
                    "Event gaps can bypass normal stop execution."));
        }
        if (evidence.clusterOpenRisk().compareTo(evidence.strategy().clusterOpenRiskMax()) >= 0
                || evidence.totalOpenRisk().compareTo(evidence.strategy().totalOpenRiskMax()) >= 0) {
            values.add(candidate(
                    RecommendationAction.DO_NOT_ADD,
                    "DO_NOT",
                    7,
                    "PORTFOLIO.RISK_CAP.DO_NOT_ADD",
                    "Portfolio or cluster open-risk capacity is exhausted.",
                    "Correlated positions may lose together."));
        }
        if (qualityCompany(evidence.position().classification())
                && EstimateRevisionPolicy.blocksQualityAdd(revision(evidence))) {
            values.add(candidate(
                    RecommendationAction.DO_NOT_ADD,
                    "DO_NOT",
                    7,
                    "QUALITY.REVISION.STRONGLY_NEGATIVE",
                    "Forward EPS/revenue revisions are strongly negative.",
                    "Adding while forward expectations are falling compounds fundamental risk."));
        }
        var valuationDecision = qualityValuationDecision(evidence);
        if ((state == AnalysisReadiness.READY || state == AnalysisReadiness.PARTIAL)
                && valuationDecision == QualityValuationDecisionRule.Decision.STARTER_BUY) {
            values.add(candidate(
                    RecommendationAction.STARTER_BUY,
                    "NORMAL",
                    8,
                    "QUALITY.VALUATION.DEEP_DISCOUNT_STARTER",
                    "Healthy company evidence and a high-confidence deep discount permit a starter position.",
                    "A starter is deliberately limited and a later add requires independent confirmation."));
        } else if ((state == AnalysisReadiness.READY || state == AnalysisReadiness.PARTIAL)
                && valuationDecision == QualityValuationDecisionRule.Decision.ADD) {
            values.add(candidate(
                    RecommendationAction.ADD,
                    "NORMAL",
                    8,
                    "QUALITY.VALUATION.NORMAL_ADD",
                    "Health, valuation, revisions, trend, and portfolio capacity all permit an add.",
                    "Market and thesis conditions can change before execution."));
        }
        values.add(candidate(
                state == AnalysisReadiness.READY ? RecommendationAction.HOLD : RecommendationAction.WATCH,
                state == AnalysisReadiness.READY ? "NORMAL" : "WATCH",
                9,
                "POSITION.HOLD_OR_WATCH",
                state == AnalysisReadiness.READY
                        ? "No higher-priority portfolio or holding rule requires a change."
                        : "Monitor the holding until all required evidence is ready.",
                "A later price, event, or evidence update can change this conclusion."));
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
                        strategy.broadCoreTarget(), strategy.broadCoreTarget(), null, strategy.absoluteTradeRiskMax());
            case CORE_TECH_ETF ->
                new Policy(strategy.techCoreTarget(), strategy.techCoreTarget(), null, strategy.absoluteTradeRiskMax());
            case QUALITY_STOCK, QUALITY_GROWTH_HIGH_VOL -> policy(strategy.quality());
            case THEMATIC_ETF -> policy(strategy.thematicEtf());
            case TACTICAL_STOCK, CYCLICAL_TACTICAL, TURNAROUND_TACTICAL -> policy(strategy.tactical());
            case SPECULATIVE -> policy(strategy.speculative());
            default -> new Policy(null, null, null, BigDecimal.ZERO);
        };
    }

    private static Policy policy(StrategyDefinition.PositionPolicy value) {
        return new Policy(value.targetMin(), value.targetMax(), value.hardMax(), value.tradeRisk());
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

    private static EstimateRevisionEngine.RevisionState revision(HoldingEvidence evidence) {
        try {
            return EstimateRevisionEngine.RevisionState.valueOf(
                    evidence.fundamentals().estimateRevision());
        } catch (IllegalArgumentException | NullPointerException exception) {
            return EstimateRevisionEngine.RevisionState.MISSING;
        }
    }

    private static QualityValuationDecisionRule.Decision qualityValuationDecision(HoldingEvidence evidence) {
        if (!qualityCompany(evidence.position().classification())) return QualityValuationDecisionRule.Decision.HOLD;
        var normalMax = evidence.strategy().quality().normalMax();
        return new QualityValuationDecisionRule()
                .evaluate(new QualityValuationDecisionRule.Input(
                        true,
                        health(evidence),
                        valuation(evidence),
                        revision(evidence),
                        valuationPriceState(evidence.indicators().priceState()),
                        evidence.emergencyCash()
                                                .amount()
                                                .compareTo(evidence.strategy().emergencyCashFloor())
                                        < 0
                                || evidence.drawdown().noNewRisk()
                                || evidence.clusterOpenRisk()
                                                .compareTo(evidence.strategy().clusterOpenRiskMax())
                                        >= 0
                                || evidence.totalOpenRisk()
                                                .compareTo(evidence.strategy().totalOpenRiskMax())
                                        >= 0,
                        evidence.thesis().invalidated(),
                        evidence.stop().catastrophic(),
                        evidence.currentWeight(),
                        normalMax,
                        evidence.valuation().priorStarterCount(),
                        evidence.valuation().independentConfirmation()));
    }

    private static QualityValuationDecisionRule.PriceState valuationPriceState(String value) {
        return switch (value) {
            case "STRONG_UPTREND", "UPTREND" -> QualityValuationDecisionRule.PriceState.UPTREND;
            case "REVERSAL_CONFIRMED" -> QualityValuationDecisionRule.PriceState.REVERSAL_CONFIRMED;
            case "NEUTRAL", "WEAK", "DOWNTREND", "REVERSAL_SETUP", "BREAKDOWN" ->
                QualityValuationDecisionRule.PriceState.DOWNTREND;
            default -> QualityValuationDecisionRule.PriceState.MISSING;
        };
    }

    private static ValuationEngineV2.CompanyHealth health(HoldingEvidence evidence) {
        try {
            return ValuationEngineV2.CompanyHealth.valueOf(
                    evidence.fundamentals().financialHealth());
        } catch (IllegalArgumentException | NullPointerException exception) {
            return ValuationEngineV2.CompanyHealth.MISSING;
        }
    }

    private static ValuationEngineV2.ValuationState valuation(HoldingEvidence evidence) {
        try {
            return ValuationEngineV2.ValuationState.valueOf(evidence.valuation().state());
        } catch (IllegalArgumentException | NullPointerException exception) {
            return ValuationEngineV2.ValuationState.MISSING;
        }
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

    private static RecommendationCandidate candidate(
            RecommendationAction action, String priority, int rank, String rule, String reason, String risk) {
        return new RecommendationCandidate(action, priority, rank, rule, reason, List.of(risk));
    }

    private record Policy(BigDecimal targetMin, BigDecimal targetMax, BigDecimal hardMax, BigDecimal tradeRisk) {}

    public record AnalyzedHolding(
            UUID snapshotId,
            HoldingEvidence evidence,
            HoldingAnalysisResult analysis,
            RecommendationResolution resolution) {}
}
