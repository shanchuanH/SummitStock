package com.example.portfolio.brief;

import com.example.portfolio.analysis.domain.PortfolioAnalysisState;
import com.example.portfolio.portfolio.PortfolioStore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ExecutiveBriefQueryService {
    private final PortfolioStore portfolios;
    private final ExecutiveBriefStore briefStore;
    private final PortfolioReadinessService readiness;

    public ExecutiveBriefQueryService(
            PortfolioStore portfolios, ExecutiveBriefStore briefStore, PortfolioReadinessService readiness) {
        this.portfolios = portfolios;
        this.briefStore = briefStore;
        this.readiness = readiness;
    }

    public ExecutiveBrief today(String email) {
        var summary = portfolios.summary(email);
        var cash = briefStore.cashSummary(email);
        var metrics = briefStore.portfolioMetrics(email);
        var marketSnapshot = briefStore.latestMarket();
        var evidence = briefStore.evidence(email);
        var metadata = briefStore.analysisMetadata(email);
        var run = briefStore.latestAnalysisRun(email);
        var state = readiness.assess(new PortfolioReadinessService.ReadinessFacts(
                evidence.openPositions(),
                evidence.importPending(),
                evidence.importing(),
                evidence.analysisRunExists()
                        || evidence.anyAnalysisPositions() > 0
                        || evidence.analysisQueued()
                        || evidence.failedJobCount() > 0,
                evidence.analysisQueued(),
                evidence.missingMarketPositions(),
                evidence.requiredFundamentalPositions(),
                evidence.missingFundamentalPositions(),
                evidence.analyzedPositions(),
                evidence.staleAnalysisPositions(),
                evidence.blocked(),
                evidence.failedJobCount() > 0 && evidence.analyzedPositions() == 0));
        var recommendations =
                state == PortfolioAnalysisState.ANALYSIS_READY || state == PortfolioAnalysisState.PARTIAL_ANALYSIS
                        ? portfolios.activeRecommendations(email)
                        : List.<PortfolioStore.RecommendationView>of();
        var mustAct = actions(recommendations, "MUST_ACT", 3);
        var doNot = actions(recommendations, "DO_NOT", Integer.MAX_VALUE);
        var blockedRecommendations = recommendations.stream()
                .filter(ExecutiveBriefQueryService::isDataBlocked)
                .toList();
        var watch = actions(
                recommendations.stream().filter(item -> !isDataBlocked(item)).toList(), "WATCH", Integer.MAX_VALUE);
        var opportunities = recommendations.stream()
                .filter(item -> !isDataBlocked(item) && isOpportunity(item.action()))
                .map(ExecutiveBriefQueryService::action)
                .toList();
        var blocked = blockedRecommendations.stream()
                .map(ExecutiveBriefQueryService::action)
                .toList();
        var portfolioHealth = health(state, mustAct);
        var readinessView = new DataReadiness(
                dataStatus(state),
                coverage(evidence.openPositions() - evidence.missingMarketPositions(), evidence.openPositions()),
                coverage(
                        evidence.requiredFundamentalPositions() - evidence.missingFundamentalPositions(),
                        evidence.requiredFundamentalPositions()),
                evidence.staleAnalysisPositions(),
                Math.max(0, evidence.openPositions() - evidence.analyzedPositions()),
                evidence.failedJobCount());
        var confirmedNoAction = confirmedNoAction(state, mustAct, doNot, watch, blocked, readinessView);
        var strategyVersion = run != null
                ? run.strategyVersion()
                : metadata.strategyVersion() == null
                        ? briefStore.currentStrategyVersion(email)
                        : metadata.strategyVersion();
        var dataAsOf = run != null && run.dataAsOf() != null
                ? instant(run.dataAsOf())
                : metadata.dataAsOf() == null ? instant(summary.dataAsOf()) : instant(metadata.dataAsOf());
        return new ExecutiveBrief(
                state,
                confirmedNoAction,
                headline(state, mustAct.size(), watch.size()),
                new PortfolioSummary(
                        decimal(summary.investedValue()),
                        decimal(cash.trackedCash()),
                        decimal(cash.emergencyCash()),
                        decimal(cash.tacticalReserve()),
                        summary.openPositions(),
                        decimal(summary.investedValue().add(cash.trackedCash())),
                        fraction(metrics.coreValue(), summary.investedValue().add(cash.trackedCash())),
                        fraction(
                                metrics.tacticalValue(), summary.investedValue().add(cash.trackedCash())),
                        decimal(metrics.technologyExposureFraction()),
                        decimal(metrics.employerExposureFraction()),
                        decimal(metrics.clusterRiskFraction()),
                        decimal(metrics.openPlannedRiskFraction()),
                        decimal(metrics.unvestedCompensationValue()),
                        decimal(metrics.drawdownFraction()),
                        metrics.drawdownSource()),
                new Market(
                        marketSnapshot.regime(),
                        marketSnapshot.score(),
                        marketSnapshot.confidence(),
                        marketSnapshot.qualityStatus(),
                        marketSnapshot.regime().equals("UNKNOWN")
                                ? "Required market-regime evidence is not available."
                                : "Regime " + marketSnapshot.regime() + " with " + marketSnapshot.confidence()
                                        + " confidence.",
                        instant(marketSnapshot.dataAsOf())),
                new Capital(
                        decimal(summary.investedValue().add(cash.trackedCash())),
                        decimal(cash.emergencyCash()),
                        decimal(cash.trackedCash()
                                .subtract(cash.emergencyCash())
                                .max(BigDecimal.ZERO)),
                        decimal(summary.investedValue()
                                .add(cash.trackedCash())
                                .subtract(cash.emergencyCash())
                                .max(BigDecimal.ZERO)),
                        decimal(cash.tacticalReserve())),
                new PortfolioCommand(
                        decimal(metrics.drawdownFraction()),
                        metrics.drawdownSource(),
                        decimal(metrics.technologyExposureFraction()),
                        decimal(metrics.openPlannedRiskFraction()),
                        decimal(metrics.clusterRiskFraction())),
                mustAct,
                doNot,
                watch,
                opportunities,
                blocked,
                portfolioHealth,
                readinessView,
                briefStore.nextEvents(email).stream()
                        .map(event -> new NextEvent(
                                event.symbol(),
                                event.eventType(),
                                event.title(),
                                instant(event.eventAt()),
                                event.qualityStatus()))
                        .toList(),
                run == null ? null : run.runId(),
                strategyVersion,
                dataAsOf);
    }

    static boolean confirmedNoAction(
            PortfolioAnalysisState state,
            List<BriefAction> mustAct,
            List<BriefAction> doNot,
            List<BriefAction> watch,
            List<BriefAction> blocked,
            DataReadiness readiness) {
        return state == PortfolioAnalysisState.ANALYSIS_READY
                && mustAct.isEmpty()
                && doNot.isEmpty()
                && watch.isEmpty()
                && blocked.isEmpty()
                && "HEALTHY".equals(readiness.status())
                && "1".equals(readiness.marketCoverage())
                && "1".equals(readiness.fundamentalCoverage())
                && readiness.stalePositionCount() == 0
                && readiness.missingPositionCount() == 0
                && readiness.failedJobCount() == 0;
    }

    private static List<BriefAction> actions(
            List<PortfolioStore.RecommendationView> recommendations, String priority, int limit) {
        return recommendations.stream()
                .filter(item -> priority.equals(item.priority()))
                .limit(limit)
                .map(item -> new BriefAction(
                        item.id(),
                        item.positionId(),
                        item.symbol(),
                        item.classification(),
                        item.action(),
                        item.priority(),
                        decimal(item.quantityMin()),
                        decimal(item.quantityMax()),
                        decimal(item.currentWeight()),
                        decimal(item.targetWeightMin()),
                        decimal(item.targetWeightMax()),
                        decimal(item.estimatedAmount()),
                        decimal(item.riskBeforeFraction()),
                        decimal(item.riskAfterFraction()),
                        item.riskCalculationReason(),
                        item.taxLotStatus(),
                        item.confidence(),
                        item.reasons(),
                        item.risks(),
                        item.changeConditions(),
                        instant(item.dataAsOf()),
                        instant(item.validUntil())))
                .toList();
    }

    private static BriefAction action(PortfolioStore.RecommendationView item) {
        return new BriefAction(
                item.id(),
                item.positionId(),
                item.symbol(),
                item.classification(),
                item.action(),
                item.priority(),
                decimal(item.quantityMin()),
                decimal(item.quantityMax()),
                decimal(item.currentWeight()),
                decimal(item.targetWeightMin()),
                decimal(item.targetWeightMax()),
                decimal(item.estimatedAmount()),
                decimal(item.riskBeforeFraction()),
                decimal(item.riskAfterFraction()),
                item.riskCalculationReason(),
                item.taxLotStatus(),
                item.confidence(),
                item.reasons(),
                item.risks(),
                item.changeConditions(),
                instant(item.dataAsOf()),
                instant(item.validUntil()));
    }

    private static boolean isDataBlocked(PortfolioStore.RecommendationView item) {
        return "WAIT_FOR_DATA".equals(item.action()) || "WAIT_FOR_DATA".equals(item.confidence());
    }

    private static boolean isOpportunity(String action) {
        return action != null
                && (action.contains("BUY")
                        || action.contains("ADD")
                        || action.contains("STARTER")
                        || action.contains("DEPLOY_DIP"));
    }

    private static PortfolioHealth health(PortfolioAnalysisState state, List<BriefAction> mustAct) {
        if (state == PortfolioAnalysisState.BLOCKED || state == PortfolioAnalysisState.FAILED) {
            return new PortfolioHealth("BLOCKED", List.of(headline(state, 0, 0)));
        }
        if (state != PortfolioAnalysisState.ANALYSIS_READY || !mustAct.isEmpty()) {
            return new PortfolioHealth("WARNING", List.of(headline(state, mustAct.size(), 0)));
        }
        return new PortfolioHealth("HEALTHY", List.of());
    }

    private static String dataStatus(PortfolioAnalysisState state) {
        return switch (state) {
            case ANALYSIS_READY -> "HEALTHY";
            case PARTIAL_ANALYSIS, STALE, WAIT_FOR_MARKET_DATA, WAIT_FOR_FUNDAMENTALS -> "PARTIAL";
            case FAILED, BLOCKED -> "BLOCKED";
            default -> "NOT_READY";
        };
    }

    static String headline(PortfolioAnalysisState state, int mustActCount, int watchCount) {
        return switch (state) {
            case NO_PORTFOLIO -> "No portfolio has been imported.";
            case IMPORT_PENDING_CONFIRMATION -> "The imported portfolio is waiting for confirmation.";
            case IMPORTING -> "The portfolio import is in progress.";
            case PORTFOLIO_READY -> "The portfolio is ready; analysis has not started.";
            case ANALYSIS_QUEUED -> "Portfolio analysis is queued.";
            case WAIT_FOR_MARKET_DATA -> "Analysis is waiting for required market data.";
            case WAIT_FOR_FUNDAMENTALS -> "Analysis is waiting for required fundamentals.";
            case PARTIAL_ANALYSIS -> "Some positions have not completed analysis.";
            case STALE -> "The latest portfolio analysis is stale.";
            case BLOCKED -> "Portfolio analysis is blocked by a hard safety gate.";
            case FAILED -> "Portfolio analysis failed and no usable prior result is available.";
            case ANALYSIS_READY ->
                mustActCount == 0 && watchCount == 0
                        ? "NO URGENT ACTION"
                        : mustActCount + " item(s) require action; " + watchCount + " item(s) require watching.";
        };
    }

    private static String coverage(long covered, long required) {
        if (required == 0) return "1";
        return BigDecimal.valueOf(covered)
                .divide(BigDecimal.valueOf(required), 4, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static String fraction(BigDecimal value, BigDecimal total) {
        if (value == null || total == null || total.signum() == 0) return null;
        return decimal(value.divide(total, 10, RoundingMode.HALF_UP));
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    public record ExecutiveBrief(
            @NotNull PortfolioAnalysisState state,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean confirmedNoAction,
            @NotNull String headline,
            @NotNull @Valid PortfolioSummary summary,
            @NotNull @Valid Market market,
            @NotNull @Valid Capital capital,
            @NotNull @Valid PortfolioCommand portfolio,
            @NotNull List<@Valid BriefAction> mustAct,
            @NotNull List<@Valid BriefAction> doNot,
            @NotNull List<@Valid BriefAction> watch,
            @NotNull List<@Valid BriefAction> opportunities,
            @NotNull List<@Valid BriefAction> blocked,
            @NotNull @Valid PortfolioHealth portfolioHealth,
            @NotNull @Valid DataReadiness dataReadiness,
            @NotNull List<@Valid NextEvent> nextEvents,
            UUID analysisRunId,
            String strategyVersion,
            Instant dataAsOf) {}

    public record Market(
            @NotNull String regime,
            Double score,
            @NotNull String confidence,
            @NotNull String qualityStatus,
            @NotNull String summary,
            Instant dataAsOf) {}

    public record Capital(
            @NotNull String totalLiquidAssets,
            @NotNull String emergencyReserve,
            @NotNull String deployableCash,
            @NotNull String investableAssets,
            @NotNull String tacticalReserve) {}

    public record PortfolioCommand(
            String drawdown, String drawdownSource, String technologyExposure, String openRisk, String clusterRisk) {}

    public record PortfolioSummary(
            @NotNull String investedValue,
            @NotNull String trackedCash,
            @NotNull String emergencyCash,
            @NotNull String tacticalReserve,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long openPositions,
            @NotNull String totalLiquidAssets,
            String coreExposureFraction,
            String tacticalExposureFraction,
            String technologyExposureFraction,
            String employerExposureFraction,
            String clusterRiskFraction,
            String openPlannedRiskFraction,
            @NotNull String unvestedCompensationValue,
            String portfolioDrawdownFraction,
            String drawdownSource) {}

    public record BriefAction(
            @NotNull UUID id,
            UUID positionId,
            String symbol,
            String classification,
            @NotNull String action,
            @NotNull String priority,
            String quantityMin,
            String quantityMax,
            String currentWeight,
            String targetWeightMin,
            String targetWeightMax,
            String estimatedAmount,
            String riskBeforeFraction,
            String riskAfterFraction,
            @NotNull String riskCalculationReason,
            @NotNull String taxLotStatus,
            @NotNull String confidence,
            @NotNull String reasonsJson,
            @NotNull String risksJson,
            @NotNull String changeConditionsJson,
            @NotNull Instant dataAsOf,
            @NotNull Instant validUntil) {}

    public record PortfolioHealth(@NotNull String status, @NotNull List<String> reasons) {}

    public record DataReadiness(
            @NotNull String status,
            @NotNull String marketCoverage,
            @NotNull String fundamentalCoverage,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long stalePositionCount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long missingPositionCount,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long failedJobCount) {}

    public record NextEvent(
            @NotNull String symbol,
            @NotNull String eventType,
            @NotNull String title,
            @NotNull Instant eventAt,
            @NotNull String qualityStatus) {}
}
