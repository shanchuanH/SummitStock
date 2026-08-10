package com.example.portfolio.analysis.narrative;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class NarrativeFactValidator {
    private static final Pattern PERCENTAGE = Pattern.compile("(?<![\\d.])(\\d+(?:\\.\\d+)?)\\s*%");
    private static final List<String> TARGET_PRICE_TERMS = List.of("target price", "price target", "目标价", "目标价格");
    private static final List<String> CONSENSUS_TERMS =
            List.of("analysts agree", "analyst consensus", "分析师一致认为", "分析师共识");

    public Validation validate(NarrativeInput input, DecisionNarrative output) {
        var violations = new ArrayList<String>();
        var facts = input.factText().toLowerCase(Locale.ROOT);
        var narrative = output.text().toLowerCase(Locale.ROOT);
        var matcher = PERCENTAGE.matcher(narrative);
        while (matcher.find()) {
            if (!facts.contains(matcher.group())) violations.add("UNSUPPORTED_PERCENTAGE:" + matcher.group());
        }
        if (containsAny(narrative, TARGET_PRICE_TERMS) && !containsAny(facts, TARGET_PRICE_TERMS)) {
            violations.add("UNSUPPORTED_TARGET_PRICE");
        }
        if (!input.analystEvidenceAvailable() && containsAny(narrative, CONSENSUS_TERMS)) {
            violations.add("UNSUPPORTED_ANALYST_CONSENSUS");
        }
        return new Validation(violations.isEmpty(), violations);
    }

    private static boolean containsAny(String value, List<String> terms) {
        return terms.stream().anyMatch(value::contains);
    }

    public record Validation(boolean valid, List<String> violations) {
        public Validation {
            violations = List.copyOf(violations);
        }
    }
}
