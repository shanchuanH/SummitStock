package com.example.portfolio.fundamentals;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.market.provider.ProviderModels;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NoFakeHealthyOnMissingFactsTest {
    @Test
    void missingFactsRemainMissingInsteadOfDefaultingToHealthy() {
        var result = new FinancialHealthEngine()
                .evaluate(
                        new FinancialMetricEngine.MetricResult(Map.of(), ProviderModels.QualityStatus.MISSING),
                        FinancialHealthEngineTest.policy());

        assertThat(result.overall()).isEqualTo(FinancialHealthEngine.Status.MISSING);
        assertThat(result.quality()).isEqualTo(ProviderModels.QualityStatus.MISSING);
        assertThat(result.negatives()).contains("Required financial facts are missing");
    }
}
