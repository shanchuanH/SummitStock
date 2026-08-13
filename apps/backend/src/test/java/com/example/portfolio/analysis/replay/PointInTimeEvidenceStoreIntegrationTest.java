package com.example.portfolio.analysis.replay;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.MySqlIntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class PointInTimeEvidenceStoreIntegrationTest extends MySqlIntegrationTest {
    private static final UUID SPY = UUID.fromString("00000000-0000-0000-0000-000000000101");

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PointInTimeEvidenceStore store;

    @AfterEach
    void clean() {
        jdbc.sql("DELETE FROM indicator_snapshot WHERE instrument_id=UUID_TO_BIN(:id) AND indicator_code='PIT_TEST'")
                .param("id", SPY.toString())
                .update();
    }

    @Test
    void futureIndicatorIsInvisibleEvenWhenItIsTheNewestDatabaseRow() {
        insert("2026-01-09", "2026-01-09T21:00:00Z", "10", "a");
        insert("2026-01-10", "2026-01-11T02:00:00Z", "99", "b");
        insert("2026-01-11", "2026-01-11T03:00:00Z", "100", "c");
        var context = new DecisionAsOfContext(
                LocalDate.parse("2026-01-10"), Instant.parse("2026-01-10T23:59:59Z"), "3.0.0-draft");

        var selected = store.latestIndicator(SPY, "PIT_TEST", context).orElseThrow();

        assertThat(selected.value()).isEqualTo("10");
        assertThat(selected.marketDate()).isEqualTo(LocalDate.parse("2026-01-09"));
        assertThat(selected.dataAsOf()).isEqualTo(Instant.parse("2026-01-09T21:00:00Z"));
    }

    private void insert(String marketDate, String dataAsOf, String value, String checksumSuffix) {
        jdbc.sql(
                        """
                        INSERT INTO indicator_snapshot (
                          id,instrument_id,market_date,indicator_code,parameters_hash,adjusted,status,value_double,
                          values_json,required_observations,actual_observations,warnings,source_bar_checksum,
                          normalization_version,data_as_of,created_at)
                        VALUES (UUID_TO_BIN(:id),UUID_TO_BIN(:instrumentId),:marketDate,'PIT_TEST',:hash,TRUE,'READY',
                          :value,NULL,1,1,JSON_ARRAY(),:source,'v1',:dataAsOf,:dataAsOf)
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("instrumentId", SPY.toString())
                .param("marketDate", LocalDate.parse(marketDate))
                .param("hash", checksumSuffix.repeat(64))
                .param("value", value)
                .param("source", checksumSuffix.repeat(64))
                .param("dataAsOf", Instant.parse(dataAsOf))
                .update();
    }
}
