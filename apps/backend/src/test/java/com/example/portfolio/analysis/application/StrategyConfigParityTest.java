package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class StrategyConfigParityTest {
    private static final String CONFIG = "strategy/STRATEGY_CONFIG_V3_DRAFT.yaml";
    private final StrategyDefinitionLoader loader = new StrategyDefinitionLoader(new DefaultResourceLoader());

    @Test
    void everyYamlLeafIsEitherConsumedByRuntimeOrExplicitlyNonDecisionMetadata() {
        var registered = new HashSet<>(StrategyDefinitionLoader.consumedStrategyKeys());
        registered.addAll(StrategyDefinitionLoader.nonDecisionMetadataKeys());

        assertThat(loader.configuredKeys(CONFIG)).containsExactlyInAnyOrderElementsOf(registered);
    }

    @Test
    void decisionAffectingValuesSurviveLoadingIntoTypedDefinition() {
        var strategy = loader.load(CONFIG);

        assertThat(strategy.stopNewSpeculationAt()).isEqualByComparingTo("0.08");
        assertThat(strategy.reduceTacticalCapacityAt()).isEqualByComparingTo("0.10");
        assertThat(strategy.etfDipSetupAt()).isEqualByComparingTo("0.12");
        assertThat(strategy.marketDrivenEtfDeploymentAt()).isEqualByComparingTo("0.15");
        assertThat(strategy.painLine()).isEqualByComparingTo("0.20");
        assertThat(strategy.exactQuantityRequiresHealthyPrice()).isTrue();
        assertThat(strategy.exactQuantityRequiresReadyRisk()).isTrue();
        assertThat(strategy.riskPriorityOverTax()).isTrue();
        assertThat(strategy.underweightAloneCanTriggerAdd()).isFalse();
        assertThat(strategy.coolingHours()).isEqualTo(48);
        assertThat(strategy.deepDiscountStarterEnabled()).isTrue();
        assertThat(strategy.speculativeAverageDownAllowed()).isFalse();
        assertThat(strategy.speculativeTimeStopTradingDays()).isEqualTo(25);
        assertThat(strategy.tacticalReserveTargets())
                .containsExactly(new BigDecimal("0.08"), new BigDecimal("0.10"), new BigDecimal("0.12"));
        assertThat(strategy.sleeveAllocations().total()).isEqualByComparingTo("1.00");
        assertThat(strategy.sleeveAllocations().international()).isEqualByComparingTo("0.10");
        assertThat(strategy.sleeveAllocations().quality()).isEqualByComparingTo("0.15");
        assertThat(strategy.executionRisk().liquidityParticipationMax()).isEqualByComparingTo("0.10");
        assertThat(strategy.stops().speculativeVolatilityAtr()).isEqualByComparingTo("3.5");
        assertThat(strategy.manualExecutionOnly()).isTrue();
        assertThat(strategy.primaryGrowthBenchmark()).isEqualTo("QQQ");
        assertThat(strategy.broadMarketBenchmark()).isEqualTo("SPY");
        assertThat(strategy.freshness().eodPriceTradingSessions()).isEqualTo(1);
        assertThat(strategy.freshness().financialQuarterDays()).isEqualTo(140);
        assertThat(strategy.freshness().estimatesDays()).isEqualTo(14);
        assertThat(strategy.freshness().earningsCalendarDays()).isEqualTo(7);
        assertThat(strategy.freshness().etfProfileDays()).isEqualTo(14);
        assertThat(strategy.freshness().macroDailyDays()).isEqualTo(3);
    }
}
