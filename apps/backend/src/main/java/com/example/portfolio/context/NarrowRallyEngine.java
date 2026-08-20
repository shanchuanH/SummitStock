package com.example.portfolio.context;

import java.math.BigDecimal;

public final class NarrowRallyEngine {
    public Result evaluate(Input input) {
        var breadthDeteriorating = decreased(input.breadth50(), input.priorBreadth50())
                || decreased(input.breadth200(), input.priorBreadth200());
        var newHighsDeteriorating = decreased(input.newHighParticipation(), input.priorNewHighParticipation());
        var equalWeightLagging = input.equalWeightRelativeReturn63d() != null
                && input.equalWeightRelativeReturn63d().signum() < 0;
        var complete = input.breadth50() != null
                && input.breadth200() != null
                && input.priorBreadth50() != null
                && input.priorBreadth200() != null
                && input.newHighParticipation() != null
                && input.priorNewHighParticipation() != null
                && input.equalWeightRelativeReturn63d() != null;
        var weakParticipationSignals = count(breadthDeteriorating, newHighsDeteriorating, equalWeightLagging);
        var narrow = complete && input.spyAbove200() && input.qqqAbove200() && weakParticipationSignals >= 2;
        return new Result(
                narrow,
                complete,
                breadthDeteriorating,
                newHighsDeteriorating,
                equalWeightLagging,
                weakParticipationSignals);
    }

    private static boolean decreased(BigDecimal current, BigDecimal prior) {
        return current != null && prior != null && current.compareTo(prior) < 0;
    }

    private static int count(boolean... values) {
        int count = 0;
        for (var value : values) if (value) count++;
        return count;
    }

    public record Input(
            boolean spyAbove200,
            boolean qqqAbove200,
            BigDecimal breadth50,
            BigDecimal breadth200,
            BigDecimal priorBreadth50,
            BigDecimal priorBreadth200,
            BigDecimal newHighParticipation,
            BigDecimal priorNewHighParticipation,
            BigDecimal equalWeightRelativeReturn63d) {}

    public record Result(
            boolean narrowRally,
            boolean complete,
            boolean breadthDeteriorating,
            boolean newHighsDeteriorating,
            boolean equalWeightLagging,
            int weakParticipationSignals) {}
}
