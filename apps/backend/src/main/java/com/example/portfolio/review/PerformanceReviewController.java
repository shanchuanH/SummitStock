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
    Response performance(Principal principal) {
        var review = service.review(store.userId(principal.getName()));
        return new Response(
                review.periods().stream().map(Period::from).toList(),
                review.contributions().stream().map(Contribution::from).toList(),
                review.decisionOutcomes().stream().map(DecisionOutcome::from).toList(),
                review.activeSleeve() == null ? null : Accountability.from(review.activeSleeve()),
                review.quality());
    }

    public record Response(
            List<Period> periods,
            List<Contribution> contributions,
            List<DecisionOutcome> decisionOutcomes,
            Accountability activeSleeve,
            String quality) {}

    public record Period(
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
        static Period from(PerformanceReviewService.Period v) {
            return new Period(
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

    public record Contribution(String symbol, String contribution) {
        static Contribution from(PerformanceReviewService.Contribution v) {
            return new Contribution(v.symbol(), decimal(v.contribution()));
        }
    }

    public record DecisionOutcome(
            UUID recommendationId,
            String symbol,
            String action,
            String userDecision,
            String ruleObjective,
            String evaluation,
            String interpretation) {
        static DecisionOutcome from(PerformanceReviewService.DecisionOutcome v) {
            return new DecisionOutcome(
                    v.recommendationId(),
                    v.symbol(),
                    v.action(),
                    v.userDecision(),
                    v.ruleObjective(),
                    v.evaluation(),
                    v.interpretation());
        }
    }

    public record Accountability(
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
        static Accountability from(PerformanceReviewService.Accountability v) {
            return new Accountability(
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
