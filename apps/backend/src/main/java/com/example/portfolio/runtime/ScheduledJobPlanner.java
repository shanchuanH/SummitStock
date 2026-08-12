package com.example.portfolio.runtime;

import com.example.portfolio.configuration.PortfolioProperties;
import com.example.portfolio.market.provider.TradingCalendar;
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
    private final TradingCalendar calendar;

    ScheduledJobPlanner(
            DurableJobStore jobs,
            AnalysisRunOrchestrator orchestrator,
            PortfolioProperties properties,
            Clock clock,
            TradingCalendar calendar) {
        this.jobs = jobs;
        this.orchestrator = orchestrator;
        this.properties = properties;
        this.clock = clock;
        this.calendar = calendar;
    }

    @Scheduled(cron = "${portfolio.worker.quote-cron:0 * * * * *}", zone = "UTC")
    void quotes() {
        var now = clock.instant();
        var marketDate = LocalDate.ofInstant(now, java.time.ZoneId.of("America/New_York"));
        if (!calendar.isSession(marketDate)
                || now.isBefore(calendar.sessionOpen(marketDate))
                || !now.isBefore(calendar.sessionClose(marketDate))) return;
        var slot = now.getEpochSecond() / 60;
        jobs.enqueue("COLLECT_QUOTES", "quotes:" + slot, "{}", 20, clock.instant());
    }

    @Scheduled(cron = "${portfolio.worker.filing-cron:0 0 21 * * MON-FRI}", zone = "UTC")
    void filings() {
        var date = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        jobs.enqueue("CHECK_FILINGS", "filings:" + date, "{\"marketDate\":\"" + date + "\"}", 30, clock.instant());
    }

    @Scheduled(cron = "${portfolio.worker.eod-cron:0 15 22 * * MON-FRI}", zone = "UTC")
    void endOfDay() {
        orchestrator.scheduleForAllUsers(LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC), properties);
    }

    @Scheduled(cron = "${portfolio.worker.weekly-cron:0 30 22 * * FRI}", zone = "UTC")
    void weekly() {
        var date = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        jobs.enqueue(
                "COUNT_VALID_RECOMMENDATIONS",
                "weekly:" + date,
                "{\"marketDate\":\"" + date + "\"}",
                10,
                clock.instant());
    }

    @Scheduled(cron = "${portfolio.worker.monthly-cron:0 0 23 L * *}", zone = "UTC")
    void monthly() {
        var date = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        jobs.enqueue(
                "COUNT_RECENT_SUCCESSFUL_ANALYSES",
                "monthly:" + date.getYear() + "-" + date.getMonthValue(),
                "{\"marketDate\":\"" + date + "\"}",
                10,
                clock.instant());
    }
}
