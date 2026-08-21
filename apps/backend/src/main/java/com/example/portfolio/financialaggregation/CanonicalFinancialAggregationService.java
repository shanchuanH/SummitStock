package com.example.portfolio.financialaggregation;

import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public final class CanonicalFinancialAggregationService {
    private final JdbcClient jdbc;

    public CanonicalFinancialAggregationService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Ttm ttm(UUID instrumentId, Instant cutoff) {
        var observations = jdbc.sql(
                        """
                        SELECT p.period_type periodType,p.end_date periodEnd,m.metric_code metricCode,
                               m.value_decimal value,m.data_as_of dataAsOf
                        FROM financial_metric_snapshot m JOIN financial_period p ON p.id=m.period_id
                        WHERE m.instrument_id=UUID_TO_BIN(:instrumentId)
                          AND m.metric_code IN ('REVENUE','DILUTED_EPS','FREE_CASH_FLOW')
                          AND m.data_as_of<=:cutoff
                        """)
                .param("instrumentId", instrumentId.toString())
                .param("cutoff", cutoff)
                .query((rs, row) -> new CanonicalFinancialAggregation.Observation(
                        rs.getString("periodType"),
                        rs.getObject("periodEnd", java.time.LocalDate.class),
                        rs.getString("metricCode"),
                        rs.getBigDecimal("value"),
                        rs.getTimestamp("dataAsOf").toInstant()))
                .list();
        return new Ttm(
                CanonicalFinancialAggregation.ttm("REVENUE", cutoff, observations),
                CanonicalFinancialAggregation.ttm("DILUTED_EPS", cutoff, observations),
                CanonicalFinancialAggregation.ttm("FREE_CASH_FLOW", cutoff, observations));
    }

    public record Ttm(
            CanonicalFinancialAggregation.Aggregate revenue,
            CanonicalFinancialAggregation.Aggregate eps,
            CanonicalFinancialAggregation.Aggregate freeCashFlow) {}
}
