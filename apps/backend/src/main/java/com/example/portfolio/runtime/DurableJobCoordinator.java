package com.example.portfolio.runtime;

import com.example.portfolio.market.MarketPipelineException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "portfolio.runtime-mode", havingValue = "worker")
public class DurableJobCoordinator {
    private static final Logger log = LoggerFactory.getLogger(DurableJobCoordinator.class);
    private final DurableJobStore jobs;
    private final JobHandlerRegistry handlers;
    private final AnalysisRunOrchestrator orchestrator;
    private final Clock clock;
    private final Counter succeeded;
    private final Counter failed;
    private final String workerId = "worker-" + UUID.randomUUID();

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
        var claimed = jobs.claim(workerId, Duration.ofMinutes(5));
        if (claimed.isEmpty()) return false;
        var job = claimed.orElseThrow();
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
            jobs.succeed(job, result);
            orchestrator.succeeded(job, result);
            log.info(
                    "event=job_completed jobId={} jobType={} attempt={}", job.id(), job.jobType(), job.attemptNumber());
            succeeded.increment();
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
        }
        return true;
    }

    private void retry(DurableJobStore.ClaimedJob job, String code) {
        boolean dead = jobs.failTransient(job, code);
        if (dead) orchestrator.failed(job, code);
        else orchestrator.retrying(job, code);
        log.warn("event=job_failed jobId={} jobType={} code={}", job.id(), job.jobType(), code);
        failed.increment();
    }

    private void permanent(DurableJobStore.ClaimedJob job, String code) {
        jobs.failPermanent(job, code);
        orchestrator.failed(job, code);
        log.warn("event=job_failed jobId={} jobType={} code={}", job.id(), job.jobType(), code);
        failed.increment();
    }

    private static String safeCode(String value) {
        if (value == null || !value.matches("[A-Z0-9_]{1,64}")) return "JOB_FAILURE";
        return value;
    }
}
