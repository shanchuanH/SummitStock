package com.example.portfolio.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.MySqlIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class RetryIdempotencyTest extends MySqlIntegrationTest {
    @Autowired
    DurableJobStore jobs;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void isolateJobQueue() {
        jdbc.sql("DELETE FROM job_attempt").update();
        jdbc.sql("DELETE FROM job_run").update();
    }

    @Test
    void retryUsesOneRunAndPersistsOneResult() {
        var key = "retry-test:" + java.util.UUID.randomUUID();
        assertThat(jobs.enqueue("WEEKLY_MEMO", key, "{}", 1, Instant.now().minusSeconds(1)))
                .isTrue();
        var first = jobs.claim("worker-1", Duration.ofMinutes(1)).orElseThrow();
        assertThat(jobs.failTransient(first, "TRANSIENT_PROVIDER")).isFalse();
        jdbc.sql("UPDATE job_run SET scheduled_at=UTC_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=UUID_TO_BIN(:id)")
                .param("id", first.id().toString())
                .update();
        var second = jobs.claim("worker-2", Duration.ofMinutes(1)).orElseThrow();
        jobs.succeed(second, JobExecutionResult.succeeded("{\"count\":1}", Instant.now()));
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.attemptNumber()).isEqualTo(2);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM job_run WHERE idempotency_key=:key")
                        .param("key", key)
                        .query(Long.class)
                        .single())
                .isEqualTo(1);
        assertThat(jdbc.sql(
                                "SELECT JSON_UNQUOTE(JSON_EXTRACT(result_json,'$.count')) FROM job_run WHERE idempotency_key=:key")
                        .param("key", key)
                        .query(String.class)
                        .single())
                .isEqualTo("1");
    }
}
