package com.example.portfolio.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DurableJobCoordinatorTest {
    @Test
    void executesResolvedHandlerAndPersistsItsOutput() {
        var jobs = mock(DurableJobStore.class);
        var orchestrator = mock(AnalysisRunOrchestrator.class);
        var claimed = job("TEST");
        var result = JobExecutionResult.succeeded("{\"ok\":true}", Instant.parse("2026-08-05T20:00:00Z"));
        when(jobs.claim(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(claimed));
        JobHandler handler = new JobHandler() {
            public String jobType() {
                return "TEST";
            }

            public JobExecutionResult execute(JobExecutionContext context) {
                return result;
            }
        };
        var coordinator = new DurableJobCoordinator(
                jobs,
                new JobHandlerRegistry(List.of(handler)),
                orchestrator,
                new SimpleMeterRegistry(),
                Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        assertThat(coordinator.runOnce()).isTrue();
        verify(jobs).succeed(claimed, result);
        verify(orchestrator).succeeded(claimed, result);
    }

    @Test
    void unknownTypesArePermanentFailures() {
        var jobs = mock(DurableJobStore.class);
        var orchestrator = mock(AnalysisRunOrchestrator.class);
        var claimed = job("UNKNOWN");
        when(jobs.claim(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(claimed));
        var coordinator = new DurableJobCoordinator(
                jobs, new JobHandlerRegistry(List.of()), orchestrator, new SimpleMeterRegistry(), Clock.systemUTC());
        coordinator.runOnce();
        verify(jobs).failPermanent(claimed, "UNKNOWN_JOB_TYPE");
        verify(orchestrator).failed(claimed, "UNKNOWN_JOB_TYPE");
    }

    private static DurableJobStore.ClaimedJob job(String type) {
        return new DurableJobStore.ClaimedJob(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                type,
                "key",
                "{}",
                1,
                5,
                Instant.EPOCH,
                "worker-test",
                UUID.randomUUID());
    }
}
