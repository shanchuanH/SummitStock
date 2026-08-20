package com.example.portfolio.review;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/review")
public class PerformanceReviewController {
    private final PerformanceReviewStore store;
    private final PerformanceReviewService service;

    PerformanceReviewController(PerformanceReviewStore store, PerformanceReviewService service) {
        this.store = store;
        this.service = service;
    }

    @GetMapping("/performance")
    PerformanceReviewResponse performance(Principal principal) {
        var review = service.review(store.userId(principal.getName()));
        return new PerformanceReviewResponse(
                review.periods().stream().map(PerformancePeriodResponse::from).toList(),
                review.contributions().stream()
                        .map(PerformanceContributionResponse::from)
                        .toList(),
                review.decisionOutcomes().stream()
                        .map(DecisionOutcomeResponse::from)
                        .toList(),
                review.activeSleeve() == null ? null : ActiveSleeveAccountabilityResponse.from(review.activeSleeve()),
                review.quality());
    }

    public record PerformanceReviewResponse(
            List<PerformancePeriodResponse> periods,
            List<PerformanceContributionResponse> contributions,
            List<DecisionOutcomeResponse> decisionOutcomes,
            ActiveSleeveAccountabilityResponse activeSleeve,
            String quality) {}

    public record PerformancePeriodResponse(
            String period,
            String portfolioTwr,
            String spyReturn,
            String qqqReturn,
            String activeReturn,
            String activeMaxDrawdown,
            String coreMaxDrawdown,
            String turnover,
            LocalDate coverageStart,
            LocalDate coverageEnd) {
        static PerformancePeriodResponse from(PerformanceReviewService.Period v) {
            return new PerformancePeriodResponse(
                    v.period(),
                    decimal(v.portfolioTwr()),
                    decimal(v.spyReturn()),
                    decimal(v.qqqReturn()),
                    decimal(v.activeReturn()),
                    decimal(v.activeMaxDrawdown()),
                    decimal(v.coreMaxDrawdown()),
                    decimal(v.turnover()),
                    v.coverageStart(),
                    v.coverageEnd());
        }
    }

    public record PerformanceContributionResponse(String symbol, String contribution) {
        static PerformanceContributionResponse from(PerformanceReviewService.Contribution v) {
            return new PerformanceContributionResponse(v.symbol(), decimal(v.contribution()));
        }
    }

    public record DecisionOutcomeResponse(
            UUID recommendationId,
            String symbol,
            String action,
            String userDecision,
            String ruleObjective,
            String evaluation,
            String interpretation) {
        static DecisionOutcomeResponse from(PerformanceReviewService.DecisionOutcome v) {
            return new DecisionOutcomeResponse(
                    v.recommendationId(),
                    v.symbol(),
                    v.action(),
                    v.userDecision(),
                    v.ruleObjective(),
                    v.evaluation(),
                    v.interpretation());
        }
    }

    public record ActiveSleeveAccountabilityResponse(
            int reviewMonths,
            String activeReturn,
            String benchmarkReturn,
            String relativeReturn,
            String contribution,
            String underperformance,
            String activeMaxDrawdown,
            String coreMaxDrawdown,
            String turnover,
            String budgetMultiplier,
            List<String> ruleIds) {
        static ActiveSleeveAccountabilityResponse from(PerformanceReviewService.Accountability v) {
            return new ActiveSleeveAccountabilityResponse(
                    v.reviewMonths(),
                    decimal(v.activeReturn()),
                    decimal(v.benchmarkReturn()),
                    decimal(v.relativeReturn()),
                    decimal(v.contribution()),
                    decimal(v.underperformance()),
                    decimal(v.activeMaxDrawdown()),
                    decimal(v.coreMaxDrawdown()),
                    decimal(v.turnover()),
                    decimal(v.budgetMultiplier()),
                    v.ruleIds());
        }
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }
}
