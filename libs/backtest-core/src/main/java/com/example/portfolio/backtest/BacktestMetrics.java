package com.example.portfolio.backtest;

import java.util.List;

public final class BacktestMetrics {
    public Report aggregate(List<Observation> observations) {
        if (observations.isEmpty()) return new Report(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        return new Report(
                rate(observations, Observation::regimeCorrect),
                conditionalRate(observations, Observation::stopTriggered, Observation::stopAvoidedLoss),
                conditionalRate(observations, Observation::etfSignal, Observation::etfPositiveAt63),
                average(observations, Observation::return5),
                average(observations, Observation::return21),
                average(observations, Observation::return63),
                conditionalRate(observations, Observation::urgent, item -> !item.falseUrgency()),
                rate(observations, Observation::hold),
                rate(observations, Observation::quantityCompliant),
                rate(observations, Observation::accountabilityApplied));
    }

    private static double rate(List<Observation> values, Check check) {
        return (double) values.stream().filter(check::test).count() / values.size();
    }

    private static double conditionalRate(List<Observation> values, Check denominator, Check numerator) {
        var eligible = values.stream().filter(denominator::test).toList();
        return eligible.isEmpty() ? 0 : rate(eligible, numerator);
    }

    private static double average(List<Observation> values, NumberValue value) {
        return values.stream().mapToDouble(value::get).average().orElse(0);
    }

    public record Observation(
            boolean regimeCorrect,
            boolean stopTriggered,
            boolean stopAvoidedLoss,
            boolean etfSignal,
            boolean etfPositiveAt63,
            double return5,
            double return21,
            double return63,
            boolean urgent,
            boolean falseUrgency,
            boolean hold,
            boolean quantityCompliant,
            boolean accountabilityApplied) {}

    public record Report(
            double regimeAccuracy,
            double stopProtectionRate,
            double etfSuccessRate,
            double recommendationReturn5,
            double recommendationReturn21,
            double recommendationReturn63,
            double urgencyPrecision,
            double holdFrequency,
            double quantityCompliance,
            double accountabilityCoverage) {}

    @FunctionalInterface
    private interface Check {
        boolean test(Observation value);
    }

    @FunctionalInterface
    private interface NumberValue {
        double get(Observation value);
    }
}
