package com.example.portfolio.runtime;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "portfolio.runtime-mode", havingValue = "worker")
class WorkerRuntimeHeartbeat {
    private final WorkerHeartbeatStore store;
    private final String runtimeVersion;
    private final Clock clock;
    private final String workerId = "worker-" + UUID.randomUUID();
    private final Instant startedAt;
    private final String hostHint = hostHint();

    WorkerRuntimeHeartbeat(
            WorkerHeartbeatStore store,
            @Value("${portfolio.runtime-version:unknown}") String runtimeVersion,
            Clock clock) {
        this.store = store;
        this.runtimeVersion = runtimeVersion;
        this.clock = clock;
        this.startedAt = clock.instant();
    }

    @PostConstruct
    void start() {
        beat();
    }

    @Scheduled(fixedDelayString = "${portfolio.worker.runtime-heartbeat-ms:20000}")
    void beat() {
        store.heartbeat(workerId, runtimeVersion, startedAt, hostHint, "ONLINE");
    }

    @PreDestroy
    void stopping() {
        store.heartbeat(workerId, runtimeVersion, startedAt, hostHint, "STOPPING");
    }

    private static String hostHint() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException exception) {
            return "unknown-host";
        }
    }
}
