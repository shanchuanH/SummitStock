package com.example.portfolio.runtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.portfolio.configuration.PortfolioProperties;
import com.example.portfolio.market.provider.UsEquityTradingCalendar;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ScheduledJobPlannerTest {
    @Test
    void pollsQuotesOnlyDuringRegularTradingSessions() {
        var regularJobs = mock(DurableJobStore.class);
        planner(regularJobs, "2026-08-05T15:00:00Z").quotes();
        verify(regularJobs).enqueue(anyString(), anyString(), anyString(), anyInt(), any());

        var afterCloseJobs = mock(DurableJobStore.class);
        planner(afterCloseJobs, "2026-08-05T22:15:00Z").quotes();
        verify(afterCloseJobs, never()).enqueue(anyString(), anyString(), anyString(), anyInt(), any());

        var weekendJobs = mock(DurableJobStore.class);
        planner(weekendJobs, "2026-08-08T15:00:00Z").quotes();
        verify(weekendJobs, never()).enqueue(anyString(), anyString(), anyString(), anyInt(), any());
    }

    @Test
    void periodicCountJobsUseNamesThatMatchTheirActualReadOnlySemantics() {
        var weeklyJobs = mock(DurableJobStore.class);
        planner(weeklyJobs, "2026-08-07T22:30:00Z").weekly();
        verify(weeklyJobs).enqueue(eq("COUNT_VALID_RECOMMENDATIONS"), anyString(), anyString(), anyInt(), any());

        var monthlyJobs = mock(DurableJobStore.class);
        planner(monthlyJobs, "2026-08-31T23:00:00Z").monthly();
        verify(monthlyJobs).enqueue(eq("COUNT_RECENT_SUCCESSFUL_ANALYSES"), anyString(), anyString(), anyInt(), any());
    }

    private static ScheduledJobPlanner planner(DurableJobStore jobs, String now) {
        return new ScheduledJobPlanner(
                jobs,
                mock(AnalysisRunOrchestrator.class),
                mock(PortfolioProperties.class),
                Clock.fixed(Instant.parse(now), ZoneOffset.UTC),
                new UsEquityTradingCalendar());
    }
}
