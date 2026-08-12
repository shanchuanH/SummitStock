package com.example.portfolio.market;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.MySqlIntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class TrackedInstrumentPollingIntegrationTest extends MySqlIntegrationTest {
    private static final String INSTRUMENT_ID = "99999999-9999-9999-9999-999999999991";
    private static final String USER_ID = "99999999-9999-9999-9999-999999999992";

    @Autowired
    EodMarketPipelineService market;

    @Autowired
    JdbcClient jdbc;

    @AfterEach
    void clean() {
        jdbc.sql("DELETE FROM investment_idea WHERE instrument_id=UUID_TO_BIN(:id)")
                .param("id", INSTRUMENT_ID)
                .update();
        jdbc.sql("DELETE FROM quote WHERE instrument_id=UUID_TO_BIN(:id)")
                .param("id", INSTRUMENT_ID)
                .update();
        jdbc.sql("DELETE FROM instrument WHERE id=UUID_TO_BIN(:id)")
                .param("id", INSTRUMENT_ID)
                .update();
        jdbc.sql("DELETE FROM app_user WHERE id=UUID_TO_BIN(:id)")
                .param("id", USER_ID)
                .update();
    }

    @Test
    void ignoresHistoricalInstrumentsButIncludesActiveWatchlistIdeasAndBenchmarks() {
        var added = jdbc.sql(
                        """
                INSERT INTO instrument
                  (id,symbol,exchange,asset_type,currency,active,metadata,created_at,updated_at,version)
                VALUES (UUID_TO_BIN(:id),'ZZZUNTRACKED','XNAS','EQUITY','USD',TRUE,JSON_OBJECT(),:now,:now,0)
                """)
                .param("id", INSTRUMENT_ID)
                .param("now", Instant.now())
                .update();
        jdbc.sql(
                        """
                INSERT INTO app_user (id,email,password_hash,status,timezone,created_at,updated_at,version)
                VALUES (UUID_TO_BIN(:id),'polling-test@example.local','unused','ACTIVE','UTC',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)
                """)
                .param("id", USER_ID)
                .update();

        market.collectQuotes();
        assertThat(quoteCount(INSTRUMENT_ID)).isZero();
        assertThat(jdbc.sql(
                                "SELECT COUNT(*) FROM quote q JOIN instrument i ON i.id=q.instrument_id WHERE i.symbol IN ('SPY','QQQ')")
                        .query(Long.class)
                        .single())
                .isPositive();

        var ideaAdded = jdbc.sql(
                        """
                INSERT INTO investment_idea
                  (id,user_id,instrument_id,idea_created_at,cooldown_until,source_type,created_at)
                SELECT UUID_TO_BIN(:idea),u.id,UUID_TO_BIN(:instrument),UTC_TIMESTAMP(6),'2099-12-31 23:59:59',
                  'WATCHLIST',UTC_TIMESTAMP(6)
                FROM app_user u WHERE u.email='polling-test@example.local'
                """)
                .param("idea", UUID.randomUUID().toString())
                .param("instrument", INSTRUMENT_ID)
                .update();
        assertThat(added).isEqualTo(1);
        assertThat(ideaAdded).isEqualTo(1);
        assertThat(jdbc.sql(
                                "SELECT COUNT(*) FROM investment_idea WHERE instrument_id=UUID_TO_BIN(:id) AND source_type='WATCHLIST' AND active=TRUE")
                        .param("id", INSTRUMENT_ID)
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
        var result = market.collectQuotes();
        assertThat(result.observations()).isGreaterThanOrEqualTo(3);
        assertThat(quoteCount(INSTRUMENT_ID)).isEqualTo(1);
    }

    private long quoteCount(String instrumentId) {
        return jdbc.sql("SELECT COUNT(*) FROM quote WHERE instrument_id=UUID_TO_BIN(:id)")
                .param("id", instrumentId)
                .query(Long.class)
                .single();
    }
}
