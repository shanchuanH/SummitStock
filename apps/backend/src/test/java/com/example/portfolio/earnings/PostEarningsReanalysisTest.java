package com.example.portfolio.earnings;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.runtime.PostEarningsReanalysisService;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostEarningsReanalysisTest {
    @Test
    void preservesTheRequiredEvidenceRefreshOrder() {
        assertThat(PostEarningsReanalysisService.STAGES)
                .containsExactlyElementsOf(
                        List.of("FUNDAMENTALS", "ESTIMATES", "REACTION", "THESIS", "VALUATION", "RECOMMENDATION"));
    }
}
