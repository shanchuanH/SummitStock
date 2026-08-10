package com.example.portfolio.portfolioimport.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PortfolioImportPreview(
        UUID batchId,
        ImportBatchStatus status,
        long version,
        List<ImportAccount> accounts,
        List<ImportedHolding> holdings,
        List<ImportedCash> cash,
        List<String> warnings,
        List<String> errors,
        Summary summary,
        Instant dataAsOf) {
    public record ImportAccount(String accountName, String accountNumberMasked) {}

    public record Summary(
            int rowCount,
            int validRowCount,
            int errorRowCount,
            BigDecimal estimatedInvestedValue,
            BigDecimal estimatedCashValue) {}
}
