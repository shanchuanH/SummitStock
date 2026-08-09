package com.example.portfolio.strategy.position;

public final class PriceStateEngine {
    public Result evaluate(Input input) {
        if (!input.complete()) return new Result(State.NEUTRAL, 0);
        int confirmations = confirmations(input);
        if (input.close() < input.sma200() && input.relativeStrength() < 0.90) {
            return new Result(State.BREAKDOWN, confirmations);
        }
        if (confirmations >= 2 && input.close() >= input.sma20()) {
            return new Result(State.REVERSAL_CONFIRMED, confirmations);
        }
        if (confirmations >= 1 && input.close() < input.sma50()) {
            return new Result(State.REVERSAL_SETUP, confirmations);
        }
        if (input.close() > input.sma20()
                && input.close() > input.sma50()
                && input.close() > input.sma200()
                && input.sma50() > input.sma200()
                && input.macdHistogram() > 0
                && input.rsi() >= 55
                && input.relativeStrength() >= 1.0) {
            return new Result(State.STRONG_UPTREND, confirmations);
        }
        if (input.close() > input.sma50() && input.sma50() > input.sma200() && input.relativeStrength() >= 1.0) {
            return new Result(State.UPTREND, confirmations);
        }
        if (input.close() < input.sma20() && input.close() < input.sma50() && input.macdHistogram() < 0) {
            return new Result(State.DOWNTREND, confirmations);
        }
        if (input.close() < input.sma20() || input.relativeStrength() < 1.0) {
            return new Result(State.WEAK, confirmations);
        }
        return new Result(State.NEUTRAL, confirmations);
    }

    private static int confirmations(Input input) {
        int result = 0;
        if (input.reclaimedSma20()) result++;
        if (input.higherLow()) result++;
        if (input.macdPositiveCross()) result++;
        if (input.rsiRecovery()) result++;
        if (input.relativeStrengthStabilized()) result++;
        if (input.highVolumeReversal()) result++;
        return result;
    }

    public enum State {
        STRONG_UPTREND,
        UPTREND,
        NEUTRAL,
        WEAK,
        DOWNTREND,
        REVERSAL_SETUP,
        REVERSAL_CONFIRMED,
        BREAKDOWN
    }

    public record Input(
            double close,
            double sma20,
            double sma50,
            double sma200,
            double rsi,
            double macdHistogram,
            double rollingHighDrawdown,
            double relativeStrength,
            boolean reclaimedSma20,
            boolean higherLow,
            boolean macdPositiveCross,
            boolean rsiRecovery,
            boolean relativeStrengthStabilized,
            boolean highVolumeReversal,
            boolean complete) {}

    public record Result(State state, int reversalConfirmations) {}
}
