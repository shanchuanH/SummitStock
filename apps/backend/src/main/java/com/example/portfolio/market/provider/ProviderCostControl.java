package com.example.portfolio.market.provider;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderCostControl {
    private final JdbcClient jdbc;
    private final Clock clock;
    private final long dailyLimit;

    public ProviderCostControl(
            JdbcClient jdbc, Clock clock, @Value("${portfolio.providers.daily-request-limit:100000}") long dailyLimit) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.dailyLimit = Math.max(1, dailyLimit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Decision acquire(String providerId, String operation) {
        var usageDate = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        var used = jdbc.sql(
                        "SELECT COALESCE(SUM(request_count),0) FROM provider_usage_daily WHERE provider_id=:provider AND usage_date=:day")
                .param("provider", providerId)
                .param("day", usageDate)
                .query(Long.class)
                .single();
        var priority = priority(operation);
        var decision = decide(used, dailyLimit, priority);
        if (!decision.allowed()) throw new ProviderQuotaDeferredException(providerId, operation, decision.reason());
        jdbc.sql(
                        """
                        INSERT INTO provider_usage_daily
                          (provider_id,usage_date,operation,priority,request_count,last_requested_at)
                        VALUES (:provider,:day,:operation,:priority,1,:now)
                        AS incoming
                        ON DUPLICATE KEY UPDATE request_count=provider_usage_daily.request_count+1,
                          priority=incoming.priority,last_requested_at=incoming.last_requested_at
                        """)
                .param("provider", providerId)
                .param("day", usageDate)
                .param("operation", operation)
                .param("priority", priority.name())
                .param("now", clock.instant())
                .update();
        return decision;
    }

    public static Decision decide(long used, long limit, Priority priority) {
        double utilization = limit <= 0 ? 1d : (double) used / limit;
        boolean allowed = utilization < 0.9d
                || priority == Priority.P0
                || priority == Priority.P1
                || (utilization < 1d && priority == Priority.P2);
        return new Decision(
                allowed,
                priority,
                utilization,
                allowed ? "ALLOWED" : utilization >= 1d ? "DAILY_QUOTA_EXHAUSTED" : "OPTIONAL_WORK_PAUSED");
    }

    public static Priority priority(String operation) {
        var normalized = operation == null ? "" : operation.toLowerCase();
        if (normalized.contains("quote") || normalized.contains("bar") || normalized.contains("price"))
            return Priority.P0;
        if (normalized.contains("fundamental")
                || normalized.contains("filing")
                || normalized.contains("estimate")
                || normalized.contains("earning")) return Priority.P1;
        if (normalized.contains("etf")) return Priority.P2;
        if (normalized.contains("watch")) return Priority.P3;
        return Priority.P4;
    }

    public enum Priority {
        P0,
        P1,
        P2,
        P3,
        P4
    }

    public record Decision(boolean allowed, Priority priority, double utilization, String reason) {}
}
