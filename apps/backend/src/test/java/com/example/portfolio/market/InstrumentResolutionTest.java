package com.example.portfolio.market;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.MySqlIntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class InstrumentResolutionTest extends MySqlIntegrationTest {
    private static final String EMAIL = "instrument-resolution@example.local";

    @Autowired
    private InstrumentResolutionService resolution;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void createOwner() {
        jdbc.sql(
                        """
                        INSERT IGNORE INTO app_user (
                            id, email, password_hash, status, timezone, created_at, updated_at, version
                        ) VALUES (
                            UUID_TO_BIN('00000000-0000-0000-0000-000000009901'), :email, 'unused',
                            'ACTIVE', 'UTC', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0
                        )
                        """)
                .param("email", EMAIL)
                .update();
    }

    @Test
    void resolvesRequiredFidelityUniverseAndShareClasses() {
        var email = EMAIL;
        for (String symbol : List.of(
                "GOOGL", "GOOG", "MSFT", "QQQM", "VGT", "VOO", "DRAM", "DXYZ", "NOK", "AAOI", "CSIQ", "TSLA", "SNDK",
                "NVDA", "SPAXX")) {
            assertThat(resolution.resolve(email, symbol.toLowerCase(), "EQUITY").readiness())
                    .as(symbol)
                    .isEqualTo(InstrumentResolutionService.Readiness.RESOLVED);
        }
        assertThat(resolution.resolve(email, "GOOGL", "EQUITY").cik()).isEqualTo("0001652044");
        assertThat(resolution.resolve(email, "GOOG", "EQUITY").instrumentId())
                .isNotEqualTo(resolution.resolve(email, "GOOGL", "EQUITY").instrumentId());
        assertThat(resolution.resolve(email, "SPAXX", "MUTUAL_FUND").assetType())
                .isEqualTo("MUTUAL_FUND");
    }

    @Test
    void unknownSymbolWaitsWithoutBlockingAndCanBeManuallyMapped() {
        var email = EMAIL;
        var unresolved = resolution.resolve(email, "NEWCO", "EQUITY");
        assertThat(unresolved.readiness()).isEqualTo(InstrumentResolutionService.Readiness.WAIT_FOR_DATA);
        assertThat(unresolved.active()).isFalse();

        UUID msft = jdbc.sql("SELECT BIN_TO_UUID(id) FROM instrument WHERE symbol='MSFT' AND exchange='XNAS'")
                .query(UUID.class)
                .single();
        resolution.saveManualMapping(email, "NEWCO", msft);
        var mapped = resolution.resolve(email, "NEWCO", "EQUITY");
        assertThat(mapped.instrumentId()).isEqualTo(msft);
        assertThat(mapped.readiness()).isEqualTo(InstrumentResolutionService.Readiness.RESOLVED);
    }
}
