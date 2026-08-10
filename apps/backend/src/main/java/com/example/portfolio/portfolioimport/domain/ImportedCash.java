package com.example.portfolio.portfolioimport.domain;

import java.math.BigDecimal;
import java.util.List;

public record ImportedCash(
        int rowNumber,
        String accountName,
        String accountNumberMasked,
        String symbol,
        String description,
        BigDecimal currentValue,
        ImportRowStatus status,
        List<String> warnings) {}
