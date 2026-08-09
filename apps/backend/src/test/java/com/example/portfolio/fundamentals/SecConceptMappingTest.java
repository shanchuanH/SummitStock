package com.example.portfolio.fundamentals;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecConceptMappingTest {
    @Test
    void mapsDeclaredUsGaapConceptsAndRejectsUnknownTaxonomies() {
        var mapping = FinancialTestFixtures.mapping();

        assertThat(mapping.resolve("us-gaap", "REVENUEConcept")).contains(FinancialMetric.REVENUE);
        assertThat(mapping.resolve("ifrs-full", "REVENUEConcept")).isEmpty();
        assertThat(mapping.resolve("us-gaap", "UnknownConcept")).isEmpty();
    }
}
