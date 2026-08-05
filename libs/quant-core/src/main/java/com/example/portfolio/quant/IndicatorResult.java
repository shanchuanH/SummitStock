package com.example.portfolio.quant;

import java.util.List;
import java.util.Optional;

public record IndicatorResult<T>(
        IndicatorStatus status,
        Optional<T> value,
        int requiredObservations,
        int actualObservations,
        List<String> warnings) {
    public IndicatorResult {
        value = value == null ? Optional.empty() : value;
        warnings = List.copyOf(warnings);
    }

    public static <T> IndicatorResult<T> ready(T value, int required, int actual) {
        return new IndicatorResult<>(IndicatorStatus.READY, Optional.of(value), required, actual, List.of());
    }

    public static <T> IndicatorResult<T> unavailable(IndicatorStatus status, int required, int actual, String warning) {
        return new IndicatorResult<>(status, Optional.empty(), required, actual, List.of(warning));
    }
}
