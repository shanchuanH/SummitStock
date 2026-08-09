package com.example.portfolio.earnings;

import com.example.portfolio.strategy.portfolio.HoldingClassification;
import com.example.portfolio.strategy.position.EarningsPolicy;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class EarningsEvidenceStore {
    private final JdbcClient jdbc;
    private final Clock clock;

    public EarningsEvidenceStore(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public List<Instrument> eligibleInstruments() {
        return jdbc.sql("SELECT BIN_TO_UUID(id) id,symbol FROM instrument WHERE active=TRUE AND asset_type='EQUITY'")
                .query(Instrument.class)
                .list();
    }

    public int saveEvent(
            UUID instrumentId, EarningsCalendarProvider.CalendarEvent event, String provider, String quality) {
        var checksum = sha256(instrumentId + "|" + event + "|" + provider);
        int affected = jdbc.sql(
                        """
                        INSERT IGNORE INTO earnings_event (
                          id,instrument_id,event_at,market_date,timing,fiscal_period,binary_event,source,source_url,
                          quality,evidence_checksum,data_as_of,created_at
                        ) VALUES (UUID_TO_BIN(:id),UUID_TO_BIN(:instrumentId),:eventAt,:marketDate,:timing,:fiscalPeriod,
                          :binaryEvent,:source,:sourceUrl,:quality,:checksum,:now,:now)
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("instrumentId", instrumentId.toString())
                .param("eventAt", event.eventAt())
                .param("marketDate", event.marketDate())
                .param("timing", event.timing().name())
                .param("fiscalPeriod", event.fiscalPeriod())
                .param("binaryEvent", event.binaryEvent())
                .param("source", provider)
                .param("sourceUrl", event.sourceUrl())
                .param("quality", quality)
                .param("checksum", checksum)
                .param("now", clock.instant())
                .update();
        jdbc.sql(
                        """
                        INSERT IGNORE INTO company_event (
                          id,instrument_id,event_type,event_at,market_date,title,source,source_url,checksum,
                          quality_status,data_as_of,metadata,created_at
                        ) VALUES (UUID_TO_BIN(:id),UUID_TO_BIN(:instrumentId),'EARNINGS',:eventAt,:marketDate,
                          'Earnings',:source,:sourceUrl,:checksum,:quality,:now,JSON_OBJECT('timing',:timing),:now)
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("instrumentId", instrumentId.toString())
                .param("eventAt", event.eventAt())
                .param("marketDate", event.marketDate())
                .param("source", provider)
                .param("sourceUrl", event.sourceUrl())
                .param("checksum", checksum)
                .param("quality", quality)
                .param("timing", event.timing().name())
                .param("now", clock.instant())
                .update();
        return affected;
    }

    public List<Event> eventsWithoutReaction() {
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(e.id) id,BIN_TO_UUID(e.instrument_id) instrumentId,e.market_date marketDate,e.timing
                        FROM earnings_event e LEFT JOIN earnings_reaction_snapshot r ON r.earnings_event_id=e.id
                        WHERE e.market_date<CURRENT_DATE AND r.id IS NULL ORDER BY e.market_date
                        """)
                .query(Event.class)
                .list();
    }

    public List<EarningsReactionCalculator.Bar> bars(UUID instrumentId, LocalDate eventDate) {
        return jdbc.sql(
                        """
                        SELECT market_date date,open_price open,close_price close,volume
                        FROM price_bar WHERE instrument_id=UUID_TO_BIN(:instrumentId) AND adjusted=TRUE
                          AND timeframe='1D' AND quality_status='HEALTHY'
                          AND market_date BETWEEN :fromDate AND :toDate ORDER BY market_date
                        """)
                .param("instrumentId", instrumentId.toString())
                .param("fromDate", eventDate.minusDays(45))
                .param("toDate", eventDate.plusDays(10))
                .query(EarningsReactionCalculator.Bar.class)
                .list();
    }

    public int saveReaction(Event event, EarningsReactionCalculator.Result value) {
        var checksum = sha256(event.id() + "|" + value);
        return jdbc.sql(
                        """
                        INSERT IGNORE INTO earnings_reaction_snapshot (
                          id,earnings_event_id,instrument_id,pre_close,next_open,next_close,return_1d,return_3d,
                          return_5d,gap_return,volume_shock,pre_event_20d_return,quality,evidence_checksum,data_as_of,created_at
                        ) VALUES (UUID_TO_BIN(:id),UUID_TO_BIN(:eventId),UUID_TO_BIN(:instrumentId),:preClose,:nextOpen,
                          :nextClose,:return1d,:return3d,:return5d,:gapReturn,:volumeShock,:preRunup,'HEALTHY',:checksum,:now,:now)
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("eventId", event.id().toString())
                .param("instrumentId", event.instrumentId().toString())
                .param("preClose", value.preClose())
                .param("nextOpen", value.nextOpen())
                .param("nextClose", value.nextClose())
                .param("return1d", value.return1d())
                .param("return3d", value.return3d())
                .param("return5d", value.return5d())
                .param("gapReturn", value.gapReturn())
                .param("volumeShock", value.volumeShock())
                .param("preRunup", value.preEvent20dReturn())
                .param("checksum", checksum)
                .param("now", clock.instant())
                .update();
    }

    public List<EarningsReactionStats.Reaction> recentReactions(UUID instrumentId) {
        return jdbc.sql(
                        """
                        SELECT return_1d return1d,gap_return gapReturn,pre_event_20d_return preEvent20dReturn
                        FROM earnings_reaction_snapshot WHERE instrument_id=UUID_TO_BIN(:instrumentId)
                          AND return_1d IS NOT NULL AND gap_return IS NOT NULL AND pre_event_20d_return IS NOT NULL
                        ORDER BY data_as_of DESC LIMIT 12
                        """)
                .param("instrumentId", instrumentId.toString())
                .query(EarningsReactionStats.Reaction.class)
                .list();
    }

    public List<PositionRiskInput> positionRiskInputs() {
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(p.id) positionId,BIN_TO_UUID(p.instrument_id) instrumentId,p.classification,
                          CASE WHEN COALESCE((SELECT c.investable_assets FROM portfolio_capital_snapshot c
                            WHERE c.user_id=a.user_id ORDER BY c.data_as_of DESC LIMIT 1),0)>0
                            THEN p.market_value/(SELECT c.investable_assets FROM portfolio_capital_snapshot c
                              WHERE c.user_id=a.user_id ORDER BY c.data_as_of DESC LIMIT 1) ELSE 0 END positionWeight,
                          CASE WHEN p.average_cost>COALESCE((SELECT s.live_stop FROM stop_snapshot s WHERE s.position_id=p.id
                            ORDER BY s.data_as_of DESC LIMIT 1),p.average_cost)
                            THEN (COALESCE((SELECT q.last_price FROM quote q WHERE q.instrument_id=p.instrument_id
                              ORDER BY q.data_as_of DESC LIMIT 1),p.average_cost)-p.average_cost)
                              /(p.average_cost-(SELECT s.live_stop FROM stop_snapshot s WHERE s.position_id=p.id
                                ORDER BY s.data_as_of DESC LIMIT 1)) ELSE 0 END profitCushionR,
                          (SELECT e.event_at FROM earnings_event e WHERE e.instrument_id=p.instrument_id
                            AND e.event_at>=UTC_TIMESTAMP(6) ORDER BY e.event_at LIMIT 1) nextEventAt,
                          COALESCE((SELECT e.binary_event FROM earnings_event e WHERE e.instrument_id=p.instrument_id
                            AND e.event_at>=UTC_TIMESTAMP(6) ORDER BY e.event_at LIMIT 1),FALSE) binaryEvent
                        FROM position p JOIN investment_account a ON a.id=p.account_id
                        WHERE p.status='OPEN' AND p.classification<>'THEMATIC_ETF'
                        """)
                .query(PositionRiskInput.class)
                .list();
    }

    public int saveRisk(
            PositionRiskInput input,
            EarningsReactionStats.Summary stats,
            EarningsEventRiskEngine.Risk risk,
            EarningsPolicy.Result policy,
            String strategyVersion) {
        var checksum = sha256(input.positionId() + "|" + stats + "|" + risk + "|" + policy);
        return jdbc.sql(
                        """
                        INSERT IGNORE INTO earnings_risk_snapshot (
                          id,position_id,strategy_version,event_count,event_risk,median_abs_move_fraction,
                          p75_abs_move_fraction,p90_abs_move_fraction,worst_downside_gap_fraction,
                          best_upside_gap_fraction,median_pre_runup_fraction,next_event_at,downside_tail_fraction,
                          gap_p75_fraction,gap_p90_fraction,profit_cushion_r,action,rule_ids,evidence_checksum,
                          data_as_of,valid_until,created_at
                        ) VALUES (UUID_TO_BIN(:id),UUID_TO_BIN(:positionId),:strategyVersion,:eventCount,:eventRisk,
                          :medianAbs,:p75,:p90,:worstGap,:bestGap,:preRunup,:nextEvent,:worstGap,:p75,:p90,
                          :cushion,:action,:rules,:checksum,:now,DATE_ADD(:now,INTERVAL 1 DAY),:now)
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("positionId", input.positionId().toString())
                .param("strategyVersion", strategyVersion)
                .param("eventCount", stats.eventCount())
                .param("eventRisk", risk.name())
                .param("medianAbs", stats.medianAbsMove())
                .param("p75", stats.p75AbsMove())
                .param("p90", stats.p90AbsMove())
                .param("worstGap", stats.worstDownsideGap())
                .param("bestGap", stats.bestUpsideGap())
                .param("preRunup", stats.medianPreRunup())
                .param("nextEvent", input.nextEventAt())
                .param("cushion", input.profitCushionR())
                .param("action", policy.action())
                .param("rules", toJson(policy.ruleIds()))
                .param("checksum", checksum)
                .param("now", clock.instant())
                .update();
    }

    private static String toJson(List<String> values) {
        return values.stream()
                .map(value -> "\"" + value.replace("\"", "\\\"") + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record Instrument(UUID id, String symbol) {}

    public record Event(UUID id, UUID instrumentId, LocalDate marketDate, String timing) {}

    public record PositionRiskInput(
            UUID positionId,
            UUID instrumentId,
            String classification,
            BigDecimal positionWeight,
            BigDecimal profitCushionR,
            java.time.Instant nextEventAt,
            boolean binaryEvent) {
        public HoldingClassification holdingClassification() {
            return HoldingClassification.valueOf(classification);
        }
    }
}
