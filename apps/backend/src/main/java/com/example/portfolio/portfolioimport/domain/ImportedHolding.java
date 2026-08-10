package com.example.portfolio.portfolioimport.domain;

import java.math.BigDecimal;
import java.util.List;

public record ImportedHolding(
        int rowNumber,
        String accountName,
        String accountNumberMasked,
        String symbol,
        String description,
        String assetType,
        BigDecimal quantity,
        BigDecimal lastPrice,
        BigDecimal currentValue,
        BigDecimal averageCost,
        BigDecimal costBasis,
        String rowType,
        ImportRowStatus status,
        List<String> warnings,
        String rawJson) {}
