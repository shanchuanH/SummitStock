package com.example.portfolio.identity;

import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class AuthSecurityStore {
    private final JdbcClient jdbc;
    private final Clock clock;

    AuthSecurityStore(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    long recentFailures(String username, Duration window) {
        return jdbc.sql(
                        "SELECT COUNT(*) FROM auth_security_event WHERE username=:username AND event_type IN ('LOGIN_FAILED','LOGIN_RATE_LIMITED') AND occurred_at>=:since")
                .param("username", username)
                .param("since", clock.instant().minus(window))
                .query(Long.class)
                .single();
    }

    void record(String username, String type, String remoteAddress, String requestId) {
        jdbc.sql(
                        "INSERT INTO auth_security_event (id,username,event_type,remote_address,request_id,occurred_at) VALUES (UUID_TO_BIN(:id),:username,:type,:remote,:requestId,:now)")
                .param("id", UUID.randomUUID().toString())
                .param("username", username)
                .param("type", type)
                .param("remote", remoteAddress)
                .param("requestId", requestId)
                .param("now", clock.instant())
                .update();
    }
}
