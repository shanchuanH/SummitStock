package com.example.portfolio.fundamentals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ResourceLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class FinancialConceptMapping {
    private final Map<String, FinancialMetric> conceptToMetric;

    @Autowired
    public FinancialConceptMapping(ResourceLoader resources) {
        try {
            var resource = resources.getResource("classpath:strategy/financial-concept-mapping.yaml");
            conceptToMetric = parse(new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Financial concept mapping is unavailable", exception);
        }
    }

    FinancialConceptMapping(String yaml) {
        conceptToMetric = parse(yaml);
    }

    FinancialConceptMapping(Map<String, FinancialMetric> concepts) {
        conceptToMetric = Map.copyOf(concepts);
    }

    public Optional<FinancialMetric> resolve(String taxonomy, String concept) {
        if (!"us-gaap".equalsIgnoreCase(taxonomy) || concept == null) return Optional.empty();
        return Optional.ofNullable(conceptToMetric.get(concept));
    }

    public Map<String, FinancialMetric> concepts() {
        return conceptToMetric;
    }

    private static Map<String, FinancialMetric> parse(String yaml) {
        var result = new LinkedHashMap<String, FinancialMetric>();
        FinancialMetric current = null;
        for (var raw : yaml.lines().toList()) {
            if (raw.isBlank() || raw.stripLeading().startsWith("#")) continue;
            var line = raw.strip();
            if (!raw.startsWith(" ") && line.endsWith(":")) {
                current = FinancialMetric.valueOf(line.substring(0, line.length() - 1));
            } else if (line.startsWith("- ") && current != null) {
                result.put(line.substring(2).strip(), current);
            } else {
                throw new IllegalStateException("Invalid financial concept mapping line: " + line);
            }
        }
        var required = List.of(
                FinancialMetric.REVENUE,
                FinancialMetric.OPERATING_INCOME,
                FinancialMetric.NET_INCOME,
                FinancialMetric.OPERATING_CASH_FLOW,
                FinancialMetric.CAPEX,
                FinancialMetric.CASH,
                FinancialMetric.TOTAL_DEBT,
                FinancialMetric.DILUTED_SHARES);
        if (!required.stream().allMatch(result::containsValue)) {
            throw new IllegalStateException("Financial concept mapping is incomplete");
        }
        return Map.copyOf(result);
    }
}
