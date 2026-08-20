package com.example.portfolio.analysis.replay;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class PointInTimeEvidenceStore {
    private final JdbcClient jdbc;

    public PointInTimeEvidenceStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<EvidencePoint> latestIndicator(UUID instrumentId, String code, DecisionAsOfContext context) {
        return jdbc.sql(
                        """
                        SELECT market_date marketDate,data_as_of dataAsOf,CAST(value_double AS CHAR) value,NULL strategyVersion
                        FROM indicator_snapshot WHERE instrument_id=UUID_TO_BIN(:instrumentId) AND indicator_code=:code
                          AND market_date<=:marketDate AND data_as_of<=:cutoff
                        ORDER BY market_date DESC,data_as_of DESC,created_at DESC LIMIT 1
                        """)
                .param("instrumentId", instrumentId.toString())
                .param("code", code)
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .query(EvidenceRow.class)
                .optional()
                .map(PointInTimeEvidenceStore::point);
    }

    public Optional<EvidencePoint> latestBreadth(String universeCode, DecisionAsOfContext context) {
        return point(
                "SELECT market_date marketDate,data_as_of dataAsOf,CAST(pct_above_sma50 AS CHAR) value,NULL strategyVersion "
                        + "FROM breadth_snapshot WHERE universe_code=:key AND market_date<=:marketDate AND data_as_of<=:cutoff "
                        + "ORDER BY market_date DESC,data_as_of DESC,created_at DESC LIMIT 1",
                "key",
                universeCode,
                context);
    }

    public Optional<EvidencePoint> latestMacroObservation(String seriesCode, DecisionAsOfContext context) {
        return point(
                "SELECT observation_date marketDate,data_as_of dataAsOf,CAST(value_decimal AS CHAR) value,NULL strategyVersion "
                        + "FROM macro_observation WHERE series_code=:key AND observation_date<=:marketDate AND data_as_of<=:cutoff "
                        + "ORDER BY observation_date DESC,data_as_of DESC,created_at DESC LIMIT 1",
                "key",
                seriesCode,
                context);
    }

    public Optional<EvidencePoint> latestMacroFactor(DecisionAsOfContext context) {
        return point(
                "SELECT market_date marketDate,data_as_of dataAsOf,CAST(stress_resilience AS CHAR) value,NULL strategyVersion "
                        + "FROM macro_factor_snapshot WHERE market_date<=:marketDate AND data_as_of<=:cutoff "
                        + "ORDER BY market_date DESC,data_as_of DESC,created_at DESC LIMIT 1",
                context);
    }

    public Optional<EvidencePoint> latestDip(UUID userId, UUID instrumentId, DecisionAsOfContext context) {
        return jdbc.sql(
                        """
                        SELECT DATE(data_as_of) marketDate,data_as_of dataAsOf,status value,strategy_version strategyVersion
                        FROM etf_dip_event WHERE user_id=UUID_TO_BIN(:userId) AND instrument_id=UUID_TO_BIN(:instrumentId)
                          AND DATE(data_as_of)<=:marketDate AND data_as_of<=:cutoff AND strategy_version=:strategyVersion
                        ORDER BY data_as_of DESC,created_at DESC LIMIT 1
                        """)
                .param("userId", userId.toString())
                .param("instrumentId", instrumentId.toString())
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .param("strategyVersion", context.strategyVersion())
                .query(EvidenceRow.class)
                .optional()
                .map(PointInTimeEvidenceStore::point);
    }

    public Optional<EvidencePoint> latestRegime(DecisionAsOfContext context) {
        return versioned("market_regime_snapshot", "regime_label", "DATE(data_as_of)", null, null, context);
    }

    public Optional<EvidencePoint> latestEstimate(UUID instrumentId, DecisionAsOfContext context) {
        return instrument(
                "estimate_revision_snapshot", "overall_revision", "DATE(data_as_of)", instrumentId, false, context);
    }

    public Optional<EvidencePoint> latestValuation(UUID instrumentId, DecisionAsOfContext context) {
        return instrument(
                "valuation_assessment_snapshot", "valuation_state", "DATE(data_as_of)", instrumentId, true, context);
    }

    public Optional<EvidencePoint> latestEarnings(UUID positionId, DecisionAsOfContext context) {
        return jdbc.sql(
                        """
                        SELECT DATE(data_as_of) marketDate,data_as_of dataAsOf,action value,strategy_version strategyVersion
                        FROM earnings_risk_snapshot WHERE position_id=UUID_TO_BIN(:positionId)
                          AND DATE(data_as_of)<=:marketDate AND data_as_of<=:cutoff
                          AND strategy_version=:strategyVersion
                        ORDER BY data_as_of DESC,created_at DESC LIMIT 1
                        """)
                .param("positionId", positionId.toString())
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .param("strategyVersion", context.strategyVersion())
                .query(EvidenceRow.class)
                .optional()
                .map(PointInTimeEvidenceStore::point);
    }

    private Optional<EvidencePoint> instrument(
            String table,
            String valueColumn,
            String marketColumn,
            UUID instrumentId,
            boolean versioned,
            DecisionAsOfContext context) {
        var sql = "SELECT " + marketColumn + " marketDate,data_as_of dataAsOf," + valueColumn
                + " value," + (versioned ? "strategy_version" : "NULL") + " strategyVersion FROM " + table
                + " WHERE instrument_id=UUID_TO_BIN(:instrumentId) AND " + marketColumn
                + "<=:marketDate AND data_as_of<=:cutoff"
                + (versioned ? " AND strategy_version=:strategyVersion" : "")
                + " ORDER BY data_as_of DESC,created_at DESC LIMIT 1";
        var statement = jdbc.sql(sql)
                .param("instrumentId", instrumentId.toString())
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff());
        if (versioned) statement = statement.param("strategyVersion", context.strategyVersion());
        return statement.query(EvidenceRow.class).optional().map(PointInTimeEvidenceStore::point);
    }

    private Optional<EvidencePoint> versioned(
            String table,
            String valueColumn,
            String marketColumn,
            String unusedKey,
            String unusedValue,
            DecisionAsOfContext context) {
        var sql = "SELECT " + marketColumn + " marketDate,data_as_of dataAsOf," + valueColumn
                + " value,strategy_version strategyVersion FROM " + table + " WHERE " + marketColumn
                + "<=:marketDate AND data_as_of<=:cutoff AND strategy_version=:strategyVersion"
                + " ORDER BY data_as_of DESC,created_at DESC LIMIT 1";
        return jdbc.sql(sql)
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .param("strategyVersion", context.strategyVersion())
                .query(EvidenceRow.class)
                .optional()
                .map(PointInTimeEvidenceStore::point);
    }

    private Optional<EvidencePoint> point(String sql, DecisionAsOfContext context) {
        return jdbc.sql(sql)
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .query(EvidenceRow.class)
                .optional()
                .map(PointInTimeEvidenceStore::point);
    }

    private Optional<EvidencePoint> point(String sql, String keyName, String keyValue, DecisionAsOfContext context) {
        return jdbc.sql(sql)
                .param(keyName, keyValue)
                .param("marketDate", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .query(EvidenceRow.class)
                .optional()
                .map(PointInTimeEvidenceStore::point);
    }

    private static EvidencePoint point(EvidenceRow row) {
        return new EvidencePoint(
                row.marketDate(), row.dataAsOf().toInstant(ZoneOffset.UTC), row.value(), row.strategyVersion());
    }

    record EvidenceRow(LocalDate marketDate, LocalDateTime dataAsOf, String value, String strategyVersion) {}

    public record EvidencePoint(LocalDate marketDate, Instant dataAsOf, String value, String strategyVersion) {}
}
