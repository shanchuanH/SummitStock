package com.example.portfolio.context;

import com.example.portfolio.context.MarketContextStore.DrawdownView;
import com.example.portfolio.context.MarketContextStore.RegimeView;
import com.example.portfolio.macro.MacroApplicationService;
import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MarketContextController {
    private final MarketContextStore store;
    private final MacroApplicationService macro;
    private final Clock clock;

    public MarketContextController(MarketContextStore store, MacroApplicationService macro, Clock clock) {
        this.store = store;
        this.macro = macro;
        this.clock = clock;
    }

    @GetMapping("/market/volatility")
    SnapshotEnvelope<VolatilityResponse> volatility() {
        var snapshot = macro.latestVolatility(LocalDate.now(clock));
        var status = snapshot.vix() == null ? "EMPTY" : "READY";
        return new SnapshotEnvelope<>(status, VolatilityResponse.from(snapshot), clock.instant());
    }

    @GetMapping("/market/regime")
    SnapshotEnvelope<RegimeResponse> regime() {
        return store.latestRegime()
                .map(value -> new SnapshotEnvelope<>("READY", RegimeResponse.from(value), clock.instant()))
                .orElseGet(() -> new SnapshotEnvelope<>("EMPTY", null, clock.instant()));
    }

    @GetMapping("/portfolio/drawdown")
    SnapshotEnvelope<DrawdownResponse> drawdown(Principal principal) {
        return store.latestDrawdown(principal.getName())
                .map(value -> new SnapshotEnvelope<>("READY", DrawdownResponse.from(value), clock.instant()))
                .orElseGet(() -> new SnapshotEnvelope<>("EMPTY", null, clock.instant()));
    }

    public record SnapshotEnvelope<T>(String status, T snapshot, Instant dataAsOf) {}

    public record RegimeResponse(
            String strategyVersion,
            String label,
            double score,
            double trendScore,
            double momentumScore,
            double breadthScore,
            double stressScore,
            String confidence,
            boolean tacticalCapFivePercent,
            String qualityStatus,
            String narrativesJson,
            String ruleIdsJson,
            Instant dataAsOf) {
        static RegimeResponse from(RegimeView value) {
            return new RegimeResponse(
                    value.strategyVersion(),
                    value.regimeLabel(),
                    value.totalScore(),
                    value.trendScore(),
                    value.momentumScore(),
                    value.breadthScore(),
                    value.stressScore(),
                    value.confidence(),
                    value.tacticalCapFivePercent(),
                    value.qualityStatus(),
                    value.narratives(),
                    value.ruleIds(),
                    value.dataAsOf().toInstant(ZoneOffset.UTC));
        }
    }

    public record DrawdownResponse(
            String strategyVersion,
            String currentEquity,
            String highWaterMark,
            String drawdownFraction,
            String drawdownPercent,
            String state,
            String sourceClassification,
            boolean marketDriven,
            String spyReturnFromPeak,
            String qqqReturnFromPeak,
            String spyDrawdownPercent,
            String qqqDrawdownPercent,
            String breadth50,
            String stressLevel,
            String positionAttributionJson,
            String clusterAttributionJson,
            String confidence,
            String qualityStatus,
            String narrativesJson,
            String ruleIdsJson,
            Instant dataAsOf) {
        static DrawdownResponse from(DrawdownView value) {
            return new DrawdownResponse(
                    value.strategyVersion(),
                    decimal(value.currentEquity()),
                    decimal(value.highWaterMark()),
                    decimal(value.drawdownFraction()),
                    percent(value.drawdownFraction()),
                    value.drawdownState(),
                    value.sourceClassification(),
                    value.marketDriven(),
                    decimal(value.spyReturnFromPeak()),
                    decimal(value.qqqReturnFromPeak()),
                    percent(value.spyReturnFromPeak()),
                    percent(value.qqqReturnFromPeak()),
                    decimal(value.breadth50()),
                    decimal(value.stressLevel()),
                    value.positionAttribution(),
                    value.clusterAttribution(),
                    value.confidence(),
                    value.qualityStatus(),
                    value.narratives(),
                    value.ruleIds(),
                    value.dataAsOf().toInstant(ZoneOffset.UTC));
        }
    }

    public record VolatilityResponse(
            String vix,
            String vixPercentile,
            String vixDelta1d,
            String vixDelta2d,
            String vixDelta5d,
            String vix3m,
            String vixTermRatio,
            String vixTermState,
            String vxn,
            String vxnPercentile,
            String vxnDelta1d,
            String vxnDelta2d,
            String vxnDelta5d,
            String vxnVixRatio,
            String vxnVixSpread,
            String techStressState,
            String quality) {
        static VolatilityResponse from(MacroApplicationService.VolatilitySnapshot value) {
            return new VolatilityResponse(
                    nullableDecimal(value.vix()),
                    nullableDecimal(value.vixPercentile()),
                    nullableDecimal(value.vixDelta1d()),
                    nullableDecimal(value.vixDelta2d()),
                    nullableDecimal(value.vixDelta5d()),
                    nullableDecimal(value.vix3m()),
                    nullableDecimal(value.vixTermRatio()),
                    value.vixTermState(),
                    nullableDecimal(value.vxn()),
                    nullableDecimal(value.vxnPercentile()),
                    nullableDecimal(value.vxnDelta1d()),
                    nullableDecimal(value.vxnDelta2d()),
                    nullableDecimal(value.vxnDelta5d()),
                    nullableDecimal(value.vxnVixRatio()),
                    nullableDecimal(value.vxnVixSpread()),
                    value.techStressState(),
                    value.quality());
        }
    }

    private static String decimal(java.math.BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private static String nullableDecimal(java.math.BigDecimal value) {
        return value == null ? null : decimal(value);
    }

    private static String percent(java.math.BigDecimal fraction) {
        return fraction.multiply(java.math.BigDecimal.valueOf(100))
                .stripTrailingZeros()
                .toPlainString();
    }
}
