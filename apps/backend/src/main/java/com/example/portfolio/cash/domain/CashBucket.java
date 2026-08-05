package com.example.portfolio.cash.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "cash_bucket")
public class CashBucket {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID userId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "account_id", columnDefinition = "BINARY(16)")
    private UUID accountId;

    @Column(name = "bucket_type", nullable = false, length = 32)
    private String bucketType;

    @Column(name = "target_amount", nullable = false, precision = 24, scale = 8)
    private BigDecimal targetAmount;

    @Column(name = "current_amount", nullable = false, precision = 24, scale = 8)
    private BigDecimal currentAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "as_of", nullable = false)
    private LocalDate asOf;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected CashBucket() {}
}
