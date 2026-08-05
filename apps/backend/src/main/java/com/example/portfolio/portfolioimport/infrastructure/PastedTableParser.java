package com.example.portfolio.portfolioimport.infrastructure;

import com.example.portfolio.portfolioimport.domain.PortfolioImportPreview;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class PastedTableParser {
    private final FidelityCsvParser fidelity;

    public PastedTableParser(FidelityCsvParser fidelity) {
        this.fidelity = fidelity;
    }

    public PortfolioImportPreview parse(String table) {
        if (table == null || table.isBlank()) throw new IllegalArgumentException("The pasted table is empty");
        var csv = table.lines()
                .map(line -> Arrays.stream(line.split("\\t", -1))
                        .map(PastedTableParser::quote)
                        .collect(Collectors.joining(",")))
                .collect(Collectors.joining("\n"));
        return fidelity.parse(csv.getBytes(StandardCharsets.UTF_8), "pasted-fidelity-table.csv");
    }

    private static String quote(String value) {
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
