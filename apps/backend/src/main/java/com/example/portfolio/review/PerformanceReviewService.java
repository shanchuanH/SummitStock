package com.example.portfolio.review;

import com.example.portfolio.strategy.dip.ActiveSleeveAccountability;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class PerformanceReviewService {
    private static final MathContext MATH = MathContext.DECIMAL128;
    private static final List<String> ACTIVE =
            List.of("THEMATIC_ETF", "TACTICAL_STOCK", "CYCLICAL_TACTICAL", "TURNAROUND_TACTICAL", "SPECULATIVE");
    private static final List<String> CORE = List.of("CORE_BROAD_ETF", "CORE_TECH_ETF");
    private final PerformanceReviewStore store;
    private final Clock clock;

    PerformanceReviewService(PerformanceReviewStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    Review review(UUID userId) {
        var today = LocalDate.now(clock);
        var navRows = store.nav(userId);
        var nav = dedupeNav(navRows);
        var spy = dedupePrice(store.benchmark("SPY"));
        var qqq = dedupePrice(store.benchmark("QQQ"));
        var positions = store.positions(userId);
        var periods = List.of(
                period("1M", today.minusMonths(1), nav, spy, qqq, positions),
                period("3M", today.minusMonths(3), nav, spy, qqq, positions),
                period("YTD", LocalDate.of(today.getYear(), 1, 1), nav, spy, qqq, positions),
                period("1Y", today.minusYears(1), nav, spy, qqq, positions),
                period("SINCE_INCEPTION", LocalDate.MIN, nav, spy, qqq, positions));
        var contributions = contributions(today.minusYears(1), positions, navRows).stream()
                .sorted(Comparator.comparing(Contribution::contribution).reversed())
                .toList();
        var oneYear = periods.get(3);
        var twoYear = period("24M", today.minusYears(2), nav, spy, qqq, positions);
        var accountabilityPeriod = twoYear.coverageStart() != null
                        && !twoYear.coverageStart().isAfter(today.minusYears(2).plusDays(7))
                ? twoYear
                : oneYear;
        var reviewMonths = accountabilityPeriod == twoYear ? 24 : 12;
        var activeUnderperformance =
                accountabilityPeriod.qqqReturn() == null || accountabilityPeriod.activeReturn() == null
                        ? null
                        : accountabilityPeriod.qqqReturn().subtract(accountabilityPeriod.activeReturn());
        var drawdownImproved = accountabilityPeriod.activeMaxDrawdown() != null
                && accountabilityPeriod.coreMaxDrawdown() != null
                && accountabilityPeriod.activeMaxDrawdown().compareTo(accountabilityPeriod.coreMaxDrawdown()) < 0;
        var accountability = activeUnderperformance == null
                ? null
                : ActiveSleeveAccountability.review(
                        reviewMonths, activeUnderperformance.max(BigDecimal.ZERO), drawdownImproved);
        return new Review(
                periods,
                contributions,
                decisions(store.decisions(userId), positions),
                accountability == null
                        ? null
                        : new Accountability(
                                reviewMonths,
                                accountabilityPeriod.activeReturn(),
                                accountabilityPeriod.qqqReturn(),
                                accountabilityPeriod.activeReturn().subtract(accountabilityPeriod.qqqReturn()),
                                activeContribution(today.minusMonths(reviewMonths), positions, navRows),
                                activeUnderperformance,
                                accountabilityPeriod.activeMaxDrawdown(),
                                accountabilityPeriod.coreMaxDrawdown(),
                                accountabilityPeriod.turnover(),
                                safeBudgetMultiplier(),
                                java.util.stream.Stream.concat(
                                                accountability.ruleIds().stream(),
                                                java.util.stream.Stream.of(
                                                        "ACTIVE_SLEEVE_APPROXIMATION_NO_AUTO_MULTIPLIER"))
                                        .toList(),
                                "APPROXIMATE"),
                nav.size() >= 2 ? "HEALTHY" : "INSUFFICIENT_HISTORY");
    }

    static BigDecimal safeBudgetMultiplier() {
        return BigDecimal.ONE;
    }

    private Period period(
            String code,
            LocalDate start,
            List<PerformanceMath.DatedValue> nav,
            List<PerformanceMath.DatedValue> spy,
            List<PerformanceMath.DatedValue> qqq,
            List<PerformanceReviewStore.PositionRow> positions) {
        var navWindow = window(nav, start);
        var positionWindow =
                positions.stream().filter(v -> !v.marketDate().isBefore(start)).toList();
        return new Period(
                code,
                PerformanceMath.returnBetween(navWindow),
                PerformanceMath.returnBetween(window(spy, start)),
                PerformanceMath.returnBetween(window(qqq, start)),
                sleeveReturn(start, positions, ACTIVE),
                maxDrawdown(sleeveSeries(positionWindow, ACTIVE)),
                maxDrawdown(sleeveSeries(positionWindow, CORE)),
                turnover(positionWindow),
                navWindow.isEmpty() ? null : navWindow.getFirst().date(),
                navWindow.isEmpty() ? null : navWindow.getLast().date());
    }

    private BigDecimal sleeveReturn(
            LocalDate start, List<PerformanceReviewStore.PositionRow> rows, List<String> classes) {
        var selected =
                rows.stream().filter(v -> classes.contains(v.classification())).toList();
        var byPosition = selected.stream()
                .collect(java.util.stream.Collectors.groupingBy(PerformanceReviewStore.PositionRow::positionId));
        BigDecimal startValue = BigDecimal.ZERO;
        BigDecimal gain = BigDecimal.ZERO;
        for (var values : byPosition.values()) {
            var window =
                    values.stream().filter(v -> !v.marketDate().isBefore(start)).toList();
            if (window.size() < 2) continue;
            var first = window.getFirst();
            var last = window.getLast();
            if (first.quantity().signum() <= 0 || last.quantity().signum() <= 0) continue;
            var firstPrice = first.marketValue().divide(first.quantity(), MATH);
            var lastPrice = last.marketValue().divide(last.quantity(), MATH);
            startValue = startValue.add(first.marketValue());
            gain = gain.add(first.marketValue()
                    .multiply(lastPrice.divide(firstPrice, MATH).subtract(BigDecimal.ONE)));
        }
        return startValue.signum() == 0 ? null : gain.divide(startValue, MATH);
    }

    private List<Contribution> contributions(
            LocalDate start, List<PerformanceReviewStore.PositionRow> rows, List<PerformanceReviewStore.NavRow> nav) {
        var equity = nav.stream()
                .filter(v -> !v.marketDate().isBefore(start))
                .findFirst()
                .map(PerformanceReviewStore.NavRow::accountEquity)
                .orElse(null);
        if (equity == null) return List.of();
        var result = new ArrayList<Contribution>();
        rows.stream()
                .collect(java.util.stream.Collectors.groupingBy(PerformanceReviewStore.PositionRow::positionId))
                .values()
                .forEach(values -> {
                    var window = values.stream()
                            .filter(v -> !v.marketDate().isBefore(start))
                            .toList();
                    if (window.size() < 2) return;
                    var first = window.getFirst();
                    var last = window.getLast();
                    var contribution = PerformanceMath.contribution(point(first), point(last), equity);
                    if (contribution != null) result.add(new Contribution(first.symbol(), contribution));
                });
        return List.copyOf(result);
    }

    private BigDecimal activeContribution(
            LocalDate start, List<PerformanceReviewStore.PositionRow> rows, List<PerformanceReviewStore.NavRow> nav) {
        return contributions(
                        start,
                        rows.stream()
                                .filter(v -> ACTIVE.contains(v.classification()))
                                .toList(),
                        nav)
                .stream()
                .map(Contribution::contribution)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<DecisionOutcome> decisions(
            List<PerformanceReviewStore.DecisionRow> decisions, List<PerformanceReviewStore.PositionRow> positions) {
        return decisions.stream()
                .map(value -> {
                    var objective = objective(value.action());
                    var evaluation = "USER_DECISION_PENDING";
                    if (value.decisionType() != null) {
                        evaluation = "USER_DECISION_RECORDED";
                        if ("HOLD_DO_NOT_ADD".equals(value.action())) {
                            var after = positions.stream()
                                    .filter(p -> p.positionId().equals(value.positionId())
                                            && !p.marketDate()
                                                    .isBefore(value.dataAsOf().toLocalDate()))
                                    .toList();
                            if (after.size() >= 2)
                                evaluation = after.getLast()
                                                        .quantity()
                                                        .compareTo(
                                                                after.getFirst().quantity())
                                                <= 0
                                        ? "OBJECTIVE_MAINTAINED"
                                        : "OBJECTIVE_BREACHED";
                        }
                    }
                    return new DecisionOutcome(
                            value.recommendationId(),
                            value.symbol(),
                            value.action(),
                            value.decisionType(),
                            objective,
                            evaluation,
                            "Outcome is evaluated against the rule objective, not subsequent price direction.");
                })
                .toList();
    }

    private static String objective(String action) {
        return switch (action) {
            case "HOLD_DO_NOT_ADD" -> "CONTROL_CONCENTRATION";
            case "TRIM", "REDUCE_HALF", "EXIT" -> "REDUCE_DEFINED_RISK";
            case "ADD", "STARTER_BUY", "DEPLOY_DIP_TRANCHE" -> "DEPLOY_WITHIN_RISK_BUDGET";
            default -> "MONITOR_RULE_EVIDENCE";
        };
    }

    private static BigDecimal turnover(List<PerformanceReviewStore.PositionRow> rows) {
        if (rows.isEmpty()) return null;
        BigDecimal traded = BigDecimal.ZERO;
        for (var values : rows.stream()
                .collect(java.util.stream.Collectors.groupingBy(PerformanceReviewStore.PositionRow::positionId))
                .values()) {
            for (int i = 1; i < values.size(); i++) {
                var current = values.get(i);
                var prior = values.get(i - 1);
                if (current.quantity().signum() <= 0) continue;
                var price = current.marketValue().divide(current.quantity(), MATH);
                traded = traded.add(
                        current.quantity().subtract(prior.quantity()).abs().multiply(price));
            }
        }
        var investedSeries = sleeveSeries(
                rows,
                rows.stream()
                        .map(PerformanceReviewStore.PositionRow::classification)
                        .distinct()
                        .toList());
        if (investedSeries.isEmpty()) return null;
        var averageInvested = investedSeries.stream()
                .map(PerformanceMath.DatedValue::value)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(investedSeries.size()), MATH);
        return averageInvested.signum() == 0 ? null : traded.divide(averageInvested, MATH);
    }

    private static List<PerformanceMath.DatedValue> sleeveSeries(
            List<PerformanceReviewStore.PositionRow> rows, List<String> classes) {
        var values = new LinkedHashMap<LocalDate, BigDecimal>();
        rows.stream()
                .filter(v -> classes.contains(v.classification()))
                .forEach(v -> values.merge(v.marketDate(), v.marketValue(), BigDecimal::add));
        return values.entrySet().stream()
                .map(e -> new PerformanceMath.DatedValue(e.getKey(), e.getValue()))
                .toList();
    }

    private static BigDecimal maxDrawdown(List<PerformanceMath.DatedValue> values) {
        return PerformanceMath.maxDrawdown(values);
    }

    private static PerformanceMath.PositionPoint point(PerformanceReviewStore.PositionRow value) {
        return new PerformanceMath.PositionPoint(value.marketDate(), value.quantity(), value.marketValue());
    }

    private static List<PerformanceMath.DatedValue> window(List<PerformanceMath.DatedValue> values, LocalDate start) {
        return values.stream().filter(v -> !v.date().isBefore(start)).toList();
    }

    private static List<PerformanceMath.DatedValue> dedupeNav(List<PerformanceReviewStore.NavRow> rows) {
        var result = new LinkedHashMap<LocalDate, BigDecimal>();
        rows.forEach(v -> result.put(v.marketDate(), v.nav()));
        return result.entrySet().stream()
                .map(e -> new PerformanceMath.DatedValue(e.getKey(), e.getValue()))
                .toList();
    }

    private static List<PerformanceMath.DatedValue> dedupePrice(List<PerformanceReviewStore.PriceRow> rows) {
        var result = new LinkedHashMap<LocalDate, BigDecimal>();
        rows.forEach(v -> result.put(v.marketDate(), v.closePrice()));
        return result.entrySet().stream()
                .map(e -> new PerformanceMath.DatedValue(e.getKey(), e.getValue()))
                .toList();
    }

    record Review(
            List<Period> periods,
            List<Contribution> contributions,
            List<DecisionOutcome> decisionOutcomes,
            Accountability activeSleeve,
            String quality) {}

    record Period(
            String period,
            BigDecimal portfolioTwr,
            BigDecimal spyReturn,
            BigDecimal qqqReturn,
            BigDecimal activeReturn,
            BigDecimal activeMaxDrawdown,
            BigDecimal coreMaxDrawdown,
            BigDecimal turnover,
            LocalDate coverageStart,
            LocalDate coverageEnd) {}

    record Contribution(String symbol, BigDecimal contribution) {}

    record DecisionOutcome(
            UUID recommendationId,
            String symbol,
            String action,
            String userDecision,
            String ruleObjective,
            String evaluation,
            String interpretation) {}

    record Accountability(
            int reviewMonths,
            BigDecimal activeReturn,
            BigDecimal benchmarkReturn,
            BigDecimal relativeReturn,
            BigDecimal contribution,
            BigDecimal underperformance,
            BigDecimal activeMaxDrawdown,
            BigDecimal coreMaxDrawdown,
            BigDecimal turnover,
            BigDecimal budgetMultiplier,
            List<String> ruleIds,
            String performanceQuality) {}
}
