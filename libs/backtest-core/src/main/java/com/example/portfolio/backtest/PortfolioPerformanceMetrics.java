package com.example.portfolio.backtest;

import java.util.List;

/** Portfolio-level calibration metrics computed only from chronological OOS observations. */
public final class PortfolioPerformanceMetrics {
    private static final double SESSIONS_PER_YEAR = 252.0;

    public Report aggregate(List<Observation> observations) {
        if (observations.size() < 2) throw new IllegalArgumentException("INSUFFICIENT_PERFORMANCE_DATA");
        var values = List.copyOf(observations);
        for (int i = 1; i < values.size(); i++) {
            if (!values.get(i - 1).session().isBefore(values.get(i).session()))
                throw new IllegalArgumentException("PERFORMANCE_DATA_NOT_CHRONOLOGICAL");
        }
        var first = values.getFirst().equity();
        var last = values.getLast().equity();
        if (first <= 0 || last <= 0) throw new IllegalArgumentException("INVALID_EQUITY_CURVE");

        double peak = first;
        double maxDrawdown = 0;
        int underwater = 0;
        int longestUnderwater = 0;
        double sumReturn = 0;
        double sumSquaredReturn = 0;
        double tailLoss = 0;
        int tailCount = 0;
        double benchmarkGrowth = 1;
        for (int i = 1; i < values.size(); i++) {
            var current = values.get(i);
            var previous = values.get(i - 1);
            var dailyReturn = current.equity() / previous.equity() - 1;
            sumReturn += dailyReturn;
            sumSquaredReturn += dailyReturn * dailyReturn;
            benchmarkGrowth *= 1 + current.benchmarkReturn();
            if (dailyReturn < 0) {
                tailLoss += dailyReturn;
                tailCount++;
            }
            peak = Math.max(peak, current.equity());
            var drawdown = 1 - current.equity() / peak;
            maxDrawdown = Math.max(maxDrawdown, drawdown);
            underwater = drawdown > 0 ? underwater + 1 : 0;
            longestUnderwater = Math.max(longestUnderwater, underwater);
        }
        var periods = values.size() - 1;
        var mean = sumReturn / periods;
        var variance = Math.max(0, sumSquaredReturn / periods - mean * mean);
        var annualizedReturn = Math.pow(last / first, SESSIONS_PER_YEAR / periods) - 1;
        var benchmarkReturn = benchmarkGrowth - 1;
        return new Report(
                annualizedReturn,
                maxDrawdown,
                Math.sqrt(variance * SESSIONS_PER_YEAR),
                values.stream().mapToDouble(Observation::turnover).average().orElse(0),
                values.stream().mapToDouble(Observation::rMultiple).average().orElse(0),
                tailCount == 0 ? 0 : tailLoss / tailCount,
                longestUnderwater,
                values.stream().mapToDouble(Observation::exposure).average().orElse(0),
                last / first - 1 - benchmarkReturn);
    }

    public record Observation(
            java.time.LocalDate session,
            double equity,
            double turnover,
            double rMultiple,
            double exposure,
            double benchmarkReturn) {}

    public record Report(
            double cagr,
            double maxDrawdown,
            double volatility,
            double turnover,
            double averageR,
            double tailLoss,
            int timeUnderwaterSessions,
            double exposure,
            double benchmarkRelativeReturn) {}
}
