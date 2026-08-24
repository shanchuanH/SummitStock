package com.example.portfolio.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.MySqlIntegrationTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class CanonicalBenchmarkReturnIntegrationTest extends MySqlIntegrationTest {
    @Autowired
    PortfolioAnalysisPipelineService pipeline;

    @Autowired
    JdbcClient jdbc;

    @Test
    @Transactional
    void duplicateProviderBarsUseOneLatestCanonicalObservation() {
        var instrumentId = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO instrument (
                          id,symbol,exchange,asset_type,currency,active,metadata,created_at,updated_at
                        ) VALUES (
                          UUID_TO_BIN(:id),'QQQDUP','TEST','ETF','USD',TRUE,JSON_OBJECT(),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
                        )
                        """)
                .param("id", instrumentId.toString())
                .update();
        var marketDate = LocalDate.parse("2026-08-14");
        insertBar(instrumentId, marketDate, new BigDecimal("100"), Instant.parse("2026-08-14T22:00:00Z"));
        var learnedAt = Instant.parse("2099-01-01T00:00:00Z");
        insertBar(instrumentId, marketDate, new BigDecimal("999"), learnedAt);

        assertThat(pipeline.returnFromPeak("QQQDUP", marketDate)).isZero();
    }

    private void insertBar(UUID instrumentId, LocalDate marketDate, BigDecimal close, Instant learnedAt) {
        jdbc.sql(
                        """
                        INSERT INTO price_bar (
                          id,instrument_id,timeframe,bar_start,market_date,open_price,high_price,low_price,close_price,
                          volume,adjusted,provider,source_timestamp,checksum,normalization_version,quality_status,
                          data_as_of,created_at
                        ) VALUES (
                          UUID_TO_BIN(:rowId),UUID_TO_BIN(:instrumentId),'1D',:learnedAt,:marketDate,:close,:close,:close,
                          :close,1000,TRUE,:provider,:learnedAt,SHA2(:checksum,256),'test-v1','HEALTHY',
                          :learnedAt,:learnedAt
                        )
                        """)
                .param("rowId", UUID.randomUUID().toString())
                .param("instrumentId", instrumentId.toString())
                .param("marketDate", marketDate)
                .param("learnedAt", learnedAt)
                .param("close", close)
                .param("provider", "duplicate-provider-" + UUID.randomUUID())
                .param("checksum", UUID.randomUUID().toString())
                .update();
    }
}
