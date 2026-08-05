package com.example.portfolio.runtime;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "portfolio.runtime-mode", havingValue = "worker")
class ScheduledJobPlanner {
    static final List<String> EOD_PIPELINE = List.of(
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
            "GENERATE_DAILY_DIGEST");

    private final DurableJobStore jobs;
    private final Clock clock;

    ScheduledJobPlanner(DurableJobStore jobs, Clock clock) {
        this.jobs = jobs;
        this.clock = clock;
    }

    @Scheduled(cron = "${portfolio.worker.quote-cron:*/45 * * * * *}", zone = "UTC")
    void quotes() {
        var slot = clock.instant().getEpochSecond() / 45;
        jobs.enqueue("COLLECT_QUOTES", "quotes:" + slot, "{}", 20, clock.instant());
    }

    @Scheduled(cron = "${portfolio.worker.eod-cron:0 15 22 * * MON-FRI}", zone = "UTC")
    void endOfDay() {
        var date = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        for (int index = 0; index < EOD_PIPELINE.size(); index++) {
            var type = EOD_PIPELINE.get(index);
            jobs.enqueue(
                    type,
                    "eod:" + date + ":" + type,
                    "{\"marketDate\":\"" + date + "\"}",
                    100 - index,
                    clock.instant().plusSeconds(index));
        }
    }

    @Scheduled(cron = "${portfolio.worker.weekly-cron:0 30 22 * * FRI}", zone = "UTC")
    void weekly() {
        var date = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        jobs.enqueue("WEEKLY_MEMO", "weekly:" + date, "{}", 10, clock.instant());
    }

    @Scheduled(cron = "${portfolio.worker.monthly-cron:0 0 23 L * *}", zone = "UTC")
    void monthly() {
        var date = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        jobs.enqueue(
                "MONTHLY_REVIEW", "monthly:" + date.getYear() + "-" + date.getMonthValue(), "{}", 10, clock.instant());
    }
}
