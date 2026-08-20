package com.example.portfolio.analysis.domain;

import com.example.portfolio.strategy.market.EvidenceQuality;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record HoldingEvidence(
        Position position,
        Instrument instrument,
        Money portfolioEquity,
        Money trackedCash,
        Money emergencyCash,
        Money tacticalReserve,
        BigDecimal currentWeight,
        BigDecimal clusterWeight,
        BigDecimal clusterOpenRisk,
        BigDecimal totalOpenRisk,
        LatestQuote quote,
        List<PriceBar> completedBars,
        IndicatorSet indicators,
        FundamentalSnapshot fundamentals,
        ValuationSnapshot valuation,
        EarningsEvent nextEvent,
        CatalystEvidence catalyst,
        Thesis thesis,
        MarketRegimeSnapshot regime,
        PortfolioDrawdownSnapshot drawdown,
        StopEvidence stop,
        AnalysisProfile profile,
        EvidenceQuality capitalQuality,
        EvidenceQuality riskQuality,
        Instant riskDataAsOf,
        boolean providerHardError,
        EvidenceQuality quality,
        StrategyDefinition strategy,
        Instant dataAsOf) {
    public HoldingEvidence {
        completedBars = List.copyOf(completedBars);
        catalyst = catalyst == null ? CatalystEvidence.missing() : catalyst;
    }

    public record Position(
            UUID id,
            UUID userId,
            HoldingClassification classification,
            boolean classificationConfirmed,
            BigDecimal quantity,
            BigDecimal averageCost,
            BigDecimal marketValue) {}

    public record Instrument(UUID id, String symbol, String assetType, boolean active) {}

    public record Money(BigDecimal amount, String currency) {}

    public record LatestQuote(
            BigDecimal last,
            Instant dataAsOf,
            LocalDate marketDate,
            EvidenceQuality quality,
            EvidenceQuality executionLiquidityQuality) {
        public LatestQuote(BigDecimal last, Instant dataAsOf, EvidenceQuality quality) {
            this(
                    last,
                    dataAsOf,
                    dataAsOf == null
                            ? null
                            : dataAsOf.atZone(java.time.ZoneOffset.UTC).toLocalDate(),
                    quality,
                    EvidenceQuality.MISSING);
        }

        public boolean available() {
            return last != null && dataAsOf != null;
        }
    }

    public record PriceBar(LocalDate marketDate, BigDecimal close, BigDecimal volume, boolean completed) {
        public PriceBar(LocalDate marketDate, BigDecimal close, boolean completed) {
            this(marketDate, close, null, completed);
        }
    }

    public record IndicatorSet(boolean trendAvailable, boolean aboveTrend, Double rsi, Double atr, String priceState) {
        public IndicatorSet(boolean trendAvailable, boolean aboveTrend, Double rsi, Double atr) {
            this(trendAvailable, aboveTrend, rsi, atr, aboveTrend ? "UPTREND" : "DOWNTREND");
        }
    }

    public record FundamentalSnapshot(
            boolean available,
            EvidenceQuality quality,
            Instant dataAsOf,
            String financialHealth,
            String estimateRevision,
            EvidenceQuality estimateQuality,
            Instant estimateDataAsOf) {
        public FundamentalSnapshot(
                boolean available,
                EvidenceQuality quality,
                Instant dataAsOf,
                String financialHealth,
                String estimateRevision,
                EvidenceQuality estimateQuality) {
            this(available, quality, dataAsOf, financialHealth, estimateRevision, estimateQuality, dataAsOf);
        }

        public FundamentalSnapshot(boolean available, EvidenceQuality quality, Instant dataAsOf) {
            this(
                    available,
                    quality,
                    dataAsOf,
                    available ? "HEALTHY" : "MISSING",
                    "MISSING",
                    EvidenceQuality.MISSING,
                    dataAsOf);
        }
    }

    public record ValuationSnapshot(
            boolean available,
            String state,
            String confidence,
            int observationCount,
            int priorStarterCount,
            boolean independentConfirmation,
            Instant dataAsOf) {
        public ValuationSnapshot(boolean available, Instant dataAsOf) {
            this(available, available ? "FAIR" : "MISSING", available ? "LOW" : "MISSING", 0, 0, false, dataAsOf);
        }
    }

    public record EarningsEvent(
            boolean available, Instant eventAt, String eventRisk, String policyAction, Instant dataAsOf) {
        public EarningsEvent(boolean available, Instant eventAt, String eventRisk, String policyAction) {
            this(available, eventAt, eventRisk, policyAction, eventAt);
        }

        public EarningsEvent(boolean available, Instant eventAt, String eventRisk) {
            this(available, eventAt, eventRisk, null, eventAt);
        }
    }

    public record CatalystEvidence(
            boolean available,
            CatalystStatus status,
            CatalystType type,
            String summary,
            String source,
            Instant dataAsOf,
            LocalDate expectedWindowStart,
            LocalDate expectedWindowEnd,
            String invalidation) {
        public static CatalystEvidence missing() {
            return new CatalystEvidence(false, CatalystStatus.MISSING, null, null, null, null, null, null, null);
        }

        public boolean confirmedAt(Instant decisionAt) {
            var decisionDate = decisionAt.atZone(java.time.ZoneOffset.UTC).toLocalDate();
            return available
                    && status == CatalystStatus.CONFIRMED
                    && type != null
                    && summary != null
                    && !summary.isBlank()
                    && source != null
                    && !source.isBlank()
                    && dataAsOf != null
                    && (expectedWindowEnd == null || !expectedWindowEnd.isBefore(decisionDate))
                    && invalidation != null
                    && !invalidation.isBlank();
        }
    }

    public enum CatalystStatus {
        CONFIRMED,
        DEVELOPING,
        MISSING,
        INVALIDATED
    }

    public enum CatalystType {
        EARNINGS_INFLECTION,
        PRODUCT_CYCLE,
        ORDER_WIN,
        PRICING_RECOVERY,
        INDUSTRY_RECOVERY,
        MARGIN_INFLECTION,
        BALANCE_SHEET_REPAIR,
        MANAGEMENT_CHANGE,
        REGULATORY,
        OTHER
    }

    public record Thesis(boolean available, boolean invalidated, Instant expiresAt) {}

    public record MarketRegimeSnapshot(boolean available, String label, Instant dataAsOf) {}

    public record PortfolioDrawdownSnapshot(
            boolean available, BigDecimal drawdown, String state, boolean noNewRisk, Instant dataAsOf) {}

    public record StopEvidence(
            BigDecimal formalStop,
            BigDecimal liveStop,
            boolean closeConfirmed,
            boolean catastrophic,
            Instant dataAsOf) {
        public StopEvidence(BigDecimal formalStop, BigDecimal liveStop, boolean closeConfirmed, boolean catastrophic) {
            this(formalStop, liveStop, closeConfirmed, catastrophic, null);
        }
    }

    public record AnalysisProfile(
            boolean fundProfileAvailable,
            boolean thematic,
            BigDecimal topHoldingConcentration,
            String liquidityStatus,
            BigDecimal portfolioOverlap,
            Instant dataAsOf) {
        public AnalysisProfile(
                boolean fundProfileAvailable,
                boolean thematic,
                BigDecimal topHoldingConcentration,
                String liquidityStatus,
                BigDecimal portfolioOverlap) {
            this(fundProfileAvailable, thematic, topHoldingConcentration, liquidityStatus, portfolioOverlap, null);
        }
    }
}
