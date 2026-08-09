package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.analysis.decision.RecommendationConflictResolver;
import com.example.portfolio.analysis.domain.RecommendationAction;
import com.example.portfolio.analysis.domain.RecommendationCandidate;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendationConflictResolverTest {
    private final RecommendationConflictResolver resolver = new RecommendationConflictResolver();

    @Test
    void painLineSuppressesQualityAdd() {
        var result = resolver.resolve(List.of(
                candidate(RecommendationAction.ADD, 8, "QUALITY.DISCOUNT.ADD"),
                candidate(RecommendationAction.DO_NOT_ADD, 3, "DRAWDOWN.PAIN.NO_NEW_RISK")));
        assertThat(result.winner().action()).isEqualTo(RecommendationAction.DO_NOT_ADD);
        assertThat(result.suppressed())
                .extracting(RecommendationCandidate::action)
                .containsExactly(RecommendationAction.ADD);
    }

    @Test
    void confirmedStopBeatsHealthyThesisHold() {
        var result = resolver.resolve(List.of(
                candidate(RecommendationAction.HOLD, 9, "THESIS.HEALTHY"),
                candidate(RecommendationAction.EXIT, 4, "STOP.CONFIRMED.EXIT")));
        assertThat(result.winner().action()).isEqualTo(RecommendationAction.EXIT);
    }

    private static RecommendationCandidate candidate(RecommendationAction action, int rank, String rule) {
        return new RecommendationCandidate(action, "NORMAL", rank, rule, rule, List.of());
    }
}
