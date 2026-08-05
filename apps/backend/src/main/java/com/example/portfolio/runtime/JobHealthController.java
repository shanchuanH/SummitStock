package com.example.portfolio.runtime;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/worker")
class JobHealthController {
    private final DurableJobStore jobs;

    JobHealthController(DurableJobStore jobs) {
        this.jobs = jobs;
    }

    @GetMapping("/health")
    JobHealth health() {
        return new JobHealth(jobs.pendingCount(), jobs.deadCount());
    }

    record JobHealth(long pendingJobs, long deadJobs) {}
}
