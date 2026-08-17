package com.example.portfolio.estimates;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.MySqlIntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
class EstimateRefreshSelectionIntegrationTest extends MySqlIntegrationTest {
    @Autowired
    EstimateEvidenceStore store;

    @Autowired
    JdbcClient jdbc;

    @Test
    @Transactional
    void refreshesOnlyMissingStaleOrUnhealthyEstimateEvidence() {
        var fresh = instrument("ESTFRESH");
        var stale = instrument("ESTSTALE");
        var unhealthy = instrument("ESTMISS");
        observation(fresh, "HEALTHY", Instant.parse("2026-08-10T00:00:00Z"));
        observation(stale, "HEALTHY", Instant.parse("2026-08-01T00:00:00Z"));
        observation(unhealthy, "MISSING", Instant.parse("2026-08-10T00:00:00Z"));

        var selected = store.instrumentsNeedingRefresh(Instant.parse("2026-08-05T00:00:00Z")).stream()
                .map(EstimateEvidenceStore.InstrumentRef::symbol)
                .toList();

        assertThat(selected).contains("ESTSTALE", "ESTMISS").doesNotContain("ESTFRESH");
    }

    private UUID instrument(String symbol) {
        var id = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO instrument (
                          id,symbol,exchange,asset_type,currency,active,metadata,created_at,updated_at
                        ) VALUES (
                          UUID_TO_BIN(:id),:symbol,'TEST','EQUITY','USD',TRUE,JSON_OBJECT(),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
                        )
                        """)
                .param("id", id.toString())
                .param("symbol", symbol)
                .update();
        return id;
    }

    private void observation(UUID instrumentId, String quality, Instant dataAsOf) {
        jdbc.sql(
                        """
                        INSERT INTO estimate_observation (
                          id,instrument_id,estimate_type,period_type,period_end,horizon,mean_value,analyst_count,
                          data_as_of,source,quality,checksum,created_at
                        ) VALUES (
                          UUID_TO_BIN(:id),UUID_TO_BIN(:instrumentId),'EPS','ANNUAL','2026-12-31','fiscal year',10,5,
                          :dataAsOf,'test',:quality,SHA2(:checksum,256),:dataAsOf
                        )
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("instrumentId", instrumentId.toString())
                .param("dataAsOf", dataAsOf)
                .param("quality", quality)
                .param("checksum", UUID.randomUUID().toString())
                .update();
    }
}
