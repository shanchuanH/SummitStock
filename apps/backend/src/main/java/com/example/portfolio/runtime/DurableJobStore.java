package com.example.portfolio.runtime;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class DurableJobStore {
    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public DurableJobStore(JdbcClient jdbc, TransactionTemplate transactions, Clock clock) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.clock = clock;
    }

    public boolean enqueue(String jobType, String idempotencyKey, String payload, int priority, Instant scheduledAt) {
        return jdbc.sql(
                                """
                        INSERT IGNORE INTO job_run (id, job_type, idempotency_key, payload, status, priority,
                            scheduled_at, created_at, updated_at, version)
                        VALUES (UUID_TO_BIN(:id), :jobType, :key, CAST(:payload AS JSON), 'PENDING', :priority,
                            :scheduledAt, :now, :now, 0)
                        """)
                        .param("id", UUID.randomUUID().toString())
                        .param("jobType", jobType)
                        .param("key", idempotencyKey)
                        .param("payload", payload)
                        .param("priority", priority)
                        .param("scheduledAt", scheduledAt)
                        .param("now", clock.instant())
                        .update()
                == 1;
    }

    public Optional<ClaimedJob> claim(String workerId, Duration lease) {
        return transactions.execute(status -> {
            recoverExpiredLeases();
            var candidate = jdbc.sql(
                            """
                            SELECT BIN_TO_UUID(id) id, job_type, payload, attempt_count, max_attempts
                            FROM job_run
                            WHERE status='PENDING' AND scheduled_at <= :now
                            ORDER BY priority DESC, scheduled_at
                            LIMIT 1 FOR UPDATE SKIP LOCKED
                            """)
                    .param("now", clock.instant())
                    .query(Candidate.class)
                    .optional();
            if (candidate.isEmpty()) return Optional.<ClaimedJob>empty();
            var value = candidate.orElseThrow();
            var attempt = value.attemptCount() + 1;
            jdbc.sql(
                            """
                    UPDATE job_run SET status='RUNNING', attempt_count=:attempt, lease_owner=:worker,
                        lease_expires_at=:expires, updated_at=:now, version=version+1
                    WHERE id=UUID_TO_BIN(:id)
                    """)
                    .param("attempt", attempt)
                    .param("worker", workerId)
                    .param("expires", clock.instant().plus(lease))
                    .param("now", clock.instant())
                    .param("id", value.id().toString())
                    .update();
            var attemptId = UUID.randomUUID();
            jdbc.sql(
                            """
                    INSERT INTO job_attempt (id, job_run_id, attempt_number, worker_id, status, started_at)
                    VALUES (UUID_TO_BIN(:attemptId), UUID_TO_BIN(:jobId), :attempt, :worker, 'RUNNING', :now)
                    """)
                    .param("attemptId", attemptId.toString())
                    .param("jobId", value.id().toString())
                    .param("attempt", attempt)
                    .param("worker", workerId)
                    .param("now", clock.instant())
                    .update();
            return Optional.of(new ClaimedJob(
                    value.id(), attemptId, value.jobType(), value.payload(), attempt, value.maxAttempts()));
        });
    }

    public void succeed(ClaimedJob job) {
        complete(job, "SUCCEEDED", null, null);
    }

    public void fail(ClaimedJob job, String errorCode) {
        var dead = job.attemptNumber() >= job.maxAttempts();
        complete(
                job,
                dead ? "DEAD" : "PENDING",
                errorCode,
                dead ? null : clock.instant().plusSeconds(1L << Math.min(job.attemptNumber(), 10)));
    }

    public long pendingCount() {
        return jdbc.sql("SELECT COUNT(*) FROM job_run WHERE status='PENDING'")
                .query(Long.class)
                .single();
    }

    public long deadCount() {
        return jdbc.sql("SELECT COUNT(*) FROM job_run WHERE status='DEAD'")
                .query(Long.class)
                .single();
    }

    private void recoverExpiredLeases() {
        jdbc.sql(
                        """
                UPDATE job_run SET status='PENDING', lease_owner=NULL, lease_expires_at=NULL,
                    scheduled_at=:now, updated_at=:now, version=version+1
                WHERE status='RUNNING' AND lease_expires_at < :now
                """)
                .param("now", clock.instant())
                .update();
    }

    private void complete(ClaimedJob job, String status, String errorCode, Instant scheduledAt) {
        transactions.executeWithoutResult(ignored -> {
            jdbc.sql(
                            """
                    UPDATE job_attempt SET status=:attemptStatus, finished_at=:now, error_code=:error
                    WHERE id=UUID_TO_BIN(:attemptId)
                    """)
                    .param("attemptStatus", status.equals("SUCCEEDED") ? "SUCCEEDED" : "FAILED")
                    .param("now", clock.instant())
                    .param("error", errorCode)
                    .param("attemptId", job.attemptId().toString())
                    .update();
            jdbc.sql(
                            """
                    UPDATE job_run SET status=:status, lease_owner=NULL, lease_expires_at=NULL,
                        last_error_code=:error, scheduled_at=COALESCE(:scheduledAt, scheduled_at), updated_at=:now, version=version+1
                    WHERE id=UUID_TO_BIN(:jobId)
                    """)
                    .param("status", status)
                    .param("error", errorCode)
                    .param("scheduledAt", scheduledAt)
                    .param("now", clock.instant())
                    .param("jobId", job.id().toString())
                    .update();
        });
    }

    record Candidate(UUID id, String jobType, String payload, int attemptCount, int maxAttempts) {}

    public record ClaimedJob(
            UUID id, UUID attemptId, String jobType, String payload, int attemptNumber, int maxAttempts) {}
}
