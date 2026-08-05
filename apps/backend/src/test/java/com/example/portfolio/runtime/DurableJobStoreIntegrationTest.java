package com.example.portfolio.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.portfolio.MySqlIntegrationTest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DurableJobStoreIntegrationTest extends MySqlIntegrationTest {
    @Autowired
    DurableJobStore jobs;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    MockMvc mockMvc;

    @BeforeEach
    void clean() {
        jdbc.sql("DELETE FROM job_attempt").update();
        jdbc.sql("DELETE FROM job_run").update();
    }

    @Test
    void idempotentClaimLeaseRecoveryAndRetryAreDurable() {
        assertThat(jobs.enqueue(
                        "COLLECT_BARS",
                        "eod:2026-08-05:bars",
                        "{}",
                        10,
                        Instant.now().minusSeconds(1)))
                .isTrue();
        assertThat(jobs.enqueue("COLLECT_BARS", "eod:2026-08-05:bars", "{}", 10, Instant.now()))
                .isFalse();
        var first = jobs.claim("worker-a", Duration.ofMinutes(5)).orElseThrow();
        assertThat(jobs.claim("worker-b", Duration.ofMinutes(5))).isEmpty();
        jdbc.sql("UPDATE job_run SET lease_expires_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=UUID_TO_BIN(:id)")
                .param("id", first.id().toString())
                .update();
        var recovered = jobs.claim("worker-b", Duration.ofMinutes(5)).orElseThrow();
        assertThat(recovered.attemptNumber()).isEqualTo(2);
        jobs.fail(recovered, "TRANSIENT_PROVIDER");
        assertThat(jobs.pendingCount()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM job_attempt")
                        .query(Long.class)
                        .single())
                .isEqualTo(2);
    }

    @Test
    void pageLoadDoesNotEnqueueJobs() throws Exception {
        var before = jobs.pendingCount();
        mockMvc.perform(get("/api/v1/worker/health").with(httpBasic("admin@example.local", "change-before-use")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/portfolio/summary").with(httpBasic("admin@example.local", "change-before-use")))
                .andExpect(status().isOk());
        assertThat(jobs.pendingCount()).isEqualTo(before);
    }

    @Test
    void scheduledScannerWritesAnIdempotentEodPipeline() {
        var planner = new ScheduledJobPlanner(jobs, Clock.fixed(Instant.parse("2026-08-05T22:15:00Z"), ZoneOffset.UTC));
        planner.endOfDay();
        planner.endOfDay();
        assertThat(jobs.pendingCount()).isEqualTo(ScheduledJobPlanner.EOD_PIPELINE.size());
    }
}
