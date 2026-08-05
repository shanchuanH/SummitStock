package com.example.portfolio.strategy.foundation;

import com.example.portfolio.strategy.RuleIds;
import java.math.BigDecimal;
import java.util.List;

public final class FoundationInvariants {
    private FoundationInvariants() {}

    public static AssetEligibility unvestedCompensation() {
        return new AssetEligibility(false, false, false, BigDecimal.ZERO, List.of(RuleIds.UNVESTED_COMPENSATION));
    }

    public static BigDecimal deployableCash(BigDecimal totalCash, BigDecimal emergencyFloor) {
        if (totalCash.signum() < 0 || emergencyFloor.signum() < 0) {
            throw new IllegalArgumentException("Cash values must be non-negative");
        }
        return totalCash.subtract(emergencyFloor).max(BigDecimal.ZERO);
    }
}
