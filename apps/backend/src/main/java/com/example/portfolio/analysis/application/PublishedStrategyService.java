package com.example.portfolio.analysis.application;

import com.example.portfolio.analysis.domain.StrategyDefinition;
import com.example.portfolio.configuration.PortfolioProperties;
import org.springframework.stereotype.Service;

@Service
public final class PublishedStrategyService {
    private final StrategyDefinition definition;

    public PublishedStrategyService(StrategyDefinitionLoader loader, PortfolioProperties properties) {
        definition = loader.load();
        if (!definition.version().equals(properties.strategyVersion())) {
            throw new IllegalStateException("Runtime strategy version does not match published configuration");
        }
    }

    public StrategyDefinition current() {
        return definition;
    }
}
