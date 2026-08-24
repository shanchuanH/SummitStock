package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.analysis.domain.AnalysisReadiness;
import com.example.portfolio.analysis.domain.HoldingEvidence;
import com.example.portfolio.strategy.market.EvidenceQuality;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class HoldingEvidenceReadinessTest {
    private final AnalysisFreshnessPolicy freshness = new AnalysisFreshnessPolicy();

    @Test
    void missingQualityFundamentalsAllowsMaintenanceAnalysisButNotReadyAnalysis() {
        var googl = HoldingEvidenceFixtures.withFundamentals(
                HoldingEvidenceFixtures.evidence("GOOGL", "EQUITY", HoldingClassification.QUALITY_STOCK), false);
        var dxyz = HoldingEvidenceFixtures.withFundamentals(
                HoldingEvidenceFixtures.evidence("DXYZ", "EQUITY", HoldingClassification.SPECULATIVE), false);
        assertThat(HoldingEvidenceReadiness.assess(googl, googl.dataAsOf(), freshness))
                .isEqualTo(AnalysisReadiness.PARTIAL);
        assertThat(HoldingEvidenceReadiness.assess(dxyz, dxyz.dataAsOf(), freshness))
                .isEqualTo(AnalysisReadiness.PARTIAL);
    }

    @Test
    void qualityStockWithCoreFundamentalsButNoRevisionHistoryRemainsUsableAndFailClosed() {
        var current = HoldingEvidenceFixtures.evidence("GOOGL", "EQUITY", HoldingClassification.QUALITY_STOCK);
        var missingRevisions = new HoldingEvidence.FundamentalSnapshot(
                true,
                EvidenceQuality.HEALTHY,
                current.fundamentals().dataAsOf(),
                "STRONG",
                "MISSING",
                EvidenceQuality.MISSING,
                null);
        var evidence = withFundamentalsAndEvent(current, missingRevisions, current.nextEvent());

        assertThat(HoldingEvidenceReadiness.assess(evidence, current.dataAsOf(), freshness))
                .isEqualTo(AnalysisReadiness.PARTIAL);
    }

    @Test
    void qualityStockWithCoreFundamentalsButNoEarningsCalendarRemainsUsableAndFailClosed() {
        var current = HoldingEvidenceFixtures.evidence("GOOGL", "EQUITY", HoldingClassification.QUALITY_STOCK);
        var evidence = withFundamentalsAndEvent(
                current, current.fundamentals(), new HoldingEvidence.EarningsEvent(false, null, null));

        assertThat(HoldingEvidenceReadiness.assess(evidence, current.dataAsOf(), freshness))
                .isEqualTo(AnalysisReadiness.PARTIAL);
    }

    @Test
    void freshQuoteDoesNotHideStaleFundamentals() {
        var current = HoldingEvidenceFixtures.evidence("GOOGL", "EQUITY", HoldingClassification.QUALITY_STOCK);
        var staleFundamentals = new HoldingEvidence.FundamentalSnapshot(
                true,
                EvidenceQuality.HEALTHY,
                current.dataAsOf().minus(Duration.ofDays(250)),
                "HEALTHY",
                "UP",
                EvidenceQuality.HEALTHY,
                current.dataAsOf());
        var evidence = new HoldingEvidence(
                current.position(),
                current.instrument(),
                current.portfolioEquity(),
                current.trackedCash(),
                current.emergencyCash(),
                current.tacticalReserve(),
                current.currentWeight(),
                current.clusterWeight(),
                current.clusterOpenRisk(),
                current.totalOpenRisk(),
                current.quote(),
                current.completedBars(),
                current.indicators(),
                staleFundamentals,
                current.valuation(),
                current.nextEvent(),
                current.catalyst(),
                current.thesis(),
                current.regime(),
                current.drawdown(),
                current.stop(),
                current.profile(),
                current.capitalQuality(),
                current.riskQuality(),
                current.riskDataAsOf(),
                current.providerHardError(),
                current.quality(),
                current.strategy(),
                current.dataAsOf());

        assertThat(HoldingEvidenceReadiness.assess(evidence, current.dataAsOf(), freshness))
                .isEqualTo(AnalysisReadiness.STALE);
    }

    @Test
    void qualityRiskMissingIsPartialRatherThanReadyOrUnknownAsZero() {
        var current = HoldingEvidenceFixtures.evidence("GOOGL", "EQUITY", HoldingClassification.QUALITY_STOCK);
        var evidence = withRisk(current, null, null, EvidenceQuality.MISSING, null);

        assertThat(HoldingEvidenceReadiness.assess(evidence, current.dataAsOf(), freshness))
                .isEqualTo(AnalysisReadiness.PARTIAL);
    }

    @Test
    void tacticalReadinessRequiresCatalystStopEventRiskThesisAndCapacity() {
        var ready = HoldingEvidenceFixtures.evidence("NOK", "EQUITY", HoldingClassification.TACTICAL_STOCK);
        var missingCatalyst =
                tactical(ready, HoldingEvidence.CatalystEvidence.missing(), ready.stop(), ready.riskDataAsOf());
        var missingStop = tactical(
                ready,
                ready.catalyst(),
                new HoldingEvidence.StopEvidence(null, null, false, false, ready.dataAsOf()),
                ready.riskDataAsOf());
        var staleRisk =
                tactical(ready, ready.catalyst(), ready.stop(), ready.dataAsOf().minus(Duration.ofDays(10)));

        assertThat(HoldingEvidenceReadiness.assess(ready, ready.dataAsOf(), freshness))
                .isEqualTo(AnalysisReadiness.READY);
        assertThat(HoldingEvidenceReadiness.assess(missingCatalyst, ready.dataAsOf(), freshness))
                .isEqualTo(AnalysisReadiness.WAIT_FOR_CATALYST);
        assertThat(HoldingEvidenceReadiness.assess(missingStop, ready.dataAsOf(), freshness))
                .isEqualTo(AnalysisReadiness.BLOCKED);
        assertThat(HoldingEvidenceReadiness.assess(staleRisk, ready.dataAsOf(), freshness))
                .isEqualTo(AnalysisReadiness.STALE);
    }

    @Test
    void importedTacticalHoldingWithoutOwnerThesisWaitsInsteadOfClaimingInvalidation() {
        var current = HoldingEvidenceFixtures.evidence("AAOI", "EQUITY", HoldingClassification.TACTICAL_STOCK);
        var evidence = new HoldingEvidence(
                current.position(),
                current.instrument(),
                current.portfolioEquity(),
                current.trackedCash(),
                current.emergencyCash(),
                current.tacticalReserve(),
                current.currentWeight(),
                current.clusterWeight(),
                current.clusterOpenRisk(),
                current.totalOpenRisk(),
                current.quote(),
                current.completedBars(),
                current.indicators(),
                current.fundamentals(),
                current.valuation(),
                current.nextEvent(),
                current.catalyst(),
                new HoldingEvidence.Thesis(false, false, null),
                current.regime(),
                current.drawdown(),
                current.stop(),
                current.profile(),
                current.capitalQuality(),
                current.riskQuality(),
                current.riskDataAsOf(),
                current.providerHardError(),
                current.quality(),
                current.strategy(),
                current.dataAsOf());

        assertThat(HoldingEvidenceReadiness.assess(evidence, current.dataAsOf(), freshness))
                .isEqualTo(AnalysisReadiness.WAIT_FOR_CATALYST);
    }

    private static HoldingEvidence tactical(
            HoldingEvidence value,
            HoldingEvidence.CatalystEvidence catalyst,
            HoldingEvidence.StopEvidence stop,
            Instant riskDataAsOf) {
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
                value.nextEvent(),
                catalyst,
                value.thesis(),
                value.regime(),
                value.drawdown(),
                stop,
                value.profile(),
                value.capitalQuality(),
                value.riskQuality(),
                riskDataAsOf,
                value.providerHardError(),
                value.quality(),
                value.strategy(),
                value.dataAsOf());
    }

    private static HoldingEvidence withFundamentalsAndEvent(
            HoldingEvidence value,
            HoldingEvidence.FundamentalSnapshot fundamentals,
            HoldingEvidence.EarningsEvent nextEvent) {
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
                fundamentals,
                value.valuation(),
                nextEvent,
                value.catalyst(),
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

    private static HoldingEvidence withRisk(
            HoldingEvidence value,
            java.math.BigDecimal clusterRisk,
            java.math.BigDecimal totalRisk,
            EvidenceQuality riskQuality,
            Instant riskDataAsOf) {
        return new HoldingEvidence(
                value.position(),
                value.instrument(),
                value.portfolioEquity(),
                value.trackedCash(),
                value.emergencyCash(),
                value.tacticalReserve(),
                value.currentWeight(),
                value.clusterWeight(),
                clusterRisk,
                totalRisk,
                value.quote(),
                value.completedBars(),
                value.indicators(),
                value.fundamentals(),
                value.valuation(),
                value.nextEvent(),
                value.catalyst(),
                value.thesis(),
                value.regime(),
                value.drawdown(),
                value.stop(),
                value.profile(),
                value.capitalQuality(),
                riskQuality,
                riskDataAsOf,
                value.providerHardError(),
                value.quality(),
                value.strategy(),
                value.dataAsOf());
    }
}
