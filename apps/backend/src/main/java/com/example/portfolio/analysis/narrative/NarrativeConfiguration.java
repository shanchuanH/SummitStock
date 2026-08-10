package com.example.portfolio.analysis.narrative;

import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NarrativeConfiguration {
    @Bean
    @ConditionalOnMissingBean(NarrativeModelClient.class)
    NarrativeModelClient disabledNarrativeModelClient() {
        return input -> Optional.empty();
    }
}
