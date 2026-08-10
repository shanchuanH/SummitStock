package com.example.portfolio.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class JobHandlerRegistry {
    private final Map<String, JobHandler> handlers;

    public JobHandlerRegistry(List<JobHandler> values) {
        var indexed = new LinkedHashMap<String, JobHandler>();
        for (var handler : values) {
            var type = handler.jobType();
            if (type == null || type.isBlank()) throw new IllegalStateException("Job handler type is required");
            if (indexed.putIfAbsent(type, handler) != null) {
                throw new IllegalStateException("Duplicate job handler type: " + type);
            }
        }
        handlers = Map.copyOf(indexed);
    }

    public JobHandler require(String jobType) {
        var handler = handlers.get(jobType);
        if (handler == null) throw new PermanentDataException("UNKNOWN_JOB_TYPE", "No handler for job type");
        return handler;
    }

    public int size() {
        return handlers.size();
    }
}
