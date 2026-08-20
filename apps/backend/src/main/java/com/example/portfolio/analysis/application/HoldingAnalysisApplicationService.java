package com.example.portfolio.analysis.application;

import com.example.portfolio.analysis.allocation.PortfolioAllocationService;
import com.example.portfolio.analysis.decision.AssetDecisionRouter;
import com.example.portfolio.analysis.decision.BehavioralFirewall;
import com.example.portfolio.analysis.decision.DecisionContext;
import com.example.portfolio.analysis.decision.PortfolioConstraintEngine;
import com.example.portfolio.analysis.dip.EtfDipEventService;
import com.example.portfolio.analysis.domain.AnalysisReadiness;
import com.example.portfolio.analysis.domain.HoldingAnalysisResult;
import com.example.portfolio.analysis.domain.HoldingEvidence;
import com.example.portfolio.analysis.domain.RecommendationAction;
import com.example.portfolio.analysis.domain.RecommendationCandidate;
import com.example.portfolio.analysis.domain.RecommendationResolution;
import com.example.portfolio.analysis.domain.StrategyDefinition;
import com.example.portfolio.analysis.infrastructure.HoldingAnalysisStore;
import com.example.portfolio.analysis.narrative.NarrativeInput;
import com.example.portfolio.analysis.replay.DecisionAsOfContext;
import com.example.portfolio.strategy.market.EvidenceQuality;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
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
    private final PortfolioAllocationService allocations;
    private final EtfDipEventService dipEvents;
    private final BehavioralEvidenceService behavioralEvidence;
    private final BehavioralFirewall behavioralFirewall;
    private final Clock clock;
    private final JdbcClient jdbc;

    public HoldingAnalysisApplicationService(
            HoldingEvidenceAssembler evidenceAssembler,
            AnalysisFreshnessPolicy freshness,
            com.example.portfolio.analysis.decision.RecommendationConflictResolver conflictResolver,
            PortfolioConstraintEngine portfolioConstraints,
            AssetDecisionRouter assetDecisions,
            HoldingAnalysisStore store,
            PortfolioAllocationService allocations,
            EtfDipEventService dipEvents,
            BehavioralEvidenceService behavioralEvidence,
            BehavioralFirewall behavioralFirewall,
            Clock clock,
            JdbcClient jdbc) {
        this.evidenceAssembler = evidenceAssembler;
        this.freshness = freshness;
        this.conflictResolver = conflictResolver;
        this.portfolioConstraints = portfolioConstraints;
        this.assetDecisions = assetDecisions;
        this.store = store;
        this.allocations = allocations;
        this.dipEvents = dipEvents;
        this.behavioralEvidence = behavioralEvidence;
        this.behavioralFirewall = behavioralFirewall;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    public List<AnalyzedHolding> analyzeAll(UUID userId) {
        return analyzeAll(userId, null);
    }

    public List<AnalyzedHolding> analyzeAll(UUID userId, UUID analysisRunId) {
        var context = analysisRunId == null ? liveContext() : replayContext(userId, analysisRunId);
        var assembled = evidenceAssembler.assembleAll(userId, context);
        return assembled.stream()
                .map(evidence -> analyzeAndPersist(evidence, analysisRunId, context))
                .toList();
    }

    private DecisionAsOfContext liveContext() {
        var strategy = evidenceAssembler.currentStrategy();
        return new DecisionAsOfContext(LocalDate.now(clock), clock.instant(), strategy.version());
    }

    private DecisionAsOfContext replayContext(UUID userId, UUID analysisRunId) {
        return jdbc.sql(
                        """
                        SELECT market_date marketDate,strategy_version strategyVersion
                        FROM portfolio_analysis_run WHERE id=UUID_TO_BIN(:runId) AND user_id=UUID_TO_BIN(:userId)
                        """)
                .param("runId", analysisRunId.toString())
                .param("userId", userId.toString())
                .query(RunContext.class)
                .optional()
                .map(value -> DecisionAsOfContext.marketClose(value.marketDate(), value.strategyVersion()))
                .orElseThrow(() -> new IllegalArgumentException("Analysis run is not owned by user"));
    }

    public AnalyzedHolding analyze(UUID userId, UUID positionId) {
        return analyzeAndPersist(evidenceAssembler.assemble(userId, positionId), null, liveContext());
    }

    private AnalyzedHolding analyzeAndPersist(
            HoldingEvidence evidence, UUID analysisRunId, DecisionAsOfContext decisionContext) {
        var now = clock.instant();
        var decisionAt = decisionContext.dataCutoff();
        var state = HoldingEvidenceReadiness.assess(evidence, decisionAt, freshness);
        var policy = policy(evidence.position().classification(), evidence.strategy());
        var candidates = candidates(evidence, state, policy, decisionContext);
        var resolution =
                conflictResolver.resolve(candidates, evidence.strategy().riskPriorityOverTax());
        var sizing = size(evidence, state, policy, resolution.winner(), decisionContext);
        var confidence = confidence(evidence, state);
        var riskProjection = riskProjection(evidence, resolution.winner().action(), sizing);
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
                decisionAt.plus(VALIDITY),
                AnalysisChecksum.sha256(evidence + ":" + state + ":" + resolution));
        var narrativeInput = narrativeInput(evidence, result, resolution);
        var snapshotId = store.append(analysisRunId, result, resolution, narrativeInput, riskProjection, sizing, now);
        return new AnalyzedHolding(snapshotId, evidence, result, resolution, narrativeInput, riskProjection);
    }

    private static RiskProjection riskProjection(
            HoldingEvidence evidence, RecommendationAction action, PositionSizing.Result sizing) {
        if (evidence.riskQuality() != EvidenceQuality.HEALTHY || evidence.totalOpenRisk() == null) {
            return new RiskProjection(null, null, "PORTFOLIO_RISK_EVIDENCE_UNAVAILABLE");
        }
        var before = evidence.totalOpenRisk().max(BigDecimal.ZERO);
        if (!com.example.portfolio.analysis.decision.RecommendationSizingService.requiresBuySizing(action)
                && !com.example.portfolio.analysis.decision.RecommendationSizingService.requiresSellSizing(action)) {
            return new RiskProjection(before, before, "NO_POSITION_SIZE_CHANGE");
        }
        if (!sizing.exactQuantityAllowed()
                || sizing.quantityMax() == null
                || evidence.quote().last() == null
                || evidence.portfolioEquity().amount().signum() <= 0) {
            return new RiskProjection(before, null, "PROJECTED_RISK_INPUT_MISSING");
        }
        var riskPerShare = evidence.stop().formalStop() == null
                ? themeRiskProxyPerShare(evidence)
                : evidence.quote().last().subtract(evidence.stop().formalStop()).abs();
        if (riskPerShare == null) return new RiskProjection(before, null, "PROJECTED_RISK_INPUT_MISSING");
        var delta = riskPerShare
                .multiply(sizing.quantityMax())
                .divide(evidence.portfolioEquity().amount(), 10, RoundingMode.HALF_UP);
        var after = com.example.portfolio.analysis.decision.RecommendationSizingService.requiresBuySizing(action)
                ? before.add(delta)
                : before.subtract(delta).max(BigDecimal.ZERO);
        return new RiskProjection(
                before,
                after,
                evidence.stop().formalStop() == null
                        ? "PROJECTED_FROM_THEME_RISK_PROXY_AND_MAX_QUANTITY"
                        : "PROJECTED_FROM_FORMAL_STOP_AND_MAX_QUANTITY");
    }

    private static NarrativeInput narrativeInput(
            HoldingEvidence evidence, HoldingAnalysisResult analysis, RecommendationResolution resolution) {
        return new NarrativeInput(
                evidence.instrument().symbol(),
                evidence.position().classification().name(),
                resolution.winner().action().name(),
                evidence.fundamentals().financialHealth(),
                evidence.valuation().state(),
                evidence.fundamentals().estimateRevision(),
                evidence.indicators().priceState(),
                resolution.winner().riskRank() <= 7 ? "BLOCKED" : "AVAILABLE",
                resolution.winner().ruleId(),
                analysis.reasons(),
                analysis.risks(),
                analysis.changeConditions(),
                analysis.confidence(),
                false);
    }

    private List<RecommendationCandidate> candidates(
            HoldingEvidence evidence, AnalysisReadiness state, Policy policy, DecisionAsOfContext decisionContext) {
        var decisionAt = decisionContext.dataCutoff();
        var behavior = behavioralEvidence.load(evidence, decisionAt);
        var context = new DecisionContext(
                evidence,
                state,
                policy.targetMin(),
                policy.targetMax(),
                policy.normalMax(),
                policy.hardMax(),
                allocations.forPosition(
                        evidence.position().userId(),
                        evidence.position().classification(),
                        evidence.instrument().symbol()),
                dipEvents
                        .latest(
                                evidence.position().userId(),
                                evidence.instrument().id(),
                                decisionContext)
                        .orElse(null),
                behavior.lastDecisionAt(),
                behavior.lastAddAt(),
                behavior.averagingDown(),
                behavior.thesisImproving(),
                behavior.anchoredToCostBasis(),
                behavior.holdingTradingDays(),
                behavior.thesisProgress(),
                behavior.ideaCooldownUntil(),
                decisionAt);
        var values = new ArrayList<>(portfolioConstraints.evaluate(context));
        var assetCandidates = assetDecisions.evaluate(context);
        values.addAll(behavioralFirewall.evaluate(context, assetCandidates));
        values.addAll(assetCandidates);
        return List.copyOf(values);
    }

    private PositionSizing.Result size(
            HoldingEvidence evidence,
            AnalysisReadiness state,
            Policy policy,
            RecommendationCandidate winner,
            DecisionAsOfContext decisionContext) {
        var action = winner.action();
        if (action == RecommendationAction.DEPLOY_DIP_TRANCHE) return dipSize(evidence, state, decisionContext);
        var decisionAt = decisionContext.dataCutoff();
        if ((!com.example.portfolio.analysis.decision.RecommendationSizingService.requiresBuySizing(action)
                        && !com.example.portfolio.analysis.decision.RecommendationSizingService.requiresSellSizing(
                                action))
                || policy.targetMax() == null) {
            return unavailableSizing();
        }
        var investableAssets = evidence.portfolioEquity().amount();
        var deployableCash = evidence.trackedCash()
                .amount()
                .subtract(evidence.emergencyCash().amount())
                .max(BigDecimal.ZERO);
        var weightCap = policy.hardMax() == null ? policy.targetMax() : policy.hardMax();
        var trimTarget = "POSITION.HARD_CAP".equals(winner.ruleId()) ? weightCap : policy.targetMax();
        return PositionSizing.calculate(
                new PositionSizing.Input(
                        action,
                        investableAssets,
                        policy.tradeRisk(),
                        evidence.quote().last(),
                        evidence.stop().formalStop(),
                        evidence.quote().last(),
                        evidence.position().quantity(),
                        evidence.position().marketValue(),
                        policy.targetMin(),
                        weightCap,
                        trimTarget,
                        deployableCash,
                        investableAssets.multiply(evidence.clusterOpenRisk()),
                        evidence.strategy().clusterOpenRiskMax(),
                        evidence.strategy().qualityStarterFraction(),
                        stopRequired(evidence.position().classification()),
                        evidence.quote().quality(),
                        evidence.capitalQuality(),
                        evidence.riskQuality(),
                        state != AnalysisReadiness.STALE
                                && !freshness.stalePrice(
                                        evidence.quote().marketDate(),
                                        decisionAt,
                                        evidence.strategy().freshness()),
                        !freshness.staleDays(
                                evidence.riskDataAsOf(),
                                decisionAt,
                                evidence.strategy().freshness().macroDailyDays()),
                        evidence.position().classificationConfirmed(),
                        evidence.providerHardError(),
                        investableAssets.multiply(evidence.totalOpenRisk()),
                        evidence.strategy().totalOpenRiskMax(),
                        liquidityMaxShares(evidence),
                        themeRiskProxyPerShare(evidence)),
                evidence.strategy().exactQuantityRequiresHealthyPrice(),
                evidence.strategy().exactQuantityRequiresReadyRisk());
    }

    private PositionSizing.Result dipSize(
            HoldingEvidence evidence, AnalysisReadiness state, DecisionAsOfContext decisionContext) {
        var event = dipEvents
                .latest(evidence.position().userId(), evidence.instrument().id(), decisionContext)
                .orElse(null);
        var sleeve = allocations.forPosition(
                evidence.position().userId(),
                evidence.position().classification(),
                evidence.instrument().symbol());
        if (event == null
                || !event.readyForNextTranche()
                || sleeve == null
                || sleeve.gapWeight() == null
                || sleeve.gapWeight().signum() <= 0
                || evidence.quote().last() == null
                || evidence.quote().last().signum() <= 0
                || evidence.quote().quality() != EvidenceQuality.HEALTHY
                || evidence.capitalQuality() != EvidenceQuality.HEALTHY
                || evidence.riskQuality() != EvidenceQuality.HEALTHY
                || state == AnalysisReadiness.STALE
                || freshness.stalePrice(
                        evidence.quote().marketDate(),
                        decisionContext.dataCutoff(),
                        evidence.strategy().freshness())
                || freshness.staleDays(
                        evidence.riskDataAsOf(),
                        decisionContext.dataCutoff(),
                        evidence.strategy().freshness().macroDailyDays())
                || !evidence.position().classificationConfirmed()
                || evidence.providerHardError()) return unavailableSizing();
        var investable = evidence.portfolioEquity().amount();
        var deployableCash = evidence.trackedCash()
                .amount()
                .subtract(evidence.emergencyCash().amount())
                .max(BigDecimal.ZERO);
        var trancheAmount = evidence.tacticalReserve().amount().multiply(event.tranchePct());
        var capacityAmount = investable.multiply(sleeve.gapWeight());
        var amount = trancheAmount.min(deployableCash).min(capacityAmount).max(BigDecimal.ZERO);
        var quantity = amount.divide(evidence.quote().last(), 0, RoundingMode.FLOOR);
        return new PositionSizing.Result(true, quantity, quantity, null, quantity, quantity, null);
    }

    private static PositionSizing.Result unavailableSizing() {
        return new PositionSizing.Result(false, null, null, null, null, null, null);
    }

    private static boolean stopRequired(HoldingClassification classification) {
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

    private static BigDecimal liquidityMaxShares(HoldingEvidence evidence) {
        var volumes = evidence.completedBars().stream()
                .map(HoldingEvidence.PriceBar::volume)
                .filter(java.util.Objects::nonNull)
                .filter(value -> value.signum() > 0)
                .limit(20)
                .sorted()
                .toList();
        if (volumes.isEmpty()) return null;
        var median = volumes.get(volumes.size() / 2);
        return median.multiply(evidence.strategy().executionRisk().liquidityParticipationMax());
    }

    private static BigDecimal themeRiskProxyPerShare(HoldingEvidence evidence) {
        if (evidence.position().classification() != HoldingClassification.THEMATIC_ETF) return null;
        if (evidence.indicators().atr() != null && evidence.indicators().atr() > 0) {
            return BigDecimal.valueOf(evidence.indicators().atr())
                    .multiply(evidence.strategy().executionRisk().thematicAtrRiskMultiple());
        }
        return evidence.quote().last() == null
                ? null
                : evidence.quote()
                        .last()
                        .multiply(evidence.strategy().executionRisk().thematicFallbackRiskFraction());
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
        references.add("capital-quality:" + evidence.capitalQuality());
        references.add("risk:" + evidence.riskDataAsOf() + ":" + evidence.riskQuality());
        references.add("provider-hard-error:" + evidence.providerHardError());
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
            RecommendationResolution resolution,
            NarrativeInput narrativeInput,
            RiskProjection riskProjection) {}

    public record RiskProjection(BigDecimal beforeFraction, BigDecimal afterFraction, String reason) {}

    record RunContext(LocalDate marketDate, String strategyVersion) {}
}
