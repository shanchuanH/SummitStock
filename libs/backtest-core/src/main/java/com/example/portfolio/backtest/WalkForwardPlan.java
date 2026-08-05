package com.example.portfolio.backtest;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class WalkForwardPlan {
    public List<Fold> expanding(List<LocalDate> completedSessions, int minimumTrainingSessions, int testSessions) {
        if (minimumTrainingSessions < 1 || testSessions < 1)
            throw new IllegalArgumentException("INVALID_WALK_FORWARD_WINDOW");
        var sessions = completedSessions.stream()
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
        if (sessions.size() < minimumTrainingSessions + testSessions)
            throw new IllegalArgumentException("INSUFFICIENT_WALK_FORWARD_DATA");
        var folds = new ArrayList<Fold>();
        for (int testStart = minimumTrainingSessions;
                testStart + testSessions <= sessions.size();
                testStart += testSessions) {
            folds.add(new Fold(
                    sessions.getFirst(),
                    sessions.get(testStart - 1),
                    sessions.get(testStart),
                    sessions.get(testStart + testSessions - 1)));
        }
        return List.copyOf(folds);
    }

    public record Fold(LocalDate trainingStart, LocalDate trainingEnd, LocalDate testStart, LocalDate testEnd) {
        public Fold {
            if (!trainingEnd.isBefore(testStart)) throw new IllegalArgumentException("WALK_FORWARD_LEAKAGE");
        }
    }
}
