package com.example.portfolio.runtime;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "portfolio.runtime-mode", havingValue = "worker")
class DurableJobCoordinator {
    private static final Logger log = LoggerFactory.getLogger(DurableJobCoordinator.class);
    private static final Set<String> SUPPORTED = Set.of(
            "COLLECT_QUOTES",
            "COLLECT_BARS",
            "VALIDATE_BARS",
            "COLLECT_CORPORATE_ACTIONS",
            "COMPUTE_INDICATORS",
            "COLLECT_BREADTH_MACRO",
            "COMPUTE_REGIME",
            "SYNC_PORTFOLIO",
            "COMPUTE_DRAWDOWN_SOURCE",
            "RECALCULATE_STOPS",
            "UPDATE_THESES_EVENTS",
            "COMPUTE_HOLDING_ANALYSIS",
            "UPDATE_DIP_EVENTS",
            "GENERATE_RECOMMENDATIONS",
            "GENERATE_DAILY_DIGEST",
            "WEEKLY_MEMO",
            "MONTHLY_REVIEW");
    private final DurableJobStore jobs;
    private final Counter succeeded;
    private final Counter failed;
    private final String workerId = "worker-" + UUID.randomUUID();

    DurableJobCoordinator(DurableJobStore jobs, MeterRegistry meters) {
        this.jobs = jobs;
        this.succeeded = meters.counter("portfolio.worker.jobs", "outcome", "succeeded");
        this.failed = meters.counter("portfolio.worker.jobs", "outcome", "failed");
        meters.gauge("portfolio.worker.pending", jobs, DurableJobStore::pendingCount);
        meters.gauge("portfolio.worker.dead", jobs, DurableJobStore::deadCount);
    }

    @Scheduled(fixedDelayString = "${portfolio.worker.scan-delay-ms:1000}")
    void scan() {
        jobs.claim(workerId, Duration.ofMinutes(5)).ifPresent(job -> {
            try {
                if (!SUPPORTED.contains(job.jobType())) throw new IllegalArgumentException("UNSUPPORTED_JOB_TYPE");
                log.info(
                        "event=job_completed jobId={} jobType={} attempt={}",
                        job.id(),
                        job.jobType(),
                        job.attemptNumber());
                jobs.succeed(job);
                succeeded.increment();
            } catch (RuntimeException exception) {
                log.warn(
                        "event=job_failed jobId={} jobType={} code={}",
                        job.id(),
                        job.jobType(),
                        exception.getMessage());
                jobs.fail(job, "JOB_STEP_FAILED");
                failed.increment();
            }
        });
    }
}
