package com.example.portfolio.analysis;

import com.example.portfolio.analysis.application.HoldingEvidenceAssembler;
import com.example.portfolio.analysis.infrastructure.HoldingAnalysisStore;
import com.example.portfolio.analysis.infrastructure.PositionAnalystDataStore;
import com.example.portfolio.estimates.EstimateConsensusMath;
import com.example.portfolio.estimates.EstimateRevisionEngine;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1")
public final class PositionReportController {
    private final HoldingAnalysisStore store;
    private final HoldingEvidenceAssembler evidenceAssembler;
    private final PositionAnalystDataStore analystData;
    private final Clock clock;
    private final ObjectMapper json;

    public PositionReportController(
            HoldingAnalysisStore store,
            HoldingEvidenceAssembler evidenceAssembler,
            PositionAnalystDataStore analystData,
            Clock clock,
            ObjectMapper json) {
        this.store = store;
        this.evidenceAssembler = evidenceAssembler;
        this.analystData = analystData;
        this.clock = clock;
        this.json = json;
    }

    @GetMapping({"/positions/{positionId}/report", "/holdings/{positionId}/analyst-report"})
    PositionReportResponse report(@PathVariable UUID positionId, Principal principal) {
        var userId = store.userId(principal.getName());
        var value = store.latestReport(userId, positionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (value.readiness() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Holding analysis has not been generated");
        }
        var evidence = evidenceAssembler.assemble(userId, positionId);
        var analytics = analystData.load(evidence);
        return new PositionReportResponse(
                new Position(value.positionId(), value.symbol(), value.classification(), value.classificationSource()),
                value.readiness(),
                new Recommendation(
                        value.recommendationId(),
                        value.recommendationAction() == null ? value.recommendedAction() : value.recommendationAction(),
                        value.recommendationPriority(),
                        decimal(value.recommendedQuantityMin()),
                        decimal(value.recommendedQuantityMax()),
                        decimal(value.currentWeight()),
                        decimal(value.targetWeightMin()),
                        decimal(value.targetWeightMax()),
                        value.confidence(),
                        strings(value.reasons()),
                        strings(value.risks()),
                        strings(value.changeConditions()),
                        value.winningRule(),
                        suppressed(value.suppressedCandidates()),
                        value.resolutionReason(),
                        narrative(value),
                        instant(value.validUntil())),
                new AuditEvidence(
                        value.analysisStatus(),
                        value.exactQuantityAllowed(),
                        strings(value.ruleIds()),
                        strings(value.evidenceRefs()),
                        value.strategyVersion(),
                        value.configHash()),
                assetEvidence(evidence),
                analystLayers(value, evidence, analytics),
                instant(value.dataAsOf()));
    }

    private AnalystLayers analystLayers(
            HoldingAnalysisStore.PositionReportRow value,
            com.example.portfolio.analysis.domain.HoldingEvidence evidence,
            PositionAnalystDataStore.AnalystData analytics) {
        var action = value.recommendationAction() == null ? value.recommendedAction() : value.recommendationAction();
        var policy = positionPolicy(evidence);
        var atCapacity = value.currentWeight() != null
                && policy.hardMax() != null
                && value.currentWeight().compareTo(policy.hardMax()) >= 0;
        return new AnalystLayers(
                new SystemRecommendation(
                        action,
                        value.recommendationPriority(),
                        value.confidence(),
                        decimal(value.recommendedQuantityMin()),
                        decimal(value.recommendedQuantityMax()),
                        decimal(value.quantityBeforeLimitingConstraint()),
                        value.exactQuantityAllowed()),
                new PortfolioRole(
                        value.classification(),
                        decimal(value.currentWeight()),
                        decimal(value.targetWeightMin()),
                        decimal(value.targetWeightMax()),
                        decimal(policy.normalMax()),
                        decimal(policy.hardMax()),
                        atCapacity,
                        atCapacity
                                ? "The holding is at or above its hard portfolio limit; attractive valuation cannot authorize more buying."
                                : "Portfolio capacity remains subject to total-risk, cluster-risk, cash, and liquidity limits."),
                market(analytics.market()),
                new Fundamentals(
                        evidence.fundamentals().financialHealth(),
                        decimal(analytics.fundamentals().revenueTtm()),
                        decimal(analytics.fundamentals().revenueYoy()),
                        decimal(analytics.fundamentals().revenue3yCagr()),
                        decimal(analytics.fundamentals().epsTtm()),
                        decimal(analytics.fundamentals().epsYoy()),
                        decimal(analytics.fundamentals().operatingMargin()),
                        decimal(analytics.fundamentals().operatingMarginYoyChange()),
                        decimal(analytics.fundamentals().fcfTtm()),
                        decimal(analytics.fundamentals().fcfMargin()),
                        decimal(analytics.fundamentals().fcfConversion()),
                        decimal(analytics.fundamentals().netCash()),
                        decimal(analytics.fundamentals().netDebtToFcf()),
                        decimal(analytics.fundamentals().currentRatio()),
                        decimal(analytics.fundamentals().shareDilutionYoy()),
                        instant(analytics.fundamentals().dataAsOf()),
                        analytics.fundamentals().quality() == null
                                ? evidence.fundamentals().quality().name()
                                : analytics.fundamentals().quality(),
                        evidence.fundamentals().available()),
                new Valuation(
                        evidence.valuation().state(),
                        decimal(analytics.valuation().trailingPeTtm()),
                        decimal(analytics.valuation().forwardPeFy1()),
                        decimal(analytics.valuation().evSalesTtm()),
                        decimal(analytics.valuation().priceSalesTtm()),
                        decimal(analytics.valuation().fcfYieldTtm()),
                        decimal(analytics.valuation().historyPercentile3y()),
                        decimal(analytics.valuation().historyPercentile5y()),
                        analytics.valuation().observationCount(),
                        analytics.valuation().confidence() == null
                                ? evidence.valuation().confidence()
                                : analytics.valuation().confidence(),
                        decimal(analytics.valuation().relativeValuation()),
                        analytics.valuation().quality(),
                        instant(analytics.valuation().dataAsOf()),
                        evidence.valuation().independentConfirmation(),
                        isAttractive(evidence.valuation().state()),
                        isAttractive(evidence.valuation().state()) && atCapacity),
                estimates(analytics.estimates()),
                technical(analytics.technical()),
                earnings(analytics.earnings()),
                risk(value, evidence, policy, analytics.risk()),
                new PriceRiskEarnings(
                        evidence.indicators().priceState(),
                        decimal(evidence.stop().formalStop()),
                        decimal(evidence.stop().liveStop()),
                        evidence.nextEvent().eventRisk(),
                        evidence.nextEvent().policyAction(),
                        evidence.nextEvent().eventAt()),
                new RationaleAndEvidence(
                        strings(value.reasons()),
                        strings(value.risks()),
                        strings(value.changeConditions()),
                        new EvidenceDrawer(
                                strings(value.ruleIds()),
                                strings(value.evidenceRefs()),
                                value.strategyVersion(),
                                value.configHash(),
                                evidence.quality().name(),
                                instant(value.dataAsOf()))));
    }

    private static com.example.portfolio.analysis.domain.StrategyDefinition.PositionPolicy positionPolicy(
            com.example.portfolio.analysis.domain.HoldingEvidence evidence) {
        return switch (evidence.position().classification()) {
            case QUALITY_STOCK, QUALITY_GROWTH_HIGH_VOL -> evidence.strategy().quality();
            case THEMATIC_ETF -> evidence.strategy().thematicEtf();
            case TACTICAL_STOCK, CYCLICAL_TACTICAL, TURNAROUND_TACTICAL ->
                evidence.strategy().tactical();
            case SPECULATIVE -> evidence.strategy().speculative();
            default ->
                new com.example.portfolio.analysis.domain.StrategyDefinition.PositionPolicy(
                        BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO);
        };
    }

    private static boolean isAttractive(String valuationState) {
        return valuationState != null
                && (valuationState.contains("ATTRACTIVE")
                        || valuationState.contains("DISCOUNT")
                        || valuationState.contains("CHEAP"));
    }

    private static Market market(PositionAnalystDataStore.MarketData value) {
        return new Market(
                decimal(value.price()),
                decimal(value.dayChangePct()),
                decimal(value.oneMonthReturn()),
                decimal(value.threeMonthReturn()),
                decimal(value.averageCost()),
                decimal(value.unrealizedPnlDollar()),
                decimal(value.unrealizedPnlPct()));
    }

    private static Estimates estimates(PositionAnalystDataStore.EstimateData value) {
        return new Estimates(
                decimal(value.fy1Eps()),
                decimal(EstimateConsensusMath.priorConsensus(value.fy1Eps(), value.epsRevision30d())),
                decimal(EstimateConsensusMath.priorConsensus(value.fy1Eps(), value.epsRevision90d())),
                decimal(value.fy1Revenue()),
                decimal(value.epsRevision30d()),
                decimal(value.epsRevision90d()),
                decimal(value.revenueRevision30d()),
                decimal(value.revenueRevision90d()),
                value.analystCount(),
                decimal(value.epsHigh()),
                decimal(value.epsLow()),
                decimal(value.dispersion()),
                EstimateRevisionEngine.isHighDispersion(value.dispersion()),
                value.state(),
                value.quality(),
                instant(value.dataAsOf()));
    }

    private static Technical technical(PositionAnalystDataStore.TechnicalData value) {
        return new Technical(
                decimal(value.sma20()),
                decimal(value.sma50()),
                decimal(value.sma200()),
                decimal(value.distanceFromSma20()),
                decimal(value.distanceFromSma50()),
                decimal(value.distanceFromSma200()),
                decimal(value.rsi14()),
                value.macdState(),
                decimal(value.atr14()),
                decimal(value.atrPercent()),
                decimal(value.realizedVolatility()),
                value.breakout20d(),
                decimal(value.drawdown52Week()),
                decimal(value.relativeStrengthSpy1m()),
                decimal(value.relativeStrengthSpy3m()),
                decimal(value.relativeStrengthSpy6m()),
                decimal(value.relativeStrengthQqq1m()),
                decimal(value.relativeStrengthQqq3m()),
                decimal(value.relativeStrengthQqq6m()),
                instant(value.dataAsOf()));
    }

    private Earnings earnings(PositionAnalystDataStore.EarningsData value) {
        var next = instant(value.nextEarningsAt());
        return new Earnings(
                next,
                next == null ? null : Math.max(0, ChronoUnit.DAYS.between(clock.instant(), next)),
                value.sessionType(),
                value.eventRisk(),
                decimal(value.historicalMedianAbsMove()),
                decimal(value.historicalP75AbsMove()),
                decimal(value.worstDownsideGap()),
                decimals(value.reaction1d()),
                decimals(value.reaction3d()),
                decimals(value.reaction5d()),
                decimal(value.currentR()),
                value.policyAction(),
                value.quality(),
                instant(value.dataAsOf()));
    }

    private static Risk risk(
            HoldingAnalysisStore.PositionReportRow report,
            com.example.portfolio.analysis.domain.HoldingEvidence evidence,
            com.example.portfolio.analysis.domain.StrategyDefinition.PositionPolicy policy,
            PositionAnalystDataStore.RiskData value) {
        var price = evidence.quote().last();
        var stop = evidence.stop().formalStop();
        var stopDistance = price == null || price.signum() == 0 || stop == null
                ? null
                : price.subtract(stop).divide(price, java.math.MathContext.DECIMAL64);
        return new Risk(
                decimal(evidence.currentWeight()),
                decimal(policy.normalMax()),
                decimal(policy.hardMax()),
                decimal(stop),
                decimal(stopDistance),
                decimal(value.plannedRiskDollar()),
                decimal(value.plannedRiskFraction()),
                decimal(evidence.clusterOpenRisk()),
                decimal(evidence.totalOpenRisk()),
                decimal(evidence.strategy().totalOpenRiskMax()),
                decimal(report.projectedPositionWeight()),
                decimal(report.projectedTotalRisk()),
                decimal(report.projectedClusterRisk()),
                report.sizingLimitingConstraint(),
                decimal(report.sizingRiskPerShare()),
                value.quality(),
                instant(value.dataAsOf()));
    }

    private static List<String> decimals(List<BigDecimal> values) {
        return values.stream().map(PositionReportController::decimal).toList();
    }

    private static AssetEvidence assetEvidence(com.example.portfolio.analysis.domain.HoldingEvidence evidence) {
        var classification = evidence.position().classification();
        var company =
                switch (classification) {
                    case QUALITY_STOCK,
                            QUALITY_GROWTH_HIGH_VOL,
                            TACTICAL_STOCK,
                            CYCLICAL_TACTICAL,
                            TURNAROUND_TACTICAL ->
                        new CompanyEvidence(
                                true,
                                status(evidence.fundamentals().available()),
                                status(evidence.fundamentals().available()),
                                status(evidence.valuation().available()),
                                status(evidence.nextEvent().available()),
                                status(evidence.thesis().available()));
                    default -> null;
                };
        var etf =
                switch (classification) {
                    case CORE_BROAD_ETF, CORE_TECH_ETF, THEMATIC_ETF ->
                        new EtfEvidence(
                                true,
                                evidence.profile().thematic(),
                                decimal(evidence.profile().topHoldingConcentration()),
                                decimal(evidence.profile().portfolioOverlap()),
                                status(evidence.indicators().trendAvailable()),
                                evidence.profile().liquidityStatus(),
                                status(evidence.nextEvent().available()),
                                false);
                    default -> null;
                };
        var speculative = classification == HoldingClassification.SPECULATIVE
                ? new SpeculativeEvidence(
                        true,
                        decimal(evidence.strategy().speculative().hardMax()),
                        "LOW",
                        status(evidence.stop().formalStop() != null),
                        status(evidence.nextEvent().available()),
                        false)
                : null;
        return new AssetEvidence(
                company,
                etf,
                speculative,
                new PortfolioContext(
                        decimal(evidence.currentWeight()),
                        decimal(evidence.clusterWeight()),
                        decimal(evidence.clusterOpenRisk())));
    }

    private static String status(boolean available) {
        return available ? "AVAILABLE" : "MISSING";
    }

    private List<String> strings(String value) {
        try {
            return json.readValue(value == null ? "[]" : value, new TypeReference<>() {});
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored analysis JSON is invalid", exception);
        }
    }

    private List<SuppressedCandidate> suppressed(String value) {
        try {
            return json.readValue(value == null ? "[]" : value, new TypeReference<>() {});
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored recommendation resolution JSON is invalid", exception);
        }
    }

    private DecisionNarrative narrative(HoldingAnalysisStore.PositionReportRow value) {
        if (value.narrativeHeadline() == null) return null;
        return new DecisionNarrative(
                value.narrativeSource(),
                value.narrativeHeadline(),
                value.narrativeOneSentence(),
                strings(value.narrativeWhy()),
                strings(value.narrativeRisks()),
                strings(value.narrativeWatchNext()),
                value.narrativeConfidenceExplanation());
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    public record Position(UUID id, String symbol, String classification, String classificationSource) {}

    public record Recommendation(
            UUID id,
            String action,
            String priority,
            String quantityMin,
            String quantityMax,
            String currentWeight,
            String targetWeightMin,
            String targetWeightMax,
            String confidence,
            List<String> reasons,
            List<String> risks,
            List<String> changeConditions,
            String winningRule,
            List<SuppressedCandidate> suppressedCandidates,
            String resolutionReason,
            DecisionNarrative narrative,
            Instant validUntil) {}

    public record DecisionNarrative(
            String source,
            String headline,
            String oneSentence,
            List<String> why,
            List<String> risks,
            List<String> watchNext,
            String confidenceExplanation) {}

    public record AuditEvidence(
            String analysisStatus,
            boolean exactQuantityAllowed,
            List<String> ruleIds,
            List<String> evidenceRefs,
            String strategyVersion,
            String configHash) {}

    public record AssetEvidence(
            CompanyEvidence company,
            EtfEvidence etf,
            SpeculativeEvidence speculative,
            PortfolioContext portfolioContext) {}

    public record CompanyEvidence(
            boolean companyModelApplied,
            String fundamentalsStatus,
            String growthProfitabilityCashFlowStatus,
            String valuationStatus,
            String earningsRiskStatus,
            String thesisStatus) {}

    public record EtfEvidence(
            boolean etfModelApplied,
            boolean thematic,
            String topHoldingsConcentration,
            String portfolioOverlapFraction,
            String trendStatus,
            String liquidityStatus,
            String eventStatus,
            boolean companyEarningsModelApplied) {}

    public record SpeculativeEvidence(
            boolean speculativePolicyApplied,
            String hardMaxWeight,
            String confidenceCeiling,
            String stopStatus,
            String eventRiskStatus,
            boolean tickerOrPriceCanUpgradeQuality) {}

    public record PortfolioContext(String currentWeight, String clusterWeight, String clusterOpenRisk) {}

    public record SuppressedCandidate(
            String action, String priority, int riskRank, String ruleId, String reason, List<String> risks) {}

    public record AnalystLayers(
            SystemRecommendation systemRecommendation,
            PortfolioRole portfolioRole,
            Market market,
            Fundamentals fundamentals,
            Valuation valuation,
            Estimates estimates,
            Technical technical,
            Earnings earnings,
            Risk risk,
            PriceRiskEarnings priceRiskEarnings,
            RationaleAndEvidence rationaleAndEvidence) {}

    public record SystemRecommendation(
            String action,
            String priority,
            String confidence,
            String quantityMin,
            String quantityMax,
            String quantityBeforeLimitingConstraint,
            boolean exactQuantityAllowed) {}

    public record PortfolioRole(
            String classification,
            String currentWeight,
            String targetWeightMin,
            String targetWeightMax,
            String normalMaxWeight,
            String hardMaxWeight,
            boolean atHardMax,
            String capacityExplanation) {}

    @Schema(name = "PositionMarket")
    public record Market(
            String price,
            String dayChangePct,
            String oneMonthReturn,
            String threeMonthReturn,
            String averageCost,
            String unrealizedPnlDollar,
            String unrealizedPnlPct) {}

    public record Fundamentals(
            String financialHealth,
            String revenueTtm,
            String revenueYoy,
            String revenue3yCagr,
            String epsTtm,
            String epsYoy,
            String operatingMargin,
            String operatingMarginYoyChange,
            String fcfTtm,
            String fcfMargin,
            String fcfConversion,
            String netCash,
            String netDebtToFcf,
            String currentRatio,
            String shareDilutionYoy,
            Instant dataAsOf,
            String quality,
            boolean available) {}

    public record Valuation(
            String state,
            String trailingPeTtm,
            String forwardPeFy1,
            String evSalesTtm,
            String priceSalesTtm,
            String fcfYieldTtm,
            String historyPercentile3y,
            String historyPercentile5y,
            int observationCount,
            String confidence,
            String relativeValuation,
            String quality,
            Instant dataAsOf,
            boolean independentConfirmation,
            boolean attractive,
            boolean attractiveButCannotAdd) {}

    public record Estimates(
            String fy1Eps,
            String eps30dAgo,
            String eps90dAgo,
            String fy1Revenue,
            String epsRevision30d,
            String epsRevision90d,
            String revenueRevision30d,
            String revenueRevision90d,
            Integer analystCount,
            String epsHigh,
            String epsLow,
            String dispersion,
            boolean dispersionHigh,
            String state,
            String quality,
            Instant dataAsOf) {}

    public record Technical(
            String sma20,
            String sma50,
            String sma200,
            String distanceFromSma20,
            String distanceFromSma50,
            String distanceFromSma200,
            String rsi14,
            String macdState,
            String atr14,
            String atrPercent,
            String realizedVolatility,
            String breakout20d,
            String drawdown52Week,
            String relativeStrengthSpy1m,
            String relativeStrengthSpy3m,
            String relativeStrengthSpy6m,
            String relativeStrengthQqq1m,
            String relativeStrengthQqq3m,
            String relativeStrengthQqq6m,
            Instant dataAsOf) {}

    public record Earnings(
            Instant nextEarningsAt,
            Long daysUntilEarnings,
            String sessionType,
            String eventRisk,
            String historicalMedianAbsMove,
            String historicalP75AbsMove,
            String worstDownsideGap,
            List<String> reaction1d,
            List<String> reaction3d,
            List<String> reaction5d,
            String currentR,
            String policyAction,
            String quality,
            Instant dataAsOf) {}

    @Schema(name = "PositionRisk")
    public record Risk(
            String currentWeight,
            String normalMaxWeight,
            String hardMaxWeight,
            String plannedStop,
            String stopDistancePct,
            String positionPlannedRiskDollar,
            String positionPlannedRiskPct,
            String clusterRisk,
            String totalPortfolioPlannedRisk,
            String totalPortfolioRiskCap,
            String projectedPositionWeight,
            String projectedTotalRiskAfterAction,
            String projectedClusterRiskAfterAction,
            String sizingLimitingConstraint,
            String riskPerShare,
            String quality,
            Instant dataAsOf) {}

    public record PriceRiskEarnings(
            String priceState,
            String formalStop,
            String liveStop,
            String earningsRisk,
            String earningsPolicyAction,
            Instant earningsAt) {}

    public record RationaleAndEvidence(
            List<String> reasons, List<String> risks, List<String> changeConditions, EvidenceDrawer evidenceDrawer) {}

    public record EvidenceDrawer(
            List<String> ruleIds,
            List<String> evidenceRefs,
            String strategyVersion,
            String configHash,
            String dataQuality,
            Instant dataAsOf) {}

    public record PositionReportResponse(
            Position position,
            String readiness,
            Recommendation recommendation,
            AuditEvidence evidence,
            AssetEvidence assetEvidence,
            AnalystLayers layers,
            Instant dataAsOf) {}
}
