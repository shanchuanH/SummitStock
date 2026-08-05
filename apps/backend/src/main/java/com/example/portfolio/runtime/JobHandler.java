package com.example.portfolio.runtime;

public interface JobHandler {
    String jobType();

    JobExecutionResult execute(JobExecutionContext context);
}
