package com.example.portfolio.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.MySqlIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class BreadthServiceIntegrationTest extends MySqlIntegrationTest {
    private static final LocalDate MARKET_DATE = LocalDate.parse("2026-08-05");
    private static final UUID RISING = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID FALLING = UUID.fromString("a1000000-0000-0000-0000-000000000002");
    private static final UUID PORTFOLIO_ONLY = UUID.fromString("a1000000-0000-0000-0000-000000000003");

    @Autowired
    BreadthService breadth;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void seedPointInTimeUniverse() {
        cleanup();
        insertInstrument(RISING, "BRUP");
        insertInstrument(FALLING, "BRDN");
        insertInstrument(PORTFOLIO_ONLY, "PORTONLY");
        jdbc.sql("UPDATE breadth_universe SET expected_member_count=2 WHERE universe_code IN ('SP500','NASDAQ100')")
                .update();
        for (var universe :
                new String[] {"00000000-0000-0000-0000-000000000500", "00000000-0000-0000-0000-000000000501"}) {
            insertMember(universe, RISING, MARKET_DATE.minusYears(1), null);
            insertMember(universe, FALLING, MARKET_DATE.minusYears(1), null);
            insertMember(universe, PORTFOLIO_ONLY, MARKET_DATE.minusYears(1), MARKET_DATE.minusDays(1));
        }
        for (int offset = 199; offset >= 0; offset--) {
            var date = MARKET_DATE.minusDays(offset);
            insertBar(RISING, date, BigDecimal.valueOf(300L - offset));
            insertBar(FALLING, date, BigDecimal.valueOf(100L + offset));
            insertBar(PORTFOLIO_ONLY, date, BigDecimal.valueOf(500L - offset));
        }
    }

    @AfterEach
    void cleanup() {
        jdbc.sql("DELETE FROM breadth_snapshot WHERE universe_code IN ('SP500','NASDAQ100')")
                .update();
        jdbc.sql(
                        "DELETE FROM breadth_universe_member WHERE instrument_id IN (UUID_TO_BIN(:rising),UUID_TO_BIN(:falling),UUID_TO_BIN(:portfolio))")
                .param("rising", RISING.toString())
                .param("falling", FALLING.toString())
                .param("portfolio", PORTFOLIO_ONLY.toString())
                .update();
        jdbc.sql(
                        "DELETE FROM price_bar WHERE instrument_id IN (UUID_TO_BIN(:rising),UUID_TO_BIN(:falling),UUID_TO_BIN(:portfolio))")
                .param("rising", RISING.toString())
                .param("falling", FALLING.toString())
                .param("portfolio", PORTFOLIO_ONLY.toString())
                .update();
        jdbc.sql(
                        "DELETE FROM instrument WHERE id IN (UUID_TO_BIN(:rising),UUID_TO_BIN(:falling),UUID_TO_BIN(:portfolio))")
                .param("rising", RISING.toString())
                .param("falling", FALLING.toString())
                .param("portfolio", PORTFOLIO_ONLY.toString())
                .update();
        jdbc.sql(
                        "UPDATE breadth_universe SET expected_member_count=CASE universe_code WHEN 'SP500' THEN 500 ELSE 100 END WHERE universe_code IN ('SP500','NASDAQ100')")
                .update();
    }

    @Test
    void canonicalBreadthUsesPointInTimeMembersAndIgnoresPortfolioOnlyInstruments() {
        var capture = breadth.capture(MARKET_DATE);
        var composite = breadth.latest(MARKET_DATE);

        assertThat(capture.snapshots()).isEqualTo(2);
        assertThat(composite.quality()).isEqualTo("HEALTHY");
        assertThat(composite.pctAboveSma50()).isEqualByComparingTo("0.5");
        assertThat(composite.pctAboveSma200()).isEqualByComparingTo("0.5");
        assertThat(jdbc.sql(
                                "SELECT member_count FROM breadth_snapshot WHERE universe_code='SP500' AND market_date=:date")
                        .param("date", MARKET_DATE)
                        .query(Integer.class)
                        .single())
                .isEqualTo(2);
    }

    private void insertInstrument(UUID id, String symbol) {
        jdbc.sql(
                        """
                        INSERT INTO instrument
                          (id,symbol,exchange,asset_type,currency,active,metadata,created_at,updated_at)
                        VALUES (UUID_TO_BIN(:id),:symbol,'TEST','EQUITY','USD',TRUE,JSON_OBJECT(),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                        """)
                .param("id", id.toString())
                .param("symbol", symbol)
                .update();
    }

    private void insertMember(String universeId, UUID instrumentId, LocalDate from, LocalDate to) {
        jdbc.sql(
                        """
                        INSERT INTO breadth_universe_member
                          (id,universe_id,instrument_id,valid_from,valid_to,source,evidence_checksum,data_as_of,created_at)
                        VALUES (UUID_TO_BIN(:id),UUID_TO_BIN(:universeId),UUID_TO_BIN(:instrumentId),:validFrom,:validTo,
                          'TEST',SHA2(CONCAT(:universeId,:instrumentId,:validFrom),256),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("universeId", universeId)
                .param("instrumentId", instrumentId.toString())
                .param("validFrom", from)
                .param("validTo", to)
                .update();
    }

    private void insertBar(UUID instrumentId, LocalDate date, BigDecimal close) {
        jdbc.sql(
                        """
                        INSERT INTO price_bar
                          (id,instrument_id,timeframe,bar_start,market_date,open_price,high_price,low_price,close_price,
                           volume,adjusted,provider,source_timestamp,checksum,normalization_version,quality_status,data_as_of,created_at)
                        VALUES (UUID_TO_BIN(:id),UUID_TO_BIN(:instrumentId),'1D',:date,:date,:close,:close,:close,:close,
                          1000,TRUE,'TEST',:date,SHA2(CONCAT(:instrumentId,:date),256),'test','HEALTHY',:date,:date)
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("instrumentId", instrumentId.toString())
                .param("date", date)
                .param("close", close)
                .update();
    }
}
