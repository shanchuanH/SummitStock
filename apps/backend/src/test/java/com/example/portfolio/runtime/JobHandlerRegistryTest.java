package com.example.portfolio.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class JobHandlerRegistryTest {
    @Test
    void indexesHandlersAndRejectsDuplicatesOrMissingTypes() {
        JobHandler first = handler("ONE");
        var registry = new JobHandlerRegistry(List.of(first));
        assertThat(registry.require("ONE")).isSameAs(first);
        assertThatThrownBy(() -> registry.require("MISSING"))
                .isInstanceOf(PermanentDataException.class)
                .extracting(error -> ((PermanentDataException) error).code())
                .isEqualTo("UNKNOWN_JOB_TYPE");
        assertThatThrownBy(() -> new JobHandlerRegistry(List.of(first, handler("ONE"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate");
    }

    private static JobHandler handler(String type) {
        return new JobHandler() {
            public String jobType() {
                return type;
            }

            public JobExecutionResult execute(JobExecutionContext context) {
                return JobExecutionResult.succeeded("{}", Instant.EPOCH);
            }
        };
    }
}
