package com.example.portfolio.market;

import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InstrumentResolutionService {
    private final JdbcClient jdbc;
    private final Clock clock;

    public InstrumentResolutionService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public Resolution resolve(String userEmail, String fidelitySymbol, String sourceAssetType) {
        var symbol = normalize(fidelitySymbol);
        var manual = jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(i.id), i.symbol, i.exchange, i.asset_type, i.cik, i.currency, i.active
                        FROM instrument_manual_mapping m
                        JOIN app_user u ON u.id=m.user_id
                        JOIN instrument i ON i.id=m.instrument_id
                        WHERE u.email=:email AND m.alias_symbol=:symbol
                        """)
                .param("email", userEmail)
                .param("symbol", symbol)
                .query(this::map)
                .optional();
        if (manual.isPresent()) return manual.orElseThrow();
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(i.id), i.symbol, i.exchange, i.asset_type, i.cik, i.currency, i.active
                        FROM instrument_alias a JOIN instrument i ON i.id=a.instrument_id
                        WHERE a.alias_symbol=:symbol
                        """)
                .param("symbol", symbol)
                .query(this::map)
                .optional()
                .orElseGet(() -> unresolved(symbol, sourceAssetType));
    }

    @Transactional
    public void saveManualMapping(String userEmail, String fidelitySymbol, UUID instrumentId) {
        var symbol = normalize(fidelitySymbol);
        int updated = jdbc.sql(
                        """
                        INSERT INTO instrument_manual_mapping (
                            user_id, alias_symbol, instrument_id, created_at, updated_at
                        )
                        SELECT u.id, :symbol, i.id, :now, :now
                        FROM app_user u JOIN instrument i ON i.id=UUID_TO_BIN(:instrumentId)
                        WHERE u.email=:email
                        ON DUPLICATE KEY UPDATE instrument_id=VALUES(instrument_id), updated_at=VALUES(updated_at)
                        """)
                .param("symbol", symbol)
                .param("instrumentId", instrumentId.toString())
                .param("email", userEmail)
                .param("now", clock.instant())
                .update();
        if (updated == 0) throw new IllegalArgumentException("Unknown user or target instrument");
        jdbc.sql(
                        """
                        UPDATE position p
                        JOIN investment_account a ON a.id=p.account_id
                        JOIN app_user u ON u.id=a.user_id
                        JOIN instrument i ON i.id=UUID_TO_BIN(:instrumentId)
                        SET p.instrument_id=i.id,
                            p.data_readiness=IF(i.active, 'RESOLVED', 'WAIT_FOR_DATA'),
                            p.updated_at=:now,
                            p.version=p.version+1
                        WHERE u.email=:email AND p.external_position_key=:symbol
                          AND p.status='OPEN' AND p.data_readiness='WAIT_FOR_DATA'
                        """)
                .param("instrumentId", instrumentId.toString())
                .param("now", clock.instant())
                .param("email", userEmail)
                .param("symbol", symbol)
                .update();
    }

    private Resolution unresolved(String symbol, String sourceAssetType) {
        var existing = jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(id), symbol, exchange, asset_type, cik, currency, active
                        FROM instrument WHERE symbol=:symbol AND exchange='UNRESOLVED'
                        """)
                .param("symbol", symbol)
                .query(this::map)
                .optional();
        if (existing.isPresent()) return existing.orElseThrow();
        var id = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO instrument (
                            id, symbol, exchange, asset_type, currency, cik, active, metadata,
                            created_at, updated_at, version
                        ) VALUES (
                            UUID_TO_BIN(:id), :symbol, 'UNRESOLVED', :assetType, 'USD', NULL, FALSE,
                            JSON_OBJECT('resolutionStatus','WAIT_FOR_DATA'), :now, :now, 0
                        )
                        ON DUPLICATE KEY UPDATE updated_at=updated_at
                        """)
                .param("id", id.toString())
                .param("symbol", symbol)
                .param("assetType", normalizeAssetType(sourceAssetType))
                .param("now", clock.instant())
                .update();
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(id), symbol, exchange, asset_type, cik, currency, active
                        FROM instrument WHERE symbol=:symbol AND exchange='UNRESOLVED'
                        """)
                .param("symbol", symbol)
                .query(this::map)
                .single();
    }

    private Resolution map(java.sql.ResultSet row, int ignored) throws java.sql.SQLException {
        boolean active = row.getBoolean(7);
        return new Resolution(
                UUID.fromString(row.getString(1)),
                row.getString(2),
                row.getString(3),
                row.getString(4),
                row.getString(5),
                row.getString(6),
                active,
                active ? Readiness.RESOLVED : Readiness.WAIT_FOR_DATA);
    }

    private static String normalize(String symbol) {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("Fidelity symbol is required");
        var normalized = symbol.strip().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9.\\-]{1,32}")) throw new IllegalArgumentException("Invalid Fidelity symbol");
        return normalized;
    }

    private static String normalizeAssetType(String value) {
        if (value == null) return "EQUITY";
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "ETF", "MUTUAL_FUND", "EQUITY" -> value.toUpperCase(Locale.ROOT);
            default -> "EQUITY";
        };
    }

    public enum Readiness {
        RESOLVED,
        WAIT_FOR_DATA
    }

    public record Resolution(
            UUID instrumentId,
            String canonicalSymbol,
            String exchange,
            String assetType,
            String cik,
            String currency,
            boolean active,
            Readiness readiness) {}
}
