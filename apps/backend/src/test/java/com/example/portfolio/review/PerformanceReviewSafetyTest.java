package com.example.portfolio.review;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PerformanceReviewSafetyTest {
    @Test
    void approximateActiveSleevePerformanceCannotReduceBudgetAutomatically() {
        assertThat(PerformanceReviewService.safeBudgetMultiplier()).isEqualByComparingTo("1");
    }
}
