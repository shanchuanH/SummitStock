package com.example.portfolio.analysis.application;

import com.example.portfolio.analysis.domain.HoldingEvidence;
import com.example.portfolio.analysis.domain.StrategyDefinition;
import com.example.portfolio.strategy.market.EvidenceQuality;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

final class HoldingEvidenceFixtures {
    private HoldingEvidenceFixtures() {}

    static HoldingEvidence evidence(String symbol, String assetType, HoldingClassification classification) {
        var now = Instant.parse("2026-08-05T20:00:00Z");
        var positionId = UUID.nameUUIDFromBytes(symbol.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var strategy = new StrategyDefinition(
                "3.0.0-draft",
                "a".repeat(64),
                "DRAFT",
                new BigDecimal("20000"),
                true,
                new BigDecimal("0.20"),
                new BigDecimal("0.005"),
                new BigDecimal("0.02"),
                new BigDecimal("0.0075"),
                48,
                3,
                false,
                new BigDecimal("0.25"),
                new BigDecimal("0.35"),
                new BigDecimal("0.15"),
                "VOO",
                "QQQM",
                policy("0.04", "0.08", "0.12", "0.15", "0.004"),
                policy("0.05", "0.08", "0.10", "0.10", "0.003"),
                policy("0.02", "0.03", "0.05", "0.05", "0.003"),
                policy("0.005", "0.01", "0.02", "0.02", "0.002"),
                new StrategyDefinition.EtfDipPolicy(
                        60,
                        2,
                        List.of(
                                new BigDecimal("0.20"),
                                new BigDecimal("0.25"),
                                new BigDecimal("0.30"),
                                new BigDecimal("0.25")),
                        5,
                        true),
                new StrategyDefinition.FinancialHealthPolicy(
                        new BigDecimal("0.15"),
                        new BigDecimal("0.05"),
                        new BigDecimal("0.03"),
                        new BigDecimal("0.10"),
                        new BigDecimal("0.03"),
                        new BigDecimal("3.0")));
        return new HoldingEvidence(
                new HoldingEvidence.Position(
                        positionId,
                        UUID.randomUUID(),
                        classification,
                        true,
                        BigDecimal.TEN,
                        new BigDecimal("100"),
                        new BigDecimal("1200")),
                new HoldingEvidence.Instrument(UUID.randomUUID(), symbol, assetType, true),
                money("100000"),
                money("25000"),
                money("20000"),
                money("5000"),
                new BigDecimal("0.012"),
                new BigDecimal("0.08"),
                new BigDecimal("0.002"),
                new BigDecimal("0.01"),
                new HoldingEvidence.LatestQuote(new BigDecimal("120"), now, EvidenceQuality.HEALTHY),
                List.of(new HoldingEvidence.PriceBar(LocalDate.of(2026, 8, 5), new BigDecimal("120"), true)),
                new HoldingEvidence.IndicatorSet(true, true, 55.0, 3.0),
                new HoldingEvidence.FundamentalSnapshot(
                        true, EvidenceQuality.HEALTHY, now, "HEALTHY", "FLAT", EvidenceQuality.HEALTHY, now),
                new HoldingEvidence.ValuationSnapshot(true, now),
                new HoldingEvidence.EarningsEvent(true, now.plusSeconds(86400 * 20L), "NORMAL", null, now),
                new HoldingEvidence.Thesis(true, false, now.plusSeconds(86400 * 90L)),
                new HoldingEvidence.MarketRegimeSnapshot(true, "HEALTHY", now),
                new HoldingEvidence.PortfolioDrawdownSnapshot(true, new BigDecimal("0.05"), "NORMAL", false, now),
                new HoldingEvidence.StopEvidence(new BigDecimal("105"), new BigDecimal("106"), false, false, now),
                new HoldingEvidence.AnalysisProfile(
                        true, assetType.equals("ETF"), new BigDecimal("0.10"), "HEALTHY", new BigDecimal("0.05"), now),
                EvidenceQuality.HEALTHY,
                EvidenceQuality.HEALTHY,
                now,
                false,
                EvidenceQuality.HEALTHY,
                strategy,
                now);
    }

    static HoldingEvidence withFundamentals(HoldingEvidence value, boolean available) {
        return new HoldingEvidence(
                value.position(),
                value.instrument(),
                value.portfolioEquity(),
                value.trackedCash(),
                value.emergencyCash(),
                value.tacticalReserve(),
                value.currentWeight(),
                value.clusterWeight(),
                value.clusterOpenRisk(),
                value.totalOpenRisk(),
                value.quote(),
                value.completedBars(),
                value.indicators(),
                new HoldingEvidence.FundamentalSnapshot(available, EvidenceQuality.MISSING, value.dataAsOf()),
                value.valuation(),
                value.nextEvent(),
                value.thesis(),
                value.regime(),
                value.drawdown(),
                value.stop(),
                value.profile(),
                value.capitalQuality(),
                value.riskQuality(),
                value.riskDataAsOf(),
                value.providerHardError(),
                value.quality(),
                value.strategy(),
                value.dataAsOf());
    }

    static HoldingEvidence withEvent(HoldingEvidence value, boolean available) {
        return new HoldingEvidence(
                value.position(),
                value.instrument(),
                value.portfolioEquity(),
                value.trackedCash(),
                value.emergencyCash(),
                value.tacticalReserve(),
                value.currentWeight(),
                value.clusterWeight(),
                value.clusterOpenRisk(),
                value.totalOpenRisk(),
                value.quote(),
                value.completedBars(),
                value.indicators(),
                value.fundamentals(),
                value.valuation(),
                new HoldingEvidence.EarningsEvent(available, null, null),
                value.thesis(),
                value.regime(),
                value.drawdown(),
                value.stop(),
                value.profile(),
                value.capitalQuality(),
                value.riskQuality(),
                value.riskDataAsOf(),
                value.providerHardError(),
                value.quality(),
                value.strategy(),
                value.dataAsOf());
    }

    private static HoldingEvidence.Money money(String value) {
        return new HoldingEvidence.Money(new BigDecimal(value), "USD");
    }

    private static StrategyDefinition.PositionPolicy policy(
            String min, String max, String normal, String hard, String risk) {
        return new StrategyDefinition.PositionPolicy(
                new BigDecimal(min),
                new BigDecimal(max),
                new BigDecimal(normal),
                new BigDecimal(hard),
                new BigDecimal(risk));
    }
}
