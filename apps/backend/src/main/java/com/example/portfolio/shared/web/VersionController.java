package com.example.portfolio.shared.web;

import com.example.portfolio.configuration.PortfolioProperties;
import com.example.portfolio.strategy.RuleIds;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class VersionController {
    private final PortfolioProperties properties;

    public VersionController(PortfolioProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/version")
    ResponseEntity<VersionResponse> version() {
        return ResponseEntity.ok(new VersionResponse(
                "0.1.0",
                properties.strategyVersion(),
                properties.runtimeMode(),
                Instant.now(),
                List.of(RuleIds.EMERGENCY_CASH, RuleIds.UNVESTED_COMPENSATION)));
    }

    public record VersionResponse(
            String version, String strategyVersion, String runtimeMode, Instant dataAsOf, List<String> ruleIds) {}
}
