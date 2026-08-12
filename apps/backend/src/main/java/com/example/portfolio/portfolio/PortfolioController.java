package com.example.portfolio.portfolio;

import com.example.portfolio.strategy.market.EvidenceQuality;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import com.example.portfolio.strategy.portfolio.HoldingClassifier;
import com.example.portfolio.strategy.portfolio.HoldingPolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
public class PortfolioController {
    private final PortfolioStore store;
    private final Clock clock;
    private final Optional<DebugApiAccess> debugAccess;

    public PortfolioController(PortfolioStore store, Clock clock, Optional<DebugApiAccess> debugAccess) {
        this.store = store;
        this.clock = clock;
        this.debugAccess = debugAccess;
    }

    @GetMapping("/accounts")
    List<PortfolioStore.AccountView> accounts(Principal principal) {
        return store.accounts(principal.getName());
    }

    @GetMapping("/portfolio/summary")
    PortfolioSummaryResponse summary(Principal principal) {
        var value = store.summary(principal.getName());
        return new PortfolioSummaryResponse(
                decimal(value.investedValue()),
                decimal(value.trackedCash()),
                value.openPositions(),
                instant(value.dataAsOf()));
    }

    @GetMapping("/positions")
    List<PositionResponse> positions(Principal principal) {
        return store.positions(principal.getName()).stream()
                .map(PositionResponse::from)
                .toList();
    }

    @GetMapping("/portfolio/holdings")
    List<PortfolioHoldingResponse> holdings(Principal principal) {
        return store.holdings(principal.getName()).stream()
                .map(PortfolioHoldingResponse::from)
                .toList();
    }

    @GetMapping("/positions/{id}")
    PositionResponse position(@PathVariable UUID id, Principal principal) {
        return store.position(principal.getName(), id)
                .map(PositionResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping("/positions/{id}/classify")
    PositionResponse classify(
            @PathVariable UUID id, @Valid @RequestBody ClassificationRequest request, Principal principal) {
        var classification = classification(request.classification());
        var evidence = store.classificationEvidence(principal.getName(), id);
        var suggestion = HoldingClassifier.suggest(
                evidence.symbol(), evidence.assetType(), evidence.thematic(), evidence.unvestedCompensation());
        var source = suggestion.classification() == classification ? "USER_CONFIRMED" : "USER_OVERRIDE";
        return PositionResponse.from(store.confirmClassification(
                principal.getName(), id, classification.name(), source, request.expectedVersion()));
    }

    @GetMapping("/positions/{id}/classification-suggestion")
    ClassificationSuggestionResponse classificationSuggestion(@PathVariable UUID id, Principal principal) {
        var evidence = store.classificationEvidence(principal.getName(), id);
        var suggestion = HoldingClassifier.suggest(
                evidence.symbol(), evidence.assetType(), evidence.thematic(), evidence.unvestedCompensation());
        return new ClassificationSuggestionResponse(
                id,
                evidence.symbol(),
                evidence.assetType(),
                suggestion.classification().name(),
                "SYSTEM_RULE",
                suggestion.blocked(),
                suggestion.reason(),
                true);
    }

    @GetMapping("/holdings/analysis")
    List<HoldingAnalysisResponse> analyses(Principal principal) {
        return store.analyses(principal.getName()).stream()
                .map(HoldingAnalysisResponse::from)
                .toList();
    }

    @GetMapping("/actions/today")
    TodayActionsResponse actions(Principal principal) {
        var recommendations = store.activeRecommendations(principal.getName()).stream()
                .map(RecommendationResponse::from)
                .toList();
        return new TodayActionsResponse(
                recommendations.stream()
                        .filter(item -> item.priority().equals("MUST_ACT"))
                        .limit(3)
                        .toList(),
                recommendations.stream()
                        .filter(item -> item.priority().equals("DO_NOT"))
                        .toList(),
                recommendations.stream()
                        .filter(item -> item.priority().equals("WATCH"))
                        .toList());
    }

    @PostMapping("/trade-plans/preview")
    TradePlanPreviewResponse preview(@Valid @RequestBody TradePlanPreviewRequest request) {
        requireDebugProfile();
        var analysis = HoldingPolicy.analyze(new HoldingPolicy.Input(
                classification(request.classification()),
                request.classificationConfirmed(),
                request.currentWeight(),
                request.projectedWeight(),
                request.proposedTradeRisk(),
                request.currentOpenStockRisk(),
                request.currentClusterRisk(),
                request.averagingDown(),
                request.thesisImproving(),
                request.anchoredToCostBasis(),
                request.lastDecisionAt(),
                clock.instant(),
                quality(request.quality())));
        return TradePlanPreviewResponse.from(analysis);
    }

    private void requireDebugProfile() {
        if (debugAccess.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    private static HoldingClassification classification(String value) {
        try {
            return HoldingClassification.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown holding classification", exception);
        }
    }

    private static EvidenceQuality quality(String value) {
        try {
            return EvidenceQuality.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown evidence quality", exception);
        }
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    public record ClassificationRequest(@NotBlank String classification, long expectedVersion) {}

    public record ClassificationSuggestionResponse(
            UUID positionId,
            String symbol,
            String assetType,
            String classification,
            String source,
            boolean blocked,
            String reason,
            boolean confirmationRequired) {}

    public record PortfolioSummaryResponse(
            String investedValue, String trackedCash, long openPositions, Instant dataAsOf) {}

    public record PositionResponse(
            UUID id,
            UUID accountId,
            String symbol,
            String bucket,
            String classification,
            boolean classificationConfirmed,
            String quantity,
            String averageCost,
            String marketValue,
            String status,
            long version) {
        static PositionResponse from(PortfolioStore.PositionView value) {
            return new PositionResponse(
                    value.id(),
                    value.accountId(),
                    value.symbol(),
                    value.bucket(),
                    value.classification(),
                    value.classificationConfirmed(),
                    decimal(value.quantity()),
                    decimal(value.averageCost()),
                    decimal(value.marketValue()),
                    value.status(),
                    value.version());
        }
    }

    public record PortfolioHoldingResponse(
            UUID id,
            long version,
            String symbol,
            String name,
            String assetType,
            String bucket,
            String classification,
            boolean classificationConfirmed,
            String marketValue,
            String currentWeight,
            String targetWeightMin,
            String targetWeightMax,
            String action,
            String priority,
            String confidence,
            String trend,
            Instant nextEvent,
            String dataStatus) {
        static PortfolioHoldingResponse from(PortfolioStore.PortfolioHoldingView value) {
            return new PortfolioHoldingResponse(
                    value.id(),
                    value.version(),
                    value.symbol(),
                    value.name(),
                    value.assetType(),
                    value.bucket(),
                    value.classification(),
                    value.classificationConfirmed(),
                    decimal(value.marketValue()),
                    decimal(value.currentWeight()),
                    decimal(value.targetWeightMin()),
                    decimal(value.targetWeightMax()),
                    value.action(),
                    value.priority(),
                    value.confidence(),
                    value.trend(),
                    instant(value.nextEvent()),
                    value.dataStatus());
        }
    }

    public record HoldingAnalysisResponse(
            UUID id,
            UUID positionId,
            String symbol,
            String analysisStatus,
            String confidence,
            String currentWeight,
            String targetWeightMin,
            String targetWeightMax,
            boolean exactQuantityAllowed,
            String reasonsJson,
            String risksJson,
            String changeConditionsJson,
            String ruleIdsJson,
            String strategyVersion,
            Instant dataAsOf,
            Instant validUntil) {
        static HoldingAnalysisResponse from(PortfolioStore.HoldingAnalysisView value) {
            return new HoldingAnalysisResponse(
                    value.id(),
                    value.positionId(),
                    value.symbol(),
                    value.analysisStatus(),
                    value.confidence(),
                    decimal(value.currentWeight()),
                    decimal(value.targetWeightMin()),
                    decimal(value.targetWeightMax()),
                    value.exactQuantityAllowed(),
                    value.reasons(),
                    value.risks(),
                    value.changeConditions(),
                    value.ruleIds(),
                    value.strategyVersion(),
                    instant(value.dataAsOf()),
                    instant(value.validUntil()));
        }
    }

    public record RecommendationResponse(
            UUID id,
            UUID positionId,
            String symbol,
            String classification,
            String action,
            String priority,
            String quantityMin,
            String quantityMax,
            String targetWeightMin,
            String targetWeightMax,
            String riskBeforeFraction,
            String riskAfterFraction,
            String riskCalculationReason,
            String currentWeight,
            String estimatedAmount,
            String confidence,
            String reasonsJson,
            String risksJson,
            String changeConditionsJson,
            String ruleIdsJson,
            String strategyVersion,
            Instant dataAsOf,
            Instant validUntil) {
        static RecommendationResponse from(PortfolioStore.RecommendationView value) {
            return new RecommendationResponse(
                    value.id(),
                    value.positionId(),
                    value.symbol(),
                    value.classification(),
                    value.action(),
                    value.priority(),
                    decimal(value.quantityMin()),
                    decimal(value.quantityMax()),
                    decimal(value.targetWeightMin()),
                    decimal(value.targetWeightMax()),
                    decimal(value.riskBeforeFraction()),
                    decimal(value.riskAfterFraction()),
                    value.riskCalculationReason(),
                    decimal(value.currentWeight()),
                    decimal(value.estimatedAmount()),
                    value.confidence(),
                    value.reasons(),
                    value.risks(),
                    value.changeConditions(),
                    value.ruleIds(),
                    value.strategyVersion(),
                    instant(value.dataAsOf()),
                    instant(value.validUntil()));
        }
    }

    public record TodayActionsResponse(
            List<RecommendationResponse> mustAct,
            List<RecommendationResponse> doNot,
            List<RecommendationResponse> watch) {}

    public record TradePlanPreviewRequest(
            @NotBlank String classification,
            boolean classificationConfirmed,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal currentWeight,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal projectedWeight,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal proposedTradeRisk,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal currentOpenStockRisk,
            @NotNull @DecimalMin("0") @DecimalMax("1") BigDecimal currentClusterRisk,
            boolean averagingDown,
            boolean thesisImproving,
            boolean anchoredToCostBasis,
            Instant lastDecisionAt,
            @NotBlank String quality) {}

    public record TradePlanPreviewResponse(
            boolean debug,
            boolean allowed,
            boolean preciseQuantityAllowed,
            String weightCap,
            String tradeRiskCap,
            String projectedOpenStockRisk,
            String projectedClusterRisk,
            String confidence,
            List<String> ruleIds,
            List<String> reasons,
            List<String> risks) {
        static TradePlanPreviewResponse from(HoldingPolicy.Analysis value) {
            return new TradePlanPreviewResponse(
                    true,
                    value.allowed(),
                    value.preciseQuantityAllowed(),
                    decimal(value.weightCap()),
                    decimal(value.tradeRiskCap()),
                    decimal(value.projectedOpenStockRisk()),
                    decimal(value.projectedClusterRisk()),
                    value.confidence().name(),
                    value.ruleIds(),
                    value.reasons(),
                    value.risks());
        }
    }
}
