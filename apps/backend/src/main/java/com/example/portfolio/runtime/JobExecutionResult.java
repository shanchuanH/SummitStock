package com.example.portfolio.runtime;

import java.time.Instant;
import java.util.List;

public record JobExecutionResult(String status, String resultJson, List<String> warnings, Instant dataAsOf) {
    public JobExecutionResult {
        if (!"SUCCEEDED".equals(status) && !"PARTIAL".equals(status)) {
            throw new IllegalArgumentException("Handler result status must be SUCCEEDED or PARTIAL");
        }
        resultJson = resultJson == null ? "{}" : resultJson;
        warnings = List.copyOf(warnings);
    }

    public static JobExecutionResult succeeded(String resultJson, Instant dataAsOf) {
        return new JobExecutionResult("SUCCEEDED", resultJson, List.of(), dataAsOf);
    }
}
