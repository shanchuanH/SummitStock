package com.example.portfolio.analysis.allocation;

import com.example.portfolio.strategy.market.EvidenceQuality;
import java.math.BigDecimal;

public record SleeveAllocation(
        PortfolioSleeve sleeve,
        BigDecimal markedMarketValue,
        BigDecimal currentWeight,
        BigDecimal targetWeight,
        BigDecimal gapWeight,
        String primaryInstrument,
        boolean primaryInstrumentPosition,
        EvidenceQuality quality) {}
