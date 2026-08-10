package com.example.portfolio.analysis.capital;

import com.example.portfolio.strategy.market.EvidenceQuality;
import java.math.BigDecimal;

public record CapitalBase(
        BigDecimal investedTradableAssets,
        BigDecimal trackedCash,
        BigDecimal emergencyReserve,
        BigDecimal deployableCash,
        BigDecimal investableAssets,
        BigDecimal totalLiquidAssets,
        BigDecimal unvestedCompensationValue,
        EvidenceQuality quality) {
    public CapitalBase {
        if (investedTradableAssets.signum() < 0
                || trackedCash.signum() < 0
                || emergencyReserve.signum() < 0
                || deployableCash.signum() < 0
                || investableAssets.signum() < 0
                || totalLiquidAssets.signum() < 0
                || unvestedCompensationValue.signum() < 0) {
            throw new IllegalArgumentException("Capital values cannot be negative");
        }
        if (emergencyReserve.compareTo(trackedCash) > 0) {
            throw new IllegalArgumentException("Emergency reserve cannot exceed tracked cash");
        }
    }
}
