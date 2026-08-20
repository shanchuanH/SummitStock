package com.example.portfolio.runtime;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class WorkerHeartbeatStore {
    private final JdbcClient jdbc;
    private final Clock clock;

    WorkerHeartbeatStore(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    void heartbeat(String workerId, String runtimeVersion, Instant startedAt, String hostHint, String status) {
        jdbc.sql(
                        """
                        INSERT INTO worker_runtime_heartbeat (
                            worker_id,runtime_version,started_at,last_seen_at,host_hint,status
                        ) VALUES (:workerId,:runtimeVersion,:startedAt,:now,:hostHint,:status)
                        ON DUPLICATE KEY UPDATE runtime_version=VALUES(runtime_version),
                            last_seen_at=VALUES(last_seen_at),host_hint=VALUES(host_hint),status=VALUES(status)
                        """)
                .param("workerId", workerId)
                .param("runtimeVersion", runtimeVersion)
                .param("startedAt", startedAt)
                .param("now", clock.instant())
                .param("hostHint", hostHint)
                .param("status", status)
                .update();
    }

    RuntimeSnapshot snapshot() {
        var worker = jdbc.sql(
                        """
                        SELECT worker_id workerId,runtime_version runtimeVersion,started_at startedAt,
                               last_seen_at lastSeenAt,host_hint hostHint,status
                        FROM worker_runtime_heartbeat ORDER BY last_seen_at DESC LIMIT 1
                        """)
                .query(WorkerRow.class)
                .optional();
        var counts = jdbc.sql(
                        """
                        SELECT SUM(status='PENDING') pendingJobs,SUM(status='RUNNING') runningJobs,
                               SUM(status='DEAD') deadJobs,MIN(IF(status='PENDING',scheduled_at,NULL)) oldestPendingAt
                        FROM job_run
                        """)
                .query(JobCounts.class)
                .single();
        var lastSeen = worker.map(value -> value.lastSeenAt().toInstant(ZoneOffset.UTC))
                .orElse(null);
        var alive = worker.filter(value -> "ONLINE".equals(value.status()))
                .map(value -> value.lastSeenAt()
                        .toInstant(ZoneOffset.UTC)
                        .isAfter(clock.instant().minusSeconds(60)))
                .orElse(false);
        var oldest = counts.oldestPendingAt() == null
                ? null
                : Math.max(
                        0,
                        java.time.Duration.between(counts.oldestPendingAt().toInstant(ZoneOffset.UTC), clock.instant())
                                .toSeconds());
        return new RuntimeSnapshot(
                alive,
                lastSeen,
                counts.pendingJobs() == null ? 0 : counts.pendingJobs(),
                counts.runningJobs() == null ? 0 : counts.runningJobs(),
                counts.deadJobs() == null ? 0 : counts.deadJobs(),
                oldest);
    }

    record WorkerRow(
            String workerId,
            String runtimeVersion,
            LocalDateTime startedAt,
            LocalDateTime lastSeenAt,
            String hostHint,
            String status) {}

    record JobCounts(Long pendingJobs, Long runningJobs, Long deadJobs, LocalDateTime oldestPendingAt) {}

    record RuntimeSnapshot(
            boolean workerAlive,
            Instant lastHeartbeat,
            long pendingJobs,
            long runningJobs,
            long deadJobs,
            Long oldestPendingSeconds) {}
}
