package com.example.portfolio.analysis.application;

import com.example.portfolio.portfolio.DecisionReasonTag;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class DecisionReasonTags {
    private static final ObjectMapper JSON = new ObjectMapper();

    private DecisionReasonTags() {}

    static Set<DecisionReasonTag> parse(String value) {
        if (value == null || value.isBlank()) return Set.of();
        try {
            return JSON.readValue(value, new TypeReference<List<String>>() {}).stream()
                    .map(DecisionReasonTag::valueOf)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (Exception ignored) {
            return Set.of();
        }
    }
}
