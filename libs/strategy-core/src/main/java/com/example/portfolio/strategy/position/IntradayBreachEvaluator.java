package com.example.portfolio.strategy.position;

import com.example.portfolio.strategy.RuleIds;
import java.math.BigDecimal;
import java.util.List;

public final class IntradayBreachEvaluator {
    private IntradayBreachEvaluator() {}

    public static Result evaluate(BigDecimal intradayPriceOrLow, BigDecimal catastrophicStop) {
        if (intradayPriceOrLow == null || intradayPriceOrLow.signum() <= 0) {
            return new Result(false, List.of(), "Intraday market evidence is unavailable.");
        }
        if (catastrophicStop == null || catastrophicStop.signum() <= 0) {
            return new Result(false, List.of(), "No valid catastrophic threshold is available.");
        }
        var breached = intradayPriceOrLow.compareTo(catastrophicStop) < 0;
        return new Result(
                breached,
                breached ? List.of(RuleIds.STOP_CATASTROPHIC) : List.of(),
                breached
                        ? "Intraday price breached the catastrophic threshold."
                        : "Intraday price remains above the catastrophic threshold.");
    }

    public record Result(boolean breached, List<String> ruleIds, String reason) {
        public Result {
            ruleIds = List.copyOf(ruleIds);
        }
    }
}
