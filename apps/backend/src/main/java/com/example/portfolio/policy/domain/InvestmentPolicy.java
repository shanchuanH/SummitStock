package com.example.portfolio.policy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "investment_policy")
public class InvestmentPolicy {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(columnDefinition = "BINARY(16)")
    private UUID id;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "user_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID userId;

    @Column(name = "strategy_version_code", nullable = false, length = 64)
    private String strategyVersionCode;

    @Column(name = "emergency_cash_floor", nullable = false, precision = 24, scale = 8)
    private BigDecimal emergencyCashFloor;

    @Column(name = "monthly_take_home", nullable = false, precision = 24, scale = 8)
    private BigDecimal monthlyTakeHome;

    @Column(name = "monthly_expenses", nullable = false, precision = 24, scale = 8)
    private BigDecimal monthlyExpenses;

    @Column(name = "monthly_surplus", nullable = false, precision = 24, scale = 8)
    private BigDecimal monthlySurplus;

    @Column(name = "include_unvested_compensation", nullable = false)
    private boolean includeUnvestedCompensation;

    @Column(name = "manual_execution_only", nullable = false)
    private boolean manualExecutionOnly;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected InvestmentPolicy() {}
}
