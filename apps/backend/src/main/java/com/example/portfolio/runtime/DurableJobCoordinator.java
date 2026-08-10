package com.example.portfolio.runtime;

import com.example.portfolio.market.MarketPipelineException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "portfolio.runtime-mode", havingValue = "worker")
public class DurableJobCoordinator {
    private static final Logger log = LoggerFactory.getLogger(DurableJobCoordinator.class);
    private static final Duration LEASE = Duration.ofMinutes(5);
    private static final long HEARTBEAT_SECONDS = 30;
    private final DurableJobStore jobs;
    private final JobHandlerRegistry handlers;
    private final AnalysisRunOrchestrator orchestrator;
    private final Clock clock;
    private final Counter succeeded;
    private final Counter failed;
    private final String workerId = "worker-" + UUID.randomUUID();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
        var thread = new Thread(runnable, "portfolio-job-lease-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public DurableJobCoordinator(
            DurableJobStore jobs,
            JobHandlerRegistry handlers,
            AnalysisRunOrchestrator orchestrator,
            MeterRegistry meters,
            Clock clock) {
        this.jobs = jobs;
        this.handlers = handlers;
        this.orchestrator = orchestrator;
        this.clock = clock;
        this.succeeded = meters.counter("portfolio.worker.jobs", "outcome", "succeeded");
        this.failed = meters.counter("portfolio.worker.jobs", "outcome", "failed");
        meters.gauge("portfolio.worker.pending", jobs, DurableJobStore::pendingCount);
        meters.gauge("portfolio.worker.dead", jobs, DurableJobStore::deadCount);
    }

    @Scheduled(fixedDelayString = "${portfolio.worker.scan-delay-ms:1000}")
    void scan() {
        runOnce();
    }

    public boolean runOnce() {
        var claimed = jobs.claim(workerId, LEASE);
        if (claimed.isEmpty()) return false;
        var job = claimed.orElseThrow();
        var leaseLost = new AtomicBoolean();
        var heartbeat = startHeartbeat(job, leaseLost);
        try {
            orchestrator.started(job);
            var handler = handlers.require(job.jobType());
            var result = handler.execute(new JobExecutionContext(
                    job.id(),
                    job.analysisRunId(),
                    job.idempotencyKey(),
                    job.payload(),
                    job.attemptNumber(),
                    job.startedAt(),
                    job.workerId()));
            if (leaseLost.get()) throw new DurableJobStore.LeaseLostException(job.id(), job.workerId());
            jobs.succeed(job, result);
            orchestrator.succeeded(job, result);
            log.info(
                    "event=job_completed jobId={} jobType={} attempt={}", job.id(), job.jobType(), job.attemptNumber());
            succeeded.increment();
        } catch (DurableJobStore.LeaseLostException exception) {
            fenced(job);
        } catch (TransientProviderException exception) {
            retry(job, safeCode(exception.code()));
        } catch (MarketPipelineException exception) {
            if (exception.retryable()) retry(job, safeCode(exception.code()));
            else permanent(job, safeCode(exception.code()));
        } catch (PermanentDataException exception) {
            permanent(job, safeCode(exception.code()));
        } catch (RuntimeException exception) {
            log.error(
                    "event=job_unexpected_failure jobId={} jobType={} exceptionType={}",
                    job.id(),
                    job.jobType(),
                    exception.getClass().getName(),
                    exception);
            permanent(job, "UNEXPECTED_JOB_FAILURE");
        } finally {
            heartbeat.cancel(false);
        }
        return true;
    }

    private ScheduledFuture<?> startHeartbeat(DurableJobStore.ClaimedJob job, AtomicBoolean leaseLost) {
        return heartbeatExecutor.scheduleAtFixedRate(
                () -> {
                    try {
                        if (!jobs.heartbeat(job, LEASE)) leaseLost.set(true);
                    } catch (RuntimeException exception) {
                        log.error(
                                "event=job_heartbeat_failed jobId={} workerId={}", job.id(), job.workerId(), exception);
                    }
                },
                HEARTBEAT_SECONDS,
                HEARTBEAT_SECONDS,
                TimeUnit.SECONDS);
    }

    private void retry(DurableJobStore.ClaimedJob job, String code) {
        boolean dead;
        try {
            dead = jobs.failTransient(job, code);
        } catch (DurableJobStore.LeaseLostException exception) {
            fenced(job);
            return;
        }
        if (dead) orchestrator.failed(job, code);
        else orchestrator.retrying(job, code);
        log.warn("event=job_failed jobId={} jobType={} code={}", job.id(), job.jobType(), code);
        failed.increment();
    }

    private void permanent(DurableJobStore.ClaimedJob job, String code) {
        try {
            jobs.failPermanent(job, code);
        } catch (DurableJobStore.LeaseLostException exception) {
            fenced(job);
            return;
        }
        orchestrator.failed(job, code);
        log.warn("event=job_failed jobId={} jobType={} code={}", job.id(), job.jobType(), code);
        failed.increment();
    }

    private void fenced(DurableJobStore.ClaimedJob job) {
        log.warn(
                "event=job_completion_fenced jobId={} jobType={} workerId={} leaseToken={}",
                job.id(),
                job.jobType(),
                job.workerId(),
                job.leaseToken());
    }

    @PreDestroy
    void shutdownHeartbeatExecutor() {
        heartbeatExecutor.shutdownNow();
    }

    private static String safeCode(String value) {
        if (value == null || !value.matches("[A-Z0-9_]{1,64}")) return "JOB_FAILURE";
        return value;
    }
}
