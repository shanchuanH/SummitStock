package com.example.portfolio.analysis;

import com.example.portfolio.analysis.application.HoldingEvidenceAssembler;
import com.example.portfolio.analysis.infrastructure.HoldingAnalysisStore;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
@RequestMapping("/api/v1/positions/{positionId}")
public final class PositionReportController {
    private final HoldingAnalysisStore store;
    private final HoldingEvidenceAssembler evidenceAssembler;
    private final ObjectMapper json;

    public PositionReportController(
            HoldingAnalysisStore store, HoldingEvidenceAssembler evidenceAssembler, ObjectMapper json) {
        this.store = store;
        this.evidenceAssembler = evidenceAssembler;
        this.json = json;
    }

    @GetMapping("/report")
    PositionReportResponse report(@PathVariable UUID positionId, Principal principal) {
        var userId = store.userId(principal.getName());
        var value = store.latestReport(userId, positionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (value.readiness() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Holding analysis has not been generated");
        }
        var evidence = evidenceAssembler.assemble(userId, positionId);
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
                        instant(value.validUntil())),
                new AuditEvidence(
                        value.analysisStatus(),
                        value.exactQuantityAllowed(),
                        strings(value.ruleIds()),
                        strings(value.evidenceRefs()),
                        value.strategyVersion(),
                        value.configHash()),
                assetEvidence(evidence),
                instant(value.dataAsOf()));
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
            Instant validUntil) {}

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

    public record PositionReportResponse(
            Position position,
            String readiness,
            Recommendation recommendation,
            AuditEvidence evidence,
            AssetEvidence assetEvidence,
            Instant dataAsOf) {}
}
