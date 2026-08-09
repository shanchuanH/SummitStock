package com.example.portfolio.strategy.position;

public final class SpeculativeTimeStopPolicy {
    private SpeculativeTimeStopPolicy() {}

    public static boolean expired(int holdingTradingDays, int maxTradingDays, boolean thesisProgress) {
        if (holdingTradingDays < 0 || maxTradingDays <= 0)
            throw new IllegalArgumentException("Invalid time-stop horizon");
        return holdingTradingDays >= maxTradingDays && !thesisProgress;
    }
}
