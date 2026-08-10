package com.example.portfolio.market.provider;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ProviderModels {
    private ProviderModels() {}

    public enum QualityStatus {
        HEALTHY,
        PARTIAL,
        STALE,
        SUSPECT,
        MISSING
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
            return !sourceTimestamp.isBefore(cutoff) && qualityStatus == QualityStatus.HEALTHY;
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
            String symbol,
            BigDecimal bid,
            BigDecimal ask,
            BigDecimal last,
            String currency,
            DecisionPriceEvidence decisionPrice,
            ExecutionLiquidityEvidence executionLiquidity,
            Provenance provenance) {
        public QuoteResult(
                String symbol,
                BigDecimal bid,
                BigDecimal ask,
                BigDecimal last,
                String currency,
                Provenance provenance) {
            this(
                    symbol,
                    bid,
                    ask,
                    last,
                    currency,
                    new DecisionPriceEvidence(
                            last,
                            provenance
                                    .sourceTimestamp()
                                    .atZone(java.time.ZoneOffset.UTC)
                                    .toLocalDate(),
                            provenance.qualityStatus()),
                    ExecutionLiquidityEvidence.from(bid, ask, provenance.qualityStatus()),
                    provenance);
        }
    }

    public record DecisionPriceEvidence(BigDecimal price, LocalDate marketDate, QualityStatus quality) {}

    public record ExecutionLiquidityEvidence(
            BigDecimal bid, BigDecimal ask, BigDecimal spreadFraction, QualityStatus quality) {
        static ExecutionLiquidityEvidence from(
                BigDecimal bid, BigDecimal ask, ProviderModels.QualityStatus sourceQuality) {
            if (bid == null || ask == null) {
                return new ExecutionLiquidityEvidence(bid, ask, null, QualityStatus.MISSING);
            }
            if (bid.signum() <= 0 || ask.compareTo(bid) < 0) {
                return new ExecutionLiquidityEvidence(bid, ask, null, QualityStatus.SUSPECT);
            }
            var midpoint = bid.add(ask).divide(BigDecimal.TWO, java.math.MathContext.DECIMAL64);
            var spread = ask.subtract(bid).divide(midpoint, java.math.MathContext.DECIMAL64);
            return new ExecutionLiquidityEvidence(bid, ask, spread, sourceQuality);
        }
    }

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

    public record Filing(
            String accessionNumber,
            String form,
            LocalDate filingDate,
            LocalDate periodEnd,
            String sourceUrl,
            String primaryDocument) {}

    public record FilingIndexResult(String cik, List<Filing> filings, Provenance provenance) {
        public FilingIndexResult {
            filings = List.copyOf(filings);
        }
    }

    public record CompanyFact(
            String businessMetric,
            String taxonomy,
            String concept,
            String unit,
            BigDecimal value,
            LocalDate periodStart,
            LocalDate periodEnd,
            LocalDate filingDate,
            String accessionNumber,
            String form,
            String sourceUri,
            Integer fiscalYear,
            String fiscalPeriod) {
        public CompanyFact(
                String businessMetric,
                String taxonomy,
                String concept,
                String unit,
                BigDecimal value,
                LocalDate periodStart,
                LocalDate periodEnd,
                LocalDate filingDate,
                String accessionNumber,
                String form,
                String sourceUri) {
            this(
                    businessMetric,
                    taxonomy,
                    concept,
                    unit,
                    value,
                    periodStart,
                    periodEnd,
                    filingDate,
                    accessionNumber,
                    form,
                    sourceUri,
                    null,
                    null);
        }
    }

    public record CompanyFactsResult(String cik, List<CompanyFact> facts, Provenance provenance) {
        public CompanyFactsResult {
            facts = List.copyOf(facts);
        }
    }
}
