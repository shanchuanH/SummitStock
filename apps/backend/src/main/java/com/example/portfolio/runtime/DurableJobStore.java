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
        return enqueue(jobType, idempotencyKey, payload, priority, scheduledAt, null);
    }

    public boolean enqueue(
            String jobType,
            String idempotencyKey,
            String payload,
            int priority,
            Instant scheduledAt,
            UUID analysisRunId) {
        return jdbc.sql(
                                """
                        INSERT IGNORE INTO job_run (id, job_type, idempotency_key, payload, status, priority, analysis_run_id,
                            scheduled_at, created_at, updated_at, version)
                        VALUES (UUID_TO_BIN(:id), :jobType, :key, CAST(:payload AS JSON), 'PENDING', :priority,
                            UUID_TO_BIN(:analysisRunId),
                            :scheduledAt, :now, :now, 0)
                        """)
                        .param("id", UUID.randomUUID().toString())
                        .param("jobType", jobType)
                        .param("key", idempotencyKey)
                        .param("payload", payload)
                        .param("priority", priority)
                        .param("analysisRunId", analysisRunId == null ? null : analysisRunId.toString())
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
                            SELECT BIN_TO_UUID(id) id, BIN_TO_UUID(analysis_run_id) analysis_run_id,
                                   job_type, idempotency_key, payload, attempt_count, max_attempts
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
            var leaseToken = UUID.randomUUID();
            var claimed = jdbc.sql(
                            """
                    UPDATE job_run SET status='RUNNING', attempt_count=:attempt, lease_owner=:worker,
                        lease_token=UUID_TO_BIN(:token), lease_expires_at=:expires, updated_at=:now, version=version+1
                    WHERE id=UUID_TO_BIN(:id) AND status='PENDING'
                    """)
                    .param("attempt", attempt)
                    .param("worker", workerId)
                    .param("token", leaseToken.toString())
                    .param("expires", clock.instant().plus(lease))
                    .param("now", clock.instant())
                    .param("id", value.id().toString())
                    .update();
            if (claimed != 1) return Optional.<ClaimedJob>empty();
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
                    value.id(),
                    attemptId,
                    value.analysisRunId(),
                    value.jobType(),
                    value.idempotencyKey(),
                    value.payload(),
                    attempt,
                    value.maxAttempts(),
                    clock.instant(),
                    workerId,
                    leaseToken));
        });
    }

    public boolean heartbeat(ClaimedJob job, Duration lease) {
        var now = clock.instant();
        return jdbc.sql(
                                """
                        UPDATE job_run SET lease_expires_at=:expires, updated_at=:now, version=version+1
                        WHERE id=UUID_TO_BIN(:id) AND status='RUNNING' AND lease_owner=:worker
                          AND lease_token=UUID_TO_BIN(:token) AND lease_expires_at >= :now
                        """)
                        .param("expires", now.plus(lease))
                        .param("now", now)
                        .param("id", job.id().toString())
                        .param("worker", job.workerId())
                        .param("token", job.leaseToken().toString())
                        .update()
                == 1;
    }

    public void succeed(ClaimedJob job, JobExecutionResult result) {
        complete(job, "SUCCEEDED", null, null, result);
    }

    public void fail(ClaimedJob job, String errorCode) {
        failTransient(job, errorCode);
    }

    public boolean failTransient(ClaimedJob job, String errorCode) {
        var dead = job.attemptNumber() >= job.maxAttempts();
        complete(
                job,
                dead ? "DEAD" : "PENDING",
                errorCode,
                dead ? null : clock.instant().plusSeconds(1L << Math.min(job.attemptNumber(), 10)),
                null);
        return dead;
    }

    public void failPermanent(ClaimedJob job, String errorCode) {
        complete(job, "DEAD", errorCode, null, null);
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
                UPDATE job_attempt a JOIN job_run j ON j.id=a.job_run_id
                SET a.status='FAILED', a.finished_at=:now, a.error_code='LEASE_EXPIRED',
                    a.duration_ms=TIMESTAMPDIFF(MICROSECOND, a.started_at, :now)/1000
                WHERE j.status='RUNNING' AND j.lease_expires_at < :now AND a.status='RUNNING'
                """)
                .param("now", clock.instant())
                .update();
        jdbc.sql(
                        """
                UPDATE job_run SET status='PENDING', lease_owner=NULL, lease_token=NULL, lease_expires_at=NULL,
                    scheduled_at=:now, updated_at=:now, version=version+1
                WHERE status='RUNNING' AND lease_expires_at < :now
                """)
                .param("now", clock.instant())
                .update();
    }

    private void complete(
            ClaimedJob job, String status, String errorCode, Instant scheduledAt, JobExecutionResult result) {
        transactions.executeWithoutResult(ignored -> {
            var now = clock.instant();
            var warnings = result == null ? null : jsonArray(result.warnings());
            var updated = jdbc.sql(
                            """
                    UPDATE job_run SET status=:status, lease_owner=NULL, lease_token=NULL, lease_expires_at=NULL,
                        last_error_code=:error, scheduled_at=COALESCE(:scheduledAt, scheduled_at),
                        result_json=CAST(:result AS JSON), warnings=CAST(:warnings AS JSON), data_as_of=:dataAsOf,
                        updated_at=:now, version=version+1
                    WHERE id=UUID_TO_BIN(:jobId) AND status='RUNNING' AND lease_owner=:worker
                      AND lease_token=UUID_TO_BIN(:token) AND lease_expires_at >= :now
                    """)
                    .param("status", status)
                    .param("error", errorCode)
                    .param("scheduledAt", scheduledAt)
                    .param("result", result == null ? null : result.resultJson())
                    .param("warnings", warnings)
                    .param("dataAsOf", result == null ? null : result.dataAsOf())
                    .param("now", now)
                    .param("jobId", job.id().toString())
                    .param("worker", job.workerId())
                    .param("token", job.leaseToken().toString())
                    .update();
            if (updated != 1) throw new LeaseLostException(job.id(), job.workerId());
            if (result == null) {
                jdbc.sql(
                                """
                        UPDATE job_attempt SET status='FAILED', finished_at=:now, error_code=:error,
                            duration_ms=TIMESTAMPDIFF(MICROSECOND, started_at, :now)/1000
                        WHERE id=UUID_TO_BIN(:attemptId)
                        """)
                        .param("now", now)
                        .param("error", errorCode)
                        .param("attemptId", job.attemptId().toString())
                        .update();
                return;
            }
            jdbc.sql(
                            """
                    UPDATE job_attempt SET status='SUCCEEDED', finished_at=:now, error_code=NULL,
                        duration_ms=TIMESTAMPDIFF(MICROSECOND, started_at, :now)/1000,
                        result_json=CAST(:result AS JSON), warnings=CAST(:warnings AS JSON), data_as_of=:dataAsOf
                    WHERE id=UUID_TO_BIN(:attemptId)
                    """)
                    .param("now", now)
                    .param("result", result.resultJson())
                    .param("warnings", warnings)
                    .param("dataAsOf", result.dataAsOf())
                    .param("attemptId", job.attemptId().toString())
                    .update();
        });
    }

    private static String jsonArray(java.util.List<String> values) {
        return values.stream()
                .map(value -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    record Candidate(
            UUID id,
            UUID analysisRunId,
            String jobType,
            String idempotencyKey,
            String payload,
            int attemptCount,
            int maxAttempts) {}

    public record ClaimedJob(
            UUID id,
            UUID attemptId,
            UUID analysisRunId,
            String jobType,
            String idempotencyKey,
            String payload,
            int attemptNumber,
            int maxAttempts,
            Instant startedAt,
            String workerId,
            UUID leaseToken) {}

    public static final class LeaseLostException extends RuntimeException {
        LeaseLostException(UUID jobId, String workerId) {
            super("JOB_LEASE_LOST jobId=" + jobId + " workerId=" + workerId);
        }
    }
}
