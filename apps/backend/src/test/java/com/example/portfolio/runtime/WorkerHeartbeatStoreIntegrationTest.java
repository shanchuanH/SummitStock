package com.example.portfolio.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.MySqlIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class WorkerHeartbeatStoreIntegrationTest extends MySqlIntegrationTest {
    @Autowired
    WorkerHeartbeatStore store;

    @Test
    void latestOnlineHeartbeatMakesTheRuntimeObservable() {
        var started = Instant.now().minusSeconds(10);
        store.heartbeat("runtime-test-worker", "test-version", started, "test-host", "ONLINE");
        var runtime = store.snapshot();
        assertThat(runtime.workerAlive()).isTrue();
        assertThat(runtime.lastHeartbeat()).isNotNull();
        assertThat(runtime.pendingJobs()).isGreaterThanOrEqualTo(0);
        assertThat(runtime.runningJobs()).isGreaterThanOrEqualTo(0);
        assertThat(runtime.deadJobs()).isGreaterThanOrEqualTo(0);
    }
}

