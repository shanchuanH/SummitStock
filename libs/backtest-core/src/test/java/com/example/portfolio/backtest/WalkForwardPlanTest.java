package com.example.portfolio.backtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class WalkForwardPlanTest {
    @Test
    void createsExpandingNonOverlappingOutOfSampleFolds() {
        var start = LocalDate.parse("2025-01-01");
        var sessions = IntStream.range(0, 10).mapToObj(start::plusDays).toList();
        var folds = new WalkForwardPlan().expanding(sessions, 4, 2);
        assertThat(folds).hasSize(3);
        assertThat(folds.getFirst().trainingEnd()).isEqualTo(start.plusDays(3));
        assertThat(folds.getFirst().testStart()).isEqualTo(start.plusDays(4));
        assertThat(folds.getLast().trainingEnd()).isEqualTo(start.plusDays(7));
        assertThat(folds.getLast().testEnd()).isEqualTo(start.plusDays(9));
    }
}
