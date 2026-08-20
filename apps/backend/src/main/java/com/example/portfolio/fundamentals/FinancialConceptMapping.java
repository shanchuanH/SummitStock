package com.example.portfolio.fundamentals;

import com.example.portfolio.market.provider.ProviderModels;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
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

    public String mappingVersion() {
        return "financial-concepts-v2";
    }

    public String aggregation(FinancialMetric metric) {
        return metric == FinancialMetric.TOTAL_DEBT ? "AGGREGATE_OR_SUM_DISTINCT_COMPONENTS" : "SINGLE_CONCEPT";
    }

    public boolean valid(FinancialMetric metric, ProviderModels.CompanyFact fact) {
        if (!validUnit(metric, fact.unit())) return false;
        return allowsNegative(metric) || fact.value().compareTo(BigDecimal.ZERO) >= 0;
    }

    private static boolean validUnit(FinancialMetric metric, String unit) {
        if (unit == null) return false;
        return switch (metric) {
            case DILUTED_WEIGHTED_AVG_SHARES, COMMON_SHARES_OUTSTANDING -> "shares".equalsIgnoreCase(unit);
            case DILUTED_EPS -> "USD/shares".equalsIgnoreCase(unit);
            default -> "USD".equalsIgnoreCase(unit);
        };
    }

    private static boolean allowsNegative(FinancialMetric metric) {
        return switch (metric) {
            case OPERATING_INCOME, NET_INCOME, OPERATING_CASH_FLOW, CAPEX, DILUTED_EPS -> true;
            default -> false;
        };
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
                FinancialMetric.DILUTED_WEIGHTED_AVG_SHARES,
                FinancialMetric.COMMON_SHARES_OUTSTANDING);
        if (!required.stream().allMatch(result::containsValue)) {
            throw new IllegalStateException("Financial concept mapping is incomplete");
        }
        return Map.copyOf(result);
    }
}
