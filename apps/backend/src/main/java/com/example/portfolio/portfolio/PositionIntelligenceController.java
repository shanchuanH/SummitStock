package com.example.portfolio.portfolio;

import com.example.portfolio.strategy.portfolio.HoldingClassification;
import com.example.portfolio.strategy.position.EarningsPolicy;
import com.example.portfolio.strategy.position.StopEngine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.security.Principal;
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
@RequestMapping("/api/v1/positions/{positionId}")
public class PositionIntelligenceController {
    private final PortfolioStore portfolio;
    private final PositionIntelligenceStore store;
    private final Optional<DebugApiAccess> debugAccess;

    public PositionIntelligenceController(
            PortfolioStore portfolio, PositionIntelligenceStore store, Optional<DebugApiAccess> debugAccess) {
        this.portfolio = portfolio;
        this.store = store;
        this.debugAccess = debugAccess;
    }

    @GetMapping("/intelligence")
    IntelligenceResponse intelligence(@PathVariable UUID positionId, Principal principal) {
        requireOwned(positionId, principal);
        requireDebugProfile();
        return new IntelligenceResponse(
                store.latestStop(principal.getName(), positionId)
                        .map(StopSnapshotResponse::from)
                        .orElse(null),
                store.thesis(principal.getName(), positionId)
                        .map(ThesisResponse::from)
                        .orElse(null),
                store.latestValuation(principal.getName(), positionId)
                        .map(ValuationResponse::from)
                        .orElse(null),
                store.latestEarnings(principal.getName(), positionId)
                        .map(EarningsResponse::from)
                        .orElse(null),
                store.journal(principal.getName(), positionId).stream()
                        .map(JournalResponse::from)
                        .toList());
    }

    @GetMapping("/journal")
    List<JournalResponse> journal(@PathVariable UUID positionId, Principal principal) {
        requireOwned(positionId, principal);
        return store.journal(principal.getName(), positionId).stream()
                .map(JournalResponse::from)
                .toList();
    }

    @PostMapping("/stops/preview")
    StopPreviewResponse previewStop(
            @PathVariable UUID positionId, @Valid @RequestBody StopPreviewRequest request, Principal principal) {
        requireOwned(positionId, principal);
        var result = StopEngine.calculate(new StopEngine.Input(
                classification(request.classification()),
                request.entry(),
                request.confirmedSwingLow(),
                request.atr(),
                request.previousLiveStop(),
                request.chandelier(),
                request.ema20(),
                request.confirmedHigherLow(),
                request.dailyClose()));
        return StopPreviewResponse.from(result);
    }

    @PostMapping("/thesis/confirm")
    ThesisResponse confirmThesis(
            @PathVariable UUID positionId, @Valid @RequestBody ConfirmThesisRequest request, Principal principal) {
        return ThesisResponse.from(store.confirmThesis(principal.getName(), positionId, request.expectedVersion()));
    }

    @PostMapping("/earnings/review")
    EarningsReviewResponse reviewEarnings(
            @PathVariable UUID positionId, @Valid @RequestBody EarningsReviewRequest request, Principal principal) {
        requireOwned(positionId, principal);
        var result = EarningsPolicy.review(new EarningsPolicy.Input(
                classification(request.classification()),
                request.eventCount(),
                request.profitCushionR(),
                request.overRiskLimit()));
        return new EarningsReviewResponse(result.action(), result.ruleIds());
    }

    private void requireOwned(UUID positionId, Principal principal) {
        portfolio
                .position(principal.getName(), positionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
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

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    public record IntelligenceResponse(
            StopSnapshotResponse stop,
            ThesisResponse thesis,
            ValuationResponse valuation,
            EarningsResponse earnings,
            List<JournalResponse> journal) {}

    public record StopPreviewRequest(
            @NotBlank String classification,
            @NotNull @DecimalMin("0.00000001") BigDecimal entry,
            @NotNull @DecimalMin("0.00000001") BigDecimal confirmedSwingLow,
            @NotNull @DecimalMin("0.00000001") BigDecimal atr,
            @NotNull @DecimalMin("0.00000001") BigDecimal previousLiveStop,
            @NotNull @DecimalMin("0.00000001") BigDecimal chandelier,
            @NotNull @DecimalMin("0.00000001") BigDecimal ema20,
            @NotNull @DecimalMin("0.00000001") BigDecimal confirmedHigherLow,
            @NotNull @DecimalMin("0.00000001") BigDecimal dailyClose) {}

    public record StopPreviewResponse(
            boolean debug,
            boolean ordinaryStopApplicable,
            String initialStop,
            String liveStop,
            String softAlert,
            String catastrophicStop,
            boolean closeConfirmed,
            boolean catastrophicBreach,
            List<String> ruleIds) {
        static StopPreviewResponse from(StopEngine.Result value) {
            return new StopPreviewResponse(
                    true,
                    value.ordinaryStopApplicable(),
                    decimal(value.initialStop()),
                    decimal(value.liveStop()),
                    decimal(value.softAlert()),
                    decimal(value.catastrophicStop()),
                    value.closeConfirmed(),
                    value.catastrophicBreach(),
                    value.ruleIds());
        }
    }

    public record ConfirmThesisRequest(long expectedVersion) {}

    public record EarningsReviewRequest(
            @NotBlank String classification,
            @Min(0) @Max(12) int eventCount,
            @NotNull BigDecimal profitCushionR,
            boolean overRiskLimit) {}

    public record EarningsReviewResponse(String action, List<String> ruleIds) {}

    public record StopSnapshotResponse(
            UUID id,
            String strategyVersion,
            String entryPrice,
            String atr,
            String initialStop,
            String liveStop,
            String softAlert,
            String catastrophicStop,
            boolean closeConfirmed,
            String ruleIdsJson,
            String qualityStatus,
            Instant dataAsOf) {
        static StopSnapshotResponse from(PositionIntelligenceStore.StopView value) {
            return new StopSnapshotResponse(
                    value.id(),
                    value.strategyVersion(),
                    decimal(value.entryPrice()),
                    decimal(value.atr()),
                    decimal(value.initialStop()),
                    decimal(value.liveStop()),
                    decimal(value.softAlert()),
                    decimal(value.catastrophicStop()),
                    value.closeConfirmed(),
                    value.ruleIds(),
                    value.qualityStatus(),
                    instant(value.dataAsOf()));
        }
    }

    public record ThesisResponse(
            UUID id,
            String summary,
            String confirmationSignalsJson,
            String invalidationSignalsJson,
            String status,
            Instant expiresAt,
            boolean userConfirmed,
            Instant confirmedAt,
            long version,
            String sourcesJson) {
        static ThesisResponse from(PositionIntelligenceStore.ThesisView value) {
            return new ThesisResponse(
                    value.id(),
                    value.summary(),
                    value.confirmationSignals(),
                    value.invalidationSignals(),
                    value.status(),
                    instant(value.expiresAt()),
                    value.userConfirmed(),
                    instant(value.confirmedAt()),
                    value.version(),
                    value.sources());
        }
    }

    public record ValuationResponse(
            String fundamentalHealth,
            boolean valuationDiscount,
            String earningsRevisions,
            String priceStabilization,
            boolean portfolioCapacity,
            String discountTacticalWeight,
            String action,
            String ruleIdsJson,
            String strategyVersion,
            Instant dataAsOf,
            Instant validUntil) {
        static ValuationResponse from(PositionIntelligenceStore.ValuationView value) {
            return new ValuationResponse(
                    value.fundamentalHealth(),
                    value.valuationDiscount(),
                    value.earningsRevisions(),
                    value.priceStabilization(),
                    value.portfolioCapacity(),
                    decimal(value.discountTacticalWeight()),
                    value.action(),
                    value.ruleIds(),
                    value.strategyVersion(),
                    instant(value.dataAsOf()),
                    instant(value.validUntil()));
        }
    }

    public record EarningsResponse(
            int eventCount,
            Instant nextEventAt,
            String downsideTailFraction,
            String gapP75Fraction,
            String gapP90Fraction,
            String profitCushionR,
            String action,
            String ruleIdsJson,
            String strategyVersion,
            Instant dataAsOf,
            Instant validUntil) {
        static EarningsResponse from(PositionIntelligenceStore.EarningsView value) {
            return new EarningsResponse(
                    value.eventCount(),
                    instant(value.nextEventAt()),
                    decimal(value.downsideTailFraction()),
                    decimal(value.gapP75Fraction()),
                    decimal(value.gapP90Fraction()),
                    decimal(value.profitCushionR()),
                    value.action(),
                    value.ruleIds(),
                    value.strategyVersion(),
                    instant(value.dataAsOf()),
                    instant(value.validUntil()));
        }
    }

    public record JournalResponse(
            UUID id,
            String entryType,
            String taxStatus,
            String plannedRiskAmount,
            String plannedR,
            String realizedR,
            String mfeR,
            String maeR,
            String exitReason,
            String notes,
            Instant occurredAt) {
        static JournalResponse from(PositionIntelligenceStore.JournalView value) {
            return new JournalResponse(
                    value.id(),
                    value.entryType(),
                    value.taxStatus(),
                    decimal(value.plannedRiskAmount()),
                    decimal(value.plannedR()),
                    decimal(value.realizedR()),
                    decimal(value.mfeR()),
                    decimal(value.maeR()),
                    value.exitReason(),
                    value.notes(),
                    instant(value.occurredAt()));
        }
    }
}
