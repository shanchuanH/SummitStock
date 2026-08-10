package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.analysis.decision.RecommendationConflictResolver;
import com.example.portfolio.analysis.domain.RecommendationAction;
import com.example.portfolio.analysis.domain.RecommendationCandidate;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

    @ParameterizedTest(name = "{0} overrides {1}")
    @MethodSource("precedenceMatrix")
    void resolvesTwoChannelsBeforeLegacyRanks(
            RecommendationAction expected, RecommendationAction first, RecommendationAction second) {
        var result = resolver.resolve(List.of(candidate(first, 99, "FIRST"), candidate(second, 1, "SECOND")));

        assertThat(result.winner().action()).isEqualTo(expected);
        assertThat(result.suppressed())
                .extracting(RecommendationCandidate::action)
                .containsExactly(second == expected ? first : second);
    }

    private static Stream<Arguments> precedenceMatrix() {
        return Stream.of(
                Arguments.of(RecommendationAction.EXIT, RecommendationAction.EXIT, RecommendationAction.PAUSE_NEW_RISK),
                Arguments.of(RecommendationAction.EXIT, RecommendationAction.EXIT, RecommendationAction.WAIT_FOR_DATA),
                Arguments.of(RecommendationAction.TRIM, RecommendationAction.TRIM, RecommendationAction.DO_NOT_ADD),
                Arguments.of(
                        RecommendationAction.REDUCE_HALF, RecommendationAction.REDUCE_HALF, RecommendationAction.HOLD),
                Arguments.of(
                        RecommendationAction.PAUSE_NEW_RISK,
                        RecommendationAction.ADD,
                        RecommendationAction.PAUSE_NEW_RISK),
                Arguments.of(
                        RecommendationAction.DO_NOT_ADD, RecommendationAction.BUY, RecommendationAction.DO_NOT_ADD),
                Arguments.of(
                        RecommendationAction.WAIT_FOR_DATA,
                        RecommendationAction.BUY,
                        RecommendationAction.WAIT_FOR_DATA));
    }

    private static RecommendationCandidate candidate(RecommendationAction action, int rank, String rule) {
        return new RecommendationCandidate(action, "NORMAL", rank, rule, rule, List.of());
    }
}
