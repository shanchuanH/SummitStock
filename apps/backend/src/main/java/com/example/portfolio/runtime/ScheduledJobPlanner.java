package com.example.portfolio.runtime;

import com.example.portfolio.configuration.PortfolioProperties;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "portfolio.runtime-mode", havingValue = "worker")
class ScheduledJobPlanner {
    private final DurableJobStore jobs;
    private final AnalysisRunOrchestrator orchestrator;
    private final PortfolioProperties properties;
    private final Clock clock;

    ScheduledJobPlanner(
            DurableJobStore jobs, AnalysisRunOrchestrator orchestrator, PortfolioProperties properties, Clock clock) {
        this.jobs = jobs;
        this.orchestrator = orchestrator;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "${portfolio.worker.quote-cron:*/45 * * * * *}", zone = "UTC")
    void quotes() {
        var slot = clock.instant().getEpochSecond() / 45;
        jobs.enqueue("COLLECT_QUOTES", "quotes:" + slot, "{}", 20, clock.instant());
    }

    @Scheduled(cron = "${portfolio.worker.eod-cron:0 15 22 * * MON-FRI}", zone = "UTC")
    void endOfDay() {
        orchestrator.scheduleForAllUsers(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC), properties);
    }

    @Scheduled(cron = "${portfolio.worker.weekly-cron:0 30 22 * * FRI}", zone = "UTC")
    void weekly() {
        var date = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        jobs.enqueue("WEEKLY_MEMO", "weekly:" + date, "{\"marketDate\":\"" + date + "\"}", 10, clock.instant());
    }

    @Scheduled(cron = "${portfolio.worker.monthly-cron:0 0 23 L * *}", zone = "UTC")
    void monthly() {
        var date = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        jobs.enqueue(
                "MONTHLY_REVIEW",
                "monthly:" + date.getYear() + "-" + date.getMonthValue(),
                "{\"marketDate\":\"" + date + "\"}",
                10,
                clock.instant());
    }
}
