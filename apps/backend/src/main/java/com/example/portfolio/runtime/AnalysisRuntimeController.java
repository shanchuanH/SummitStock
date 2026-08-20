package com.example.portfolio.runtime;

import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analysis")
class AnalysisRuntimeController {
    private final WorkerHeartbeatStore heartbeats;

    AnalysisRuntimeController(WorkerHeartbeatStore heartbeats) {
        this.heartbeats = heartbeats;
    }

    @GetMapping("/runtime")
    RuntimeResponse runtime() {
        var value = heartbeats.snapshot();
        return new RuntimeResponse(
                value.workerAlive(),
                value.lastHeartbeat(),
                value.pendingJobs(),
                value.runningJobs(),
                value.deadJobs(),
                value.oldestPendingSeconds());
    }

    record RuntimeResponse(
            boolean workerAlive,
            Instant lastHeartbeat,
            long pendingJobs,
            long runningJobs,
            long deadJobs,
            Long oldestPendingSeconds) {}
}
