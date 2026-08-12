package com.example.portfolio.shared.web;

import com.example.portfolio.analysis.application.PublishedStrategyService;
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
    private final PublishedStrategyService strategies;

    public VersionController(PortfolioProperties properties, PublishedStrategyService strategies) {
        this.properties = properties;
        this.strategies = strategies;
    }

    @GetMapping("/version")
    ResponseEntity<VersionResponse> version() {
        var strategy = strategies.status();
        return ResponseEntity.ok(new VersionResponse(
                "0.1.0",
                strategy.version(),
                strategy.configHash(),
                strategy.databaseStatus(),
                strategy.production(),
                strategy.draftOverride(),
                properties.runtimeMode(),
                Instant.now(),
                List.of(RuleIds.EMERGENCY_CASH, RuleIds.UNVESTED_COMPENSATION)));
    }

    public record VersionResponse(
            String version,
            String strategyVersion,
            String strategyConfigHash,
            String strategyPublishState,
            boolean productionStrategy,
            boolean draftStrategyOverride,
            String runtimeMode,
            Instant dataAsOf,
            List<String> ruleIds) {}
}
