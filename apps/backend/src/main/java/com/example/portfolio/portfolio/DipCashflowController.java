package com.example.portfolio.portfolio;

import com.example.portfolio.strategy.dip.ActiveSleeveAccountability;
import com.example.portfolio.strategy.dip.CashflowAllocator;
import com.example.portfolio.strategy.dip.EtfDipEngine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DipCashflowController {
    private final DipCashflowStore store;

    public DipCashflowController(DipCashflowStore store) {
        this.store = store;
    }

    @GetMapping("/etf-dip/status")
    DipStatusEnvelope status(Principal principal) {
        return store.latestDip(principal.getName())
                .map(v -> new DipStatusEnvelope("READY", DipStatusResponse.from(v)))
                .orElseGet(() -> new DipStatusEnvelope("EMPTY", null));
    }

    @PostMapping("/etf-dip/preview")
    DipPreviewResponse preview(@Valid @RequestBody DipPreviewRequest r) {
        var result = EtfDipEngine.evaluate(new EtfDipEngine.Input(
                r.portfolioDrawdown(),
                r.marketDriven(),
                r.completeData(),
                r.emergencyCashProtected(),
                r.drawdownScore(),
                r.vixPercentileScore(),
                r.breadthOversoldScore(),
                r.creditStressScore(),
                r.volTermScore(),
                r.trendContextScore(),
                r.rsiCross40(),
                r.breakout5Day(),
                r.aboveEma20(),
                r.breadthImproving(),
                r.vixFalling(),
                r.creditStable(),
                r.completedTranches(),
                r.tradingDaysSinceLastTranche()));
        return new DipPreviewResponse(
                result.action(),
                result.setupScore(),
                result.triggerCount(),
                result.trancheNumber(),
                decimal(result.reserveFraction()),
                result.ruleIds(),
                false);
    }

    @PostMapping("/cashflow/plan")
    CashflowPlanResponse cashflow(@Valid @RequestBody CashflowPlanRequest r) {
        var p = CashflowAllocator.allocate(
                r.monthlyTakeHome(), r.monthlyExpenses(), r.emergencyCash(), r.qualitySignal());
        return new CashflowPlanResponse(
                decimal(p.surplus()),
                decimal(p.emergency()),
                decimal(p.broadCore()),
                decimal(p.techCore()),
                decimal(p.internationalCore()),
                decimal(p.tacticalReserve()),
                decimal(p.qualityOpportunity()),
                p.ruleIds());
    }

    @PostMapping("/active-sleeve/review")
    AccountabilityResponse accountability(@Valid @RequestBody AccountabilityRequest r) {
        var result = ActiveSleeveAccountability.review(r.months(), r.underperformance(), r.drawdownImproved());
        return new AccountabilityResponse(decimal(result.budgetMultiplier()), result.ruleIds());
    }

    @GetMapping("/recommendations/history")
    List<HistoryResponse> history(Principal principal) {
        return store.recommendationHistory(principal.getName()).stream()
                .map(HistoryResponse::from)
                .toList();
    }

    private static String decimal(BigDecimal v) {
        return v == null ? null : v.stripTrailingZeros().toPlainString();
    }

    private static Instant instant(LocalDateTime v) {
        return v == null ? null : v.toInstant(ZoneOffset.UTC);
    }

    public record DipStatusEnvelope(String status, DipStatusResponse event) {}

    public record DipStatusResponse(
            String symbol,
            String strategyVersion,
            String status,
            double setupScore,
            int triggerCount,
            String portfolioDrawdown,
            boolean marketDriven,
            boolean emergencyCashProtected,
            String ruleIdsJson,
            Instant dataAsOf,
            Instant validUntil) {
        static DipStatusResponse from(DipCashflowStore.DipView v) {
            return new DipStatusResponse(
                    v.symbol(),
                    v.strategyVersion(),
                    v.status(),
                    v.setupScore(),
                    v.triggerCount(),
                    decimal(v.portfolioDrawdown()),
                    v.marketDriven(),
                    v.emergencyCashProtected(),
                    v.ruleIds(),
                    instant(v.dataAsOf()),
                    instant(v.validUntil()));
        }
    }

    public record DipPreviewRequest(
            @NotNull @DecimalMin("0") BigDecimal portfolioDrawdown,
            boolean marketDriven,
            boolean completeData,
            boolean emergencyCashProtected,
            double drawdownScore,
            double vixPercentileScore,
            double breadthOversoldScore,
            double creditStressScore,
            double volTermScore,
            double trendContextScore,
            boolean rsiCross40,
            boolean breakout5Day,
            boolean aboveEma20,
            boolean breadthImproving,
            boolean vixFalling,
            boolean creditStable,
            @Min(0) @Max(4) int completedTranches,
            @Min(0) int tradingDaysSinceLastTranche) {}

    public record DipPreviewResponse(
            String action,
            double setupScore,
            int triggerCount,
            Integer trancheNumber,
            String reserveFraction,
            List<String> ruleIds,
            boolean executionSubmitted) {}

    public record CashflowPlanRequest(
            @NotNull @DecimalMin("0") BigDecimal monthlyTakeHome,
            @NotNull @DecimalMin("0") BigDecimal monthlyExpenses,
            @NotNull @DecimalMin("0") BigDecimal emergencyCash,
            boolean qualitySignal) {}

    public record CashflowPlanResponse(
            String surplus,
            String emergency,
            String broadCore,
            String techCore,
            String internationalCore,
            String tacticalReserve,
            String qualityOpportunity,
            List<String> ruleIds) {}

    public record AccountabilityRequest(
            @Min(0) int months, @NotNull @DecimalMin("0") BigDecimal underperformance, boolean drawdownImproved) {}

    public record AccountabilityResponse(String budgetMultiplier, List<String> ruleIds) {}

    public record HistoryResponse(
            String symbol,
            String action,
            String priority,
            String confidence,
            String strategyVersion,
            String ruleIdsJson,
            Instant dataAsOf,
            Instant validUntil,
            String status) {
        static HistoryResponse from(DipCashflowStore.HistoryView v) {
            return new HistoryResponse(
                    v.symbol(),
                    v.action(),
                    v.priority(),
                    v.confidence(),
                    v.strategyVersion(),
                    v.ruleIds(),
                    instant(v.dataAsOf()),
                    instant(v.validUntil()),
                    v.status());
        }
    }
}
