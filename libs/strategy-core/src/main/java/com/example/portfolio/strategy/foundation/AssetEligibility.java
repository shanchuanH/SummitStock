package com.example.portfolio.strategy.foundation;

import java.math.BigDecimal;
import java.util.List;

public record AssetEligibility(
        boolean liquid, boolean tradable, boolean recommendationAllowed, BigDecimal liquidValue, List<String> ruleIds) {

    public AssetEligibility {
        liquidValue = liquidValue.stripTrailingZeros();
        ruleIds = List.copyOf(ruleIds);
    }
}
