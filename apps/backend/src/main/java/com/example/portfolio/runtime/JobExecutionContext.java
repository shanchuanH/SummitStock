package com.example.portfolio.runtime;

import java.time.Instant;
import java.util.UUID;

public record JobExecutionContext(
        UUID jobId,
        UUID analysisRunId,
        String idempotencyKey,
        String payloadJson,
        int attemptNumber,
        Instant startedAt,
        String workerId) {}
