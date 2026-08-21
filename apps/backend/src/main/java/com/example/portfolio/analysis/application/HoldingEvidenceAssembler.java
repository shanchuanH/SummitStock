package com.example.portfolio.analysis.application;

import com.example.portfolio.analysis.capital.CapitalBaseService;
import com.example.portfolio.analysis.domain.HoldingEvidence;
import com.example.portfolio.analysis.domain.StrategyDefinition;
import com.example.portfolio.analysis.replay.DecisionAsOfContext;
import com.example.portfolio.analysis.risk.ClusterRiskService;
import com.example.portfolio.strategy.market.EvidenceQuality;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public final class HoldingEvidenceAssembler {
    private final JdbcClient jdbc;
    private final PublishedStrategyService strategies;
    private final CapitalBaseService capitalBases;
    private final ClusterRiskService clusterRisks;
    private final Clock clock;

    public HoldingEvidenceAssembler(
            JdbcClient jdbc,
            PublishedStrategyService strategies,
            CapitalBaseService capitalBases,
            ClusterRiskService clusterRisks,
            Clock clock) {
        this.jdbc = jdbc;
        this.strategies = strategies;
        this.capitalBases = capitalBases;
        this.clusterRisks = clusterRisks;
        this.clock = clock;
    }

    public List<HoldingEvidence> assembleAll(UUID userId) {
        var strategy = strategies.current();
        var context = new DecisionAsOfContext(LocalDate.now(clock), clock.instant(), strategy.version());
        return assembleAll(userId, context);
    }

    StrategyDefinition currentStrategy() {
        return strategies.current();
    }

    public List<HoldingEvidence> assembleAll(UUID userId, DecisionAsOfContext context) {
        return positions(userId).stream()
                .map(position -> assemble(position, context))
                .toList();
    }

    public HoldingEvidence assemble(UUID userId, UUID positionId) {
        var strategy = strategies.current();
        var context = new DecisionAsOfContext(LocalDate.now(clock), clock.instant(), strategy.version());
        return positions(userId).stream()
                .filter(value -> value.positionId().equals(positionId))
                .findFirst()
                .map(position -> assemble(position, context))
                .orElseThrow(() -> new IllegalArgumentException("Position is not owned by user"));
    }

    private HoldingEvidence assemble(PositionRow position, DecisionAsOfContext context) {
        var totals = totals(position.userId());
        var capital = capitalBases.calculate(position.userId());
        var risk = riskEvidence(position.userId());
        var investable = capital.investableAssets();
        var currentWeight = investable.signum() == 0
                ? BigDecimal.ZERO
                : position.marketValue().divide(investable, MathContext.DECIMAL64);
        var cluster = cluster(position.positionId(), investable);
        var clusterRisk = clusterRisks.forPosition(position.positionId());
        var quote = quote(position.instrumentId(), context);
        var bars = bars(position.instrumentId(), context);
        var indicators = indicators(position.instrumentId(), bars, context);
        var fundamentals = fundamentals(position.instrumentId(), context);
        var valuation = valuation(position.positionId(), position.instrumentId(), context);
        var event = event(position.positionId(), position.instrumentId(), context);
        var catalyst = catalyst(position.positionId(), context);
        var thesis = thesis(position.positionId(), context);
        var regime = regime(context);
        var drawdown = drawdown(position.userId(), context);
        var stop = stop(position.positionId(), context);
        var profile = profile(position.instrumentId(), context);
        var classification = classification(position.classification());
        var quality =
                switch (classification) {
                    case QUALITY_STOCK, QUALITY_GROWTH_HIGH_VOL -> quality(quote.quality(), fundamentals.quality());
                    default -> quote.quality();
                };
        var dataAsOf = quote.dataAsOf() == null
                ? bars.stream()
                        .map(HoldingEvidence.PriceBar::marketDate)
                        .max(LocalDate::compareTo)
                        .map(date -> date.atStartOfDay().toInstant(ZoneOffset.UTC))
                        .orElse(Instant.EPOCH)
                : quote.dataAsOf();
        return new HoldingEvidence(
                new HoldingEvidence.Position(
                        position.positionId(),
                        position.userId(),
                        classification,
                        position.classificationConfirmed(),
                        position.quantity(),
                        position.averageCost(),
                        position.marketValue()),
                new HoldingEvidence.Instrument(
                        position.instrumentId(), position.symbol(), position.assetType(), position.active()),
                money(investable),
                money(capital.trackedCash()),
                money(capital.emergencyReserve()),
                money(totals.tactical()),
                currentWeight,
                cluster.weight(),
                clusterRisk.quality() == EvidenceQuality.HEALTHY ? clusterRisk.openRiskFraction() : null,
                totals.openRisk(),
                quote,
                bars,
                indicators,
                fundamentals,
                valuation,
                event,
                catalyst,
                thesis,
                regime,
                new HoldingEvidence.PortfolioDrawdownSnapshot(
                        drawdown.available(),
                        drawdown.fraction(),
                        drawdown.state(),
                        drawdown.available()
                                && drawdown.fraction()
                                                .compareTo(strategies.current().painLine())
                                        >= 0,
                        instant(drawdown.dataAsOf())),
                stop,
                profile,
                capital.quality(),
                risk.quality(),
                risk.dataAsOf(),
                providerHardError(position.instrumentId()),
                quality,
                strategies.current(),
                dataAsOf);
    }

    private List<PositionRow> positions(UUID userId) {
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(p.id) positionId, BIN_TO_UUID(a.user_id) userId,
                               BIN_TO_UUID(i.id) instrumentId, i.symbol, i.asset_type assetType, i.active,
                               p.classification, p.classification_confirmed classificationConfirmed,
                               p.quantity, p.average_cost averageCost,
                               COALESCE(m.marked_market_value,0) marketValue
                        FROM position p JOIN investment_account a ON a.id=p.account_id
                        JOIN instrument i ON i.id=p.instrument_id
                        LEFT JOIN current_position_mark m ON m.position_id=p.id
                        WHERE a.user_id=UUID_TO_BIN(:userId) AND p.status='OPEN'
                        ORDER BY i.symbol, p.id
                        """)
                .param("userId", userId.toString())
                .query(PositionRow.class)
                .list();
    }

    private PortfolioTotals totals(UUID userId) {
        return jdbc.sql(
                        """
                        SELECT COALESCE((SELECT SUM(current_amount) FROM cash_bucket
                                         WHERE user_id=UUID_TO_BIN(:userId) AND bucket_type='TACTICAL_RESERVE'),0) tactical,
                               (SELECT SUM(r.open_risk_fraction) FROM position_risk_snapshot r
                                         JOIN position x ON x.id=r.position_id JOIN investment_account z ON z.id=x.account_id
                                         WHERE z.user_id=UUID_TO_BIN(:userId)
                                           AND r.data_as_of=(SELECT MAX(q.data_as_of) FROM position_risk_snapshot q
                                                             WHERE q.position_id=r.position_id)) openRisk
                        FROM app_user u WHERE u.id=UUID_TO_BIN(:userId)
                        """)
                .param("userId", userId.toString())
                .query(PortfolioTotals.class)
                .single();
    }

    private ClusterEvidence cluster(UUID positionId, BigDecimal liquid) {
        var value = jdbc.sql(
                        """
                        SELECT COALESCE(SUM(marked.marked_market_value*m.contribution_weight),0) clusterValue
                        FROM risk_cluster_membership own
                        JOIN risk_cluster c ON c.id=own.risk_cluster_id
                        JOIN risk_cluster_membership m ON m.risk_cluster_id=c.id
                        JOIN position other ON other.id=m.position_id AND other.status='OPEN'
                        LEFT JOIN current_position_mark marked ON marked.position_id=other.id
                        WHERE own.position_id=UUID_TO_BIN(:positionId)
                        """)
                .param("positionId", positionId.toString())
                .query(ClusterRow.class)
                .single();
        var weight =
                liquid.signum() == 0 ? BigDecimal.ZERO : value.clusterValue().divide(liquid, MathContext.DECIMAL64);
        return new ClusterEvidence(weight);
    }

    private RiskEvidence riskEvidence(UUID userId) {
        var value = jdbc.sql(
                        """
                        SELECT COUNT(p.id) positionCount, COUNT(latest.id) snapshotCount,
                               COALESCE(SUM(latest.quality_status<>'HEALTHY'),0) impairedCount,
                               MAX(latest.data_as_of) dataAsOf
                        FROM position p
                        JOIN investment_account a ON a.id=p.account_id
                        LEFT JOIN position_risk_snapshot latest ON latest.position_id=p.id
                          AND latest.data_as_of=(SELECT MAX(x.data_as_of) FROM position_risk_snapshot x
                                                WHERE x.position_id=p.id)
                        WHERE a.user_id=UUID_TO_BIN(:userId) AND p.status='OPEN'
                        """)
                .param("userId", userId.toString())
                .query(RiskRow.class)
                .single();
        var quality = value.snapshotCount() == 0
                ? EvidenceQuality.MISSING
                : value.snapshotCount() == value.positionCount() && value.impairedCount() == 0
                        ? EvidenceQuality.HEALTHY
                        : EvidenceQuality.PARTIAL;
        return new RiskEvidence(quality, instant(value.dataAsOf()));
    }

    private boolean providerHardError(UUID instrumentId) {
        return jdbc.sql(
                                """
                        SELECT COUNT(*) FROM data_quality_event
                        WHERE instrument_id=UUID_TO_BIN(:instrumentId) AND status='OPEN'
                          AND severity IN ('ERROR','CRITICAL')
                        """)
                        .param("instrumentId", instrumentId.toString())
                        .query(Long.class)
                        .single()
                > 0;
    }

    private HoldingEvidence.LatestQuote quote(UUID instrumentId, DecisionAsOfContext context) {
        return jdbc.sql(
                        """
                        SELECT last_price last, data_as_of dataAsOf,
                               COALESCE(decision_market_date,DATE(source_timestamp)) marketDate,
                               CASE WHEN decision_quality_status='MISSING' AND quality_status<>'MISSING'
                                    THEN quality_status ELSE decision_quality_status END quality,
                               execution_quality_status executionQuality
                        FROM quote WHERE instrument_id=UUID_TO_BIN(:id)
                          AND COALESCE(decision_market_date,DATE(source_timestamp))<=:marketDate AND data_as_of<=:cutoff
                        ORDER BY data_as_of DESC, created_at DESC LIMIT 1
                        """)
                .param("id", instrumentId.toString())
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .query(QuoteRow.class)
                .optional()
                .map(value -> new HoldingEvidence.LatestQuote(
                        value.last(),
                        instant(value.dataAsOf()),
                        value.marketDate(),
                        quality(value.quality()),
                        quality(value.executionQuality())))
                .orElse(new HoldingEvidence.LatestQuote(null, null, EvidenceQuality.MISSING));
    }

    private List<HoldingEvidence.PriceBar> bars(UUID instrumentId, DecisionAsOfContext context) {
        return jdbc
                .sql(
                        """
                        SELECT market_date marketDate, close_price close,volume
                        FROM price_bar WHERE instrument_id=UUID_TO_BIN(:id) AND adjusted=TRUE
                          AND market_date<=:marketDate AND data_as_of<=:cutoff
                        ORDER BY market_date DESC LIMIT 250
                        """)
                .param("id", instrumentId.toString())
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .query(BarRow.class)
                .list()
                .stream()
                .map(value -> new HoldingEvidence.PriceBar(value.marketDate(), value.close(), value.volume(), true))
                .toList();
    }

    private HoldingEvidence.IndicatorSet indicators(
            UUID instrumentId, List<HoldingEvidence.PriceBar> bars, DecisionAsOfContext context) {
        var values = jdbc.sql(
                        """
                        SELECT indicator_code code, value_double value
                        FROM indicator_snapshot s
                        WHERE instrument_id=UUID_TO_BIN(:id) AND status='READY'
                          AND market_date<=:marketDate AND data_as_of<=:cutoff
                          AND market_date=(SELECT MAX(x.market_date) FROM indicator_snapshot x
                                           WHERE x.instrument_id=s.instrument_id AND x.market_date<=:marketDate
                                             AND x.data_as_of<=:cutoff)
                          AND indicator_code IN ('SMA_20','RSI_14','ATR_14')
                        """)
                .param("id", instrumentId.toString())
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .query(IndicatorRow.class)
                .list();
        var sma = indicator(values, "SMA_20");
        var latest = bars.isEmpty() ? null : bars.getFirst().close();
        var priceState = jdbc.sql(
                        "SELECT price_state FROM price_state_snapshot WHERE instrument_id=UUID_TO_BIN(:id) AND market_date<=:marketDate AND data_as_of<=:cutoff ORDER BY market_date DESC,data_as_of DESC LIMIT 1")
                .param("id", instrumentId.toString())
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .query(String.class)
                .optional()
                .orElse(
                        sma != null && latest != null && latest.compareTo(BigDecimal.valueOf(sma)) >= 0
                                ? "UPTREND"
                                : "DOWNTREND");
        return new HoldingEvidence.IndicatorSet(
                sma != null,
                sma != null && latest != null && latest.compareTo(BigDecimal.valueOf(sma)) >= 0,
                indicator(values, "RSI_14"),
                indicator(values, "ATR_14"),
                priceState);
    }

    private HoldingEvidence.FundamentalSnapshot fundamentals(UUID instrumentId, DecisionAsOfContext context) {
        var financials = jdbc.sql(
                        """
                        SELECT quality, dataAsOf, health FROM (
                            SELECT quality, data_as_of dataAsOf, overall_status health, 0 source_priority
                            FROM financial_health_snapshot WHERE instrument_id=UUID_TO_BIN(:id)
                              AND overall_status<>'MISSING' AND DATE(data_as_of)<=:marketDate AND data_as_of<=:cutoff
                            UNION ALL
                            SELECT quality_status quality, MAX(data_as_of) dataAsOf, 'HEALTHY' health, 1 source_priority
                            FROM fundamental_observation WHERE instrument_id=UUID_TO_BIN(:id)
                              AND DATE(data_as_of)<=:marketDate AND data_as_of<=:cutoff
                            GROUP BY quality_status
                        ) evidence
                        ORDER BY source_priority, dataAsOf DESC LIMIT 1
                        """)
                .param("id", instrumentId.toString())
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .query(QualityRow.class)
                .optional()
                .map(value -> new HoldingEvidence.FundamentalSnapshot(
                        true,
                        quality(value.quality()),
                        instant(value.dataAsOf()),
                        value.health(),
                        "MISSING",
                        EvidenceQuality.MISSING))
                .orElse(new HoldingEvidence.FundamentalSnapshot(false, EvidenceQuality.MISSING, null));
        return jdbc.sql(
                        """
                        SELECT overall_revision revision, quality, data_as_of dataAsOf
                        FROM estimate_revision_snapshot WHERE instrument_id=UUID_TO_BIN(:id)
                          AND DATE(data_as_of)<=:marketDate AND data_as_of<=:cutoff
                        ORDER BY data_as_of DESC LIMIT 1
                        """)
                .param("id", instrumentId.toString())
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .query(RevisionRow.class)
                .optional()
                .map(revision -> new HoldingEvidence.FundamentalSnapshot(
                        financials.available(),
                        financials.quality(),
                        financials.dataAsOf(),
                        financials.financialHealth(),
                        revision.revision(),
                        quality(revision.quality()),
                        instant(revision.dataAsOf())))
                .orElse(financials);
    }

    private HoldingEvidence.ValuationSnapshot valuation(
            UUID positionId, UUID instrumentId, DecisionAsOfContext context) {
        var canonical = jdbc.sql(
                        """
                        SELECT valuation_state state, confidence, observation_count observationCount,
                               data_as_of dataAsOf
                        FROM valuation_assessment_snapshot WHERE instrument_id=UUID_TO_BIN(:id)
                          AND DATE(data_as_of)<=:marketDate AND data_as_of<=:cutoff AND strategy_version=:strategyVersion
                        ORDER BY data_as_of DESC LIMIT 1
                        """)
                .param("id", instrumentId.toString())
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .param("strategyVersion", context.strategyVersion())
                .query(ValuationRow.class)
                .optional()
                .map(value -> new HoldingEvidence.ValuationSnapshot(
                        true,
                        value.state(),
                        value.confidence(),
                        value.observationCount(),
                        0,
                        false,
                        instant(value.dataAsOf())));
        var valuation = canonical.orElseGet(() -> jdbc.sql(
                        "SELECT data_as_of FROM valuation_snapshot WHERE position_id=UUID_TO_BIN(:id) AND DATE(data_as_of)<=:marketDate AND data_as_of<=:cutoff ORDER BY data_as_of DESC LIMIT 1")
                .param("id", positionId.toString())
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .query(LocalDateTime.class)
                .optional()
                .map(value -> new HoldingEvidence.ValuationSnapshot(true, instant(value)))
                .orElse(new HoldingEvidence.ValuationSnapshot(false, null)));
        return jdbc.sql(
                        """
                        SELECT COUNT(*) priorCount, COALESCE(SUM(confirmed_at IS NOT NULL)>0,FALSE) confirmed
                        FROM quality_starter_event WHERE position_id=UUID_TO_BIN(:id)
                          AND DATE(created_at)<=:marketDate AND created_at<=:cutoff
                        """)
                .param("id", positionId.toString())
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .query(StarterStatusRow.class)
                .optional()
                .map(status -> new HoldingEvidence.ValuationSnapshot(
                        valuation.available(),
                        valuation.state(),
                        valuation.confidence(),
                        valuation.observationCount(),
                        status.priorCount(),
                        status.confirmed(),
                        valuation.dataAsOf()))
                .orElse(valuation);
    }

    private HoldingEvidence.EarningsEvent event(UUID positionId, UUID instrumentId, DecisionAsOfContext context) {
        var risk = jdbc.sql(
                        """
                        SELECT next_event_at eventAt,event_risk eventRisk,action policyAction,data_as_of dataAsOf FROM earnings_risk_snapshot
                        WHERE position_id=UUID_TO_BIN(:id) AND valid_until>=:cutoff
                          AND DATE(data_as_of)<=:marketDate AND data_as_of<=:cutoff
                          AND strategy_version=:strategyVersion
                        ORDER BY data_as_of DESC LIMIT 1
                        """)
                .param("id", positionId.toString())
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .param("strategyVersion", context.strategyVersion())
                .query(EventRow.class)
                .optional();
        if (risk.isPresent()) {
            var value = risk.orElseThrow();
            return new HoldingEvidence.EarningsEvent(
                    value.eventAt() != null,
                    instant(value.eventAt()),
                    value.eventRisk(),
                    value.policyAction(),
                    instant(value.dataAsOf()));
        }
        return jdbc.sql(
                        """
                        SELECT event_at eventAt,NULL eventRisk,NULL policyAction,data_as_of dataAsOf FROM company_event
                        WHERE instrument_id=UUID_TO_BIN(:id) AND event_at>=:cutoff
                          AND DATE(data_as_of)<=:marketDate AND data_as_of<=:cutoff
                        ORDER BY event_at LIMIT 1
                        """)
                .param("id", instrumentId.toString())
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .query(EventRow.class)
                .optional()
                .map(value -> new HoldingEvidence.EarningsEvent(
                        true,
                        instant(value.eventAt()),
                        value.eventRisk(),
                        value.policyAction(),
                        instant(value.dataAsOf())))
                .orElse(new HoldingEvidence.EarningsEvent(false, null, null, null));
    }

    private HoldingEvidence.CatalystEvidence catalyst(UUID positionId, DecisionAsOfContext context) {
        return jdbc.sql(
                        """
                        SELECT status,catalyst_type catalystType,summary,source,data_as_of dataAsOf,
                               expected_window_start expectedWindowStart,expected_window_end expectedWindowEnd,invalidation
                        FROM tactical_catalyst_evidence WHERE position_id=UUID_TO_BIN(:id)
                          AND DATE(data_as_of)<=:marketDate AND data_as_of<=:cutoff
                        ORDER BY data_as_of DESC,created_at DESC LIMIT 1
                        """)
                .param("id", positionId.toString())
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .query(CatalystRow.class)
                .optional()
                .map(value -> new HoldingEvidence.CatalystEvidence(
                        true,
                        HoldingEvidence.CatalystStatus.valueOf(value.status()),
                        HoldingEvidence.CatalystType.valueOf(value.catalystType()),
                        value.summary(),
                        value.source(),
                        instant(value.dataAsOf()),
                        value.expectedWindowStart(),
                        value.expectedWindowEnd(),
                        value.invalidation()))
                .orElseGet(HoldingEvidence.CatalystEvidence::missing);
    }

    private HoldingEvidence.Thesis thesis(UUID positionId, DecisionAsOfContext context) {
        return jdbc.sql(
                        """
                        SELECT status, expires_at expiresAt FROM position_thesis
                        WHERE position_id=UUID_TO_BIN(:id) AND DATE(updated_at)<=:marketDate AND updated_at<=:cutoff LIMIT 1
                        """)
                .param("id", positionId.toString())
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .query(ThesisRow.class)
                .optional()
                .map(value ->
                        new HoldingEvidence.Thesis(true, "BROKEN".equals(value.status()), instant(value.expiresAt())))
                .orElse(new HoldingEvidence.Thesis(false, false, null));
    }

    private HoldingEvidence.MarketRegimeSnapshot regime(DecisionAsOfContext context) {
        return jdbc.sql(
                        "SELECT regime_label label, data_as_of dataAsOf FROM market_regime_snapshot WHERE DATE(data_as_of)<=:marketDate AND data_as_of<=:cutoff AND strategy_version=:strategyVersion ORDER BY data_as_of DESC LIMIT 1")
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .param("strategyVersion", context.strategyVersion())
                .query(RegimeRow.class)
                .optional()
                .map(value -> new HoldingEvidence.MarketRegimeSnapshot(true, value.label(), instant(value.dataAsOf())))
                .orElse(new HoldingEvidence.MarketRegimeSnapshot(false, null, null));
    }

    private DrawdownRow drawdown(UUID userId, DecisionAsOfContext context) {
        return jdbc.sql(
                        """
                        SELECT TRUE available, drawdown_fraction fraction, drawdown_state state, data_as_of dataAsOf
                        FROM portfolio_drawdown_snapshot WHERE user_id=UUID_TO_BIN(:userId)
                          AND DATE(data_as_of)<=:marketDate AND data_as_of<=:cutoff
                        ORDER BY data_as_of DESC LIMIT 1
                        """)
                .param("userId", userId.toString())
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .query(DrawdownRow.class)
                .optional()
                .orElse(new DrawdownRow(false, BigDecimal.ZERO, null, null));
    }

    private HoldingEvidence.StopEvidence stop(UUID positionId, DecisionAsOfContext context) {
        return jdbc.sql(
                        """
                        SELECT initial_stop formalStop, live_stop liveStop, close_confirmed closeConfirmed,data_as_of dataAsOf,
                               EXISTS(SELECT 1 FROM stop_alert a WHERE a.stop_snapshot_id=s.id
                                      AND a.event_type='CATASTROPHIC') catastrophic
                        FROM stop_snapshot s WHERE position_id=UUID_TO_BIN(:id)
                          AND DATE(data_as_of)<=:marketDate AND data_as_of<=:cutoff
                        ORDER BY data_as_of DESC LIMIT 1
                        """)
                .param("id", positionId.toString())
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .query(StopRow.class)
                .optional()
                .map(value -> new HoldingEvidence.StopEvidence(
                        value.formalStop(),
                        value.liveStop(),
                        value.closeConfirmed(),
                        value.catastrophic(),
                        instant(value.dataAsOf())))
                .orElse(new HoldingEvidence.StopEvidence(null, null, false, false));
    }

    private HoldingEvidence.AnalysisProfile profile(UUID instrumentId, DecisionAsOfContext context) {
        return jdbc.sql(
                        """
                        SELECT fund_profile_available fundProfileAvailable, thematic,
                               top_holding_concentration topHoldingConcentration,
                               fund_liquidity_status liquidityStatus,
                               portfolio_overlap_fraction portfolioOverlap,data_as_of dataAsOf
                        FROM instrument_analysis_profile WHERE instrument_id=UUID_TO_BIN(:id)
                          AND DATE(data_as_of)<=:marketDate AND data_as_of<=:cutoff
                        ORDER BY data_as_of DESC LIMIT 1
                        """)
                .param("id", instrumentId.toString())
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .query(ProfileRow.class)
                .optional()
                .map(value -> new HoldingEvidence.AnalysisProfile(
                        value.fundProfileAvailable(),
                        value.thematic(),
                        value.topHoldingConcentration(),
                        value.liquidityStatus(),
                        value.portfolioOverlap(),
                        instant(value.dataAsOf())))
                .orElse(new HoldingEvidence.AnalysisProfile(false, false, null, null, null));
    }

    private static Double indicator(List<IndicatorRow> values, String code) {
        return values.stream()
                .filter(value -> code.equals(value.code()))
                .map(IndicatorRow::value)
                .findFirst()
                .orElse(null);
    }

    private static HoldingClassification classification(String value) {
        try {
            return HoldingClassification.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return HoldingClassification.UNKNOWN;
        }
    }

    private static EvidenceQuality quality(String value) {
        if (value == null) return EvidenceQuality.MISSING;
        try {
            return EvidenceQuality.valueOf(value);
        } catch (IllegalArgumentException exception) {
            return EvidenceQuality.SUSPECT;
        }
    }

    private static EvidenceQuality quality(EvidenceQuality first, EvidenceQuality second) {
        if (first == EvidenceQuality.MISSING || second == EvidenceQuality.MISSING) {
            return EvidenceQuality.MISSING;
        }
        if (first == EvidenceQuality.SUSPECT || second == EvidenceQuality.SUSPECT) return EvidenceQuality.SUSPECT;
        if (first == EvidenceQuality.STALE || second == EvidenceQuality.STALE) return EvidenceQuality.STALE;
        if (first == EvidenceQuality.PARTIAL || second == EvidenceQuality.PARTIAL) return EvidenceQuality.PARTIAL;
        return first;
    }

    private static Instant instant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }

    private static HoldingEvidence.Money money(BigDecimal value) {
        return new HoldingEvidence.Money(value, "USD");
    }

    record PositionRow(
            UUID positionId,
            UUID userId,
            UUID instrumentId,
            String symbol,
            String assetType,
            boolean active,
            String classification,
            boolean classificationConfirmed,
            BigDecimal quantity,
            BigDecimal averageCost,
            BigDecimal marketValue) {}

    record PortfolioTotals(BigDecimal tactical, BigDecimal openRisk) {}

    record ClusterRow(BigDecimal clusterValue) {}

    record ClusterEvidence(BigDecimal weight) {}

    record RiskRow(long positionCount, long snapshotCount, long impairedCount, LocalDateTime dataAsOf) {}

    record RiskEvidence(EvidenceQuality quality, Instant dataAsOf) {}

    record QuoteRow(
            BigDecimal last, LocalDateTime dataAsOf, LocalDate marketDate, String quality, String executionQuality) {}

    record BarRow(LocalDate marketDate, BigDecimal close, BigDecimal volume) {}

    record IndicatorRow(String code, Double value) {}

    record QualityRow(String quality, LocalDateTime dataAsOf, String health) {}

    record RevisionRow(String revision, String quality, LocalDateTime dataAsOf) {}

    record ValuationRow(String state, String confidence, int observationCount, LocalDateTime dataAsOf) {}

    record StarterStatusRow(int priorCount, boolean confirmed) {}

    record EventRow(LocalDateTime eventAt, String eventRisk, String policyAction, LocalDateTime dataAsOf) {}

    record CatalystRow(
            String status,
            String catalystType,
            String summary,
            String source,
            LocalDateTime dataAsOf,
            LocalDate expectedWindowStart,
            LocalDate expectedWindowEnd,
            String invalidation) {}

    record ThesisRow(String status, LocalDateTime expiresAt) {}

    record RegimeRow(String label, LocalDateTime dataAsOf) {}

    record DrawdownRow(boolean available, BigDecimal fraction, String state, LocalDateTime dataAsOf) {}

    record StopRow(
            BigDecimal formalStop,
            BigDecimal liveStop,
            boolean closeConfirmed,
            boolean catastrophic,
            LocalDateTime dataAsOf) {}

    record ProfileRow(
            boolean fundProfileAvailable,
            boolean thematic,
            BigDecimal topHoldingConcentration,
            String liquidityStatus,
            BigDecimal portfolioOverlap,
            LocalDateTime dataAsOf) {}
}
