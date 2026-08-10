package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.strategy.portfolio.HoldingClassification;
import com.example.portfolio.strategy.portfolio.HoldingClassifier;
import org.junit.jupiter.api.Test;

class ClassificationSuggestionTest {
    @Test
    void tickerNeverInventsCompanyQuality() {
        var suggestion = HoldingClassifier.suggest("GOOGL", "EQUITY", false, false);

        assertThat(suggestion.classification()).isEqualTo(HoldingClassification.UNKNOWN);
        assertThat(suggestion.reason()).contains("cannot be inferred");
    }

    @Test
    void thematicEvidenceAppliesOnlyToEtfs() {
        assertThat(HoldingClassifier.suggest("DRAM", "ETF", true, false).classification())
                .isEqualTo(HoldingClassification.THEMATIC_ETF);
        assertThat(HoldingClassifier.suggest("DXYZ", "EQUITY", true, false).classification())
                .isEqualTo(HoldingClassification.UNKNOWN);
    }
}
