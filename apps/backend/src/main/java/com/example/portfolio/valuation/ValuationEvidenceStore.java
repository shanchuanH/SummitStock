package com.example.portfolio.valuation;

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
public class ValuationEvidenceStore {
    private final JdbcClient jdbc;
    private final Clock clock;

    public ValuationEvidenceStore(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public List<InputRow> inputs() {
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(i.id) instrumentId, i.symbol,
                          (SELECT q.decision_market_date FROM quote q WHERE q.instrument_id=i.id AND q.decision_quality_status='HEALTHY' ORDER BY q.data_as_of DESC LIMIT 1) marketDate,
                          (SELECT q.last_price FROM quote q WHERE q.instrument_id=i.id AND q.decision_quality_status='HEALTHY' ORDER BY q.data_as_of DESC LIMIT 1) price,
                          (SELECT m.value_decimal FROM financial_metric_snapshot m WHERE m.instrument_id=i.id AND m.metric_code='DILUTED_EPS' ORDER BY m.data_as_of DESC LIMIT 1) trailingEps,
                          (SELECT e.mean_value FROM estimate_observation e WHERE e.instrument_id=i.id AND e.estimate_type='EPS' ORDER BY e.period_end DESC,e.data_as_of DESC LIMIT 1) forwardEps,
                          (SELECT m.value_decimal FROM financial_metric_snapshot m WHERE m.instrument_id=i.id AND m.metric_code='REVENUE' ORDER BY m.data_as_of DESC LIMIT 1) revenue,
                          (SELECT m.value_decimal FROM financial_metric_snapshot m WHERE m.instrument_id=i.id AND m.metric_code='FREE_CASH_FLOW' ORDER BY m.data_as_of DESC LIMIT 1) freeCashFlow,
                          (SELECT m.value_decimal FROM financial_metric_snapshot m WHERE m.instrument_id=i.id AND m.metric_code='CASH' ORDER BY m.data_as_of DESC LIMIT 1) cash,
                          (SELECT m.value_decimal FROM financial_metric_snapshot m WHERE m.instrument_id=i.id AND m.metric_code='TOTAL_DEBT' ORDER BY m.data_as_of DESC LIMIT 1) totalDebt,
                          (SELECT m.value_decimal FROM financial_metric_snapshot m WHERE m.instrument_id=i.id AND m.metric_code='DILUTED_SHARES' ORDER BY m.data_as_of DESC LIMIT 1) shares,
                          (SELECT m.value_decimal FROM financial_metric_snapshot m WHERE m.instrument_id=i.id AND m.metric_code='REVENUE_YOY' ORDER BY m.data_as_of DESC LIMIT 1) revenueGrowth,
                          (SELECT h.overall_status FROM financial_health_snapshot h WHERE h.instrument_id=i.id ORDER BY h.data_as_of DESC LIMIT 1) health,
                          (SELECT r.overall_revision FROM estimate_revision_snapshot r WHERE r.instrument_id=i.id ORDER BY r.data_as_of DESC LIMIT 1) revision
                        FROM instrument i WHERE i.active=TRUE AND i.asset_type='EQUITY'
                        """)
                .query(InputRow.class)
                .list();
    }

    public int saveMetrics(UUID instrumentId, LocalDate marketDate, ValuationEngineV2.Metrics value, String quality) {
        var checksum = sha256(instrumentId + "|" + marketDate + "|" + value);
        return jdbc.sql(
                        """
                        INSERT IGNORE INTO valuation_metric_history (
                          id,instrument_id,market_date,trailing_pe,forward_pe,ev_sales,fcf_yield,price_sales,
                          market_cap,source,quality,evidence_checksum,data_as_of,created_at
                        ) VALUES (
                          UUID_TO_BIN(:id),UUID_TO_BIN(:instrumentId),:marketDate,:trailingPe,:forwardPe,:evSales,:fcfYield,:priceSales,
                          :marketCap,'canonical-financials-v2',:quality,:checksum,:now,:now
                        )
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("instrumentId", instrumentId.toString())
                .param("marketDate", marketDate)
                .param("trailingPe", value.trailingPe())
                .param("forwardPe", value.forwardPe())
                .param("evSales", value.evSales())
                .param("fcfYield", value.fcfYield())
                .param("priceSales", value.priceSales())
                .param("marketCap", value.marketCap())
                .param("quality", quality)
                .param("checksum", checksum)
                .param("now", clock.instant())
                .update();
    }

    public List<ValuationEngineV2.Metrics> history(UUID instrumentId, LocalDate from) {
        return jdbc.sql(
                        """
                        SELECT trailing_pe trailingPe,forward_pe forwardPe,ev_sales evSales,fcf_yield fcfYield,
                               price_sales priceSales,market_cap marketCap
                        FROM valuation_metric_history WHERE instrument_id=UUID_TO_BIN(:instrumentId) AND market_date>=:from
                        ORDER BY market_date
                        """)
                .param("instrumentId", instrumentId.toString())
                .param("from", from)
                .query(ValuationEngineV2.Metrics.class)
                .list();
    }

    public int saveAssessment(
            UUID instrumentId,
            ValuationEngineV2.Assessment assessment,
            BigDecimal growthAdjusted,
            String strategyVersion,
            String configHash) {
        var checksum = sha256(instrumentId + "|" + assessment + "|" + growthAdjusted);
        return jdbc.sql(
                        """
                        INSERT IGNORE INTO valuation_assessment_snapshot (
                          id,instrument_id,valuation_state,confidence,own_history_percentile_3y,
                          own_history_percentile_5y,relative_valuation,growth_adjusted_valuation,observation_count,
                          quality,strategy_version,config_hash,evidence_checksum,data_as_of,created_at
                        ) VALUES (
                          UUID_TO_BIN(:id),UUID_TO_BIN(:instrumentId),:state,:confidence,:percentile3,
                          :percentile5,NULL,:growthAdjusted,:count,:quality,:strategyVersion,:configHash,:checksum,:now,:now
                        )
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("instrumentId", instrumentId.toString())
                .param("state", assessment.state().name())
                .param("confidence", assessment.confidence().name())
                .param("percentile3", assessment.ownHistoryPercentile3y())
                .param("percentile5", assessment.ownHistoryPercentile5y())
                .param("growthAdjusted", growthAdjusted)
                .param("count", assessment.observationCount())
                .param("quality", assessment.quality().name())
                .param("strategyVersion", strategyVersion)
                .param("configHash", configHash)
                .param("checksum", checksum)
                .param("now", clock.instant())
                .update();
    }

    public String configHash(String strategyVersion) {
        return jdbc.sql("SELECT config_hash FROM strategy_version WHERE version_code=:version")
                .param("version", strategyVersion)
                .query(String.class)
                .optional()
                .orElse("0".repeat(64));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record InputRow(
            UUID instrumentId,
            String symbol,
            LocalDate marketDate,
            BigDecimal price,
            BigDecimal trailingEps,
            BigDecimal forwardEps,
            BigDecimal revenue,
            BigDecimal freeCashFlow,
            BigDecimal cash,
            BigDecimal totalDebt,
            BigDecimal shares,
            BigDecimal revenueGrowth,
            String health,
            String revision) {}
}
