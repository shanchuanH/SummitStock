package com.example.portfolio.runtime;

import com.example.portfolio.configuration.PortfolioProperties;
import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/analysis")
class AnalysisCommandController {
    private final AnalysisRunOrchestrator orchestrator;
    private final PortfolioProperties properties;
    private final Clock clock;

    AnalysisCommandController(AnalysisRunOrchestrator orchestrator, PortfolioProperties properties, Clock clock) {
        this.orchestrator = orchestrator;
        this.properties = properties;
        this.clock = clock;
    }

    @PostMapping("/runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    RunResponse run(@RequestBody RunRequest request, Principal principal) {
        if (!"USER_REFRESH".equals(request.reason())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported analysis reason");
        }
        var result = orchestrator
                .scheduleForUser(principal.getName(), LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC), properties)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "An active imported portfolio is required before analysis"));
        return new RunResponse(result.runId(), result.alreadyRunning() ? "RUNNING" : "STARTING");
    }

    record RunRequest(String reason) {}

    record RunResponse(UUID runId, String state) {}
}
