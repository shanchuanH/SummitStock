package com.example.portfolio.market.provider;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class ProviderModels {
    private ProviderModels() {}

    public enum QualityStatus {
        VALID,
        PARTIAL,
        STALE,
        MALFORMED
    }

    public record Provenance(
            String provider,
            Instant sourceTimestamp,
            Instant fetchedAt,
            String checksum,
            String normalizationVersion,
            QualityStatus qualityStatus,
            List<String> warnings) {
        public Provenance {
            warnings = List.copyOf(warnings);
        }

        public boolean freshAt(Instant cutoff) {
            return !sourceTimestamp.isBefore(cutoff) && qualityStatus == QualityStatus.VALID;
        }
    }

    public record DailyBar(
            LocalDate marketDate,
            BigDecimal open,
            BigDecimal high,
            BigDecimal low,
            BigDecimal close,
            BigDecimal volume,
            boolean adjusted,
            boolean completed) {}

    public record DailyBarsResult(String symbol, List<DailyBar> bars, Provenance provenance) {
        public DailyBarsResult {
            bars = List.copyOf(bars);
        }
    }

    public record QuoteResult(
            String symbol, BigDecimal bid, BigDecimal ask, BigDecimal last, String currency, Provenance provenance) {}

    public record CorporateAction(
            String type,
            LocalDate exDate,
            LocalDate effectiveDate,
            BigDecimal ratio,
            BigDecimal cashAmount,
            String currency) {}

    public record CorporateActionsResult(String symbol, List<CorporateAction> actions, Provenance provenance) {
        public CorporateActionsResult {
            actions = List.copyOf(actions);
        }
    }

    public record Filing(String accessionNumber, String form, LocalDate filingDate, String sourceUrl) {}

    public record FilingIndexResult(String cik, List<Filing> filings, Provenance provenance) {
        public FilingIndexResult {
            filings = List.copyOf(filings);
        }
    }

    public record CompanyFactsResult(String cik, Map<String, String> facts, Provenance provenance) {
        public CompanyFactsResult {
            facts = Map.copyOf(facts);
        }
    }
}
