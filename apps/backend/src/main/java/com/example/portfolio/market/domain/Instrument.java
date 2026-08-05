package com.example.portfolio.market.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "instrument",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_instrument_symbol_exchange",
                        columnNames = {"symbol", "exchange"}))
public class Instrument {
    @Id
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(nullable = false, length = 32)
    private String exchange;

    @Column(name = "asset_type", nullable = false, length = 32)
    private String assetType;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 10)
    private String cik;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, columnDefinition = "JSON")
    private String metadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Instrument() {}

    public Instrument(UUID id, String symbol, String exchange, String assetType, String currency, String cik) {
        this.id = id;
        this.symbol = symbol;
        this.exchange = exchange;
        this.assetType = assetType;
        this.currency = currency;
        this.cik = cik;
        this.active = true;
        this.metadata = "{}";
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public UUID id() {
        return id;
    }

    public String symbol() {
        return symbol;
    }

    public String exchange() {
        return exchange;
    }

    public String assetType() {
        return assetType;
    }

    public String currency() {
        return currency;
    }

    public String cik() {
        return cik;
    }

    public boolean active() {
        return active;
    }

    public long version() {
        return version;
    }
}
