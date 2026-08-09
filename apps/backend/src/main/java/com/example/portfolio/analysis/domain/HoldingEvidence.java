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
        Thesis thesis,
        MarketRegimeSnapshot regime,
        PortfolioDrawdownSnapshot drawdown,
        StopEvidence stop,
        AnalysisProfile profile,
        EvidenceQuality quality,
        StrategyDefinition strategy,
        Instant dataAsOf) {
    public HoldingEvidence {
        completedBars = List.copyOf(completedBars);
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

    public record PriceBar(LocalDate marketDate, BigDecimal close, boolean completed) {}

    public record IndicatorSet(boolean trendAvailable, boolean aboveTrend, Double rsi, Double atr) {}

    public record FundamentalSnapshot(boolean available, EvidenceQuality quality, Instant dataAsOf) {}

    public record ValuationSnapshot(boolean available, Instant dataAsOf) {}

    public record EarningsEvent(boolean available, Instant eventAt, String riskLevel) {}

    public record Thesis(boolean available, boolean invalidated, Instant expiresAt) {}

    public record MarketRegimeSnapshot(boolean available, String label, Instant dataAsOf) {}

    public record PortfolioDrawdownSnapshot(
            boolean available, BigDecimal drawdown, String state, boolean noNewRisk, Instant dataAsOf) {}

    public record StopEvidence(
            BigDecimal formalStop, BigDecimal liveStop, boolean closeConfirmed, boolean catastrophic) {}

    public record AnalysisProfile(
            boolean fundProfileAvailable,
            boolean thematic,
            BigDecimal topHoldingConcentration,
            String liquidityStatus,
            BigDecimal portfolioOverlap) {}
}
