package com.example.portfolio.runtime;

import com.example.portfolio.analysis.application.HoldingAnalysisApplicationService;
import com.example.portfolio.analysis.application.RecommendationGenerationService;
import com.example.portfolio.earnings.EarningsIntelligenceApplicationService;
import com.example.portfolio.estimates.EstimateCollectionService;
import com.example.portfolio.estimates.EstimateRevisionApplicationService;
import com.example.portfolio.fundamentals.FinancialFactNormalizationService;
import com.example.portfolio.fundamentals.FinancialHealthApplicationService;
import com.example.portfolio.fundamentals.FundamentalsCollectionService;
import com.example.portfolio.valuation.ValuationApplicationService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PostEarningsReanalysisService {
    public static final List<String> STAGES =
            List.of("FUNDAMENTALS", "ESTIMATES", "REACTION", "THESIS", "VALUATION", "RECOMMENDATION");

    private final FundamentalsCollectionService fundamentals;
    private final FinancialFactNormalizationService normalization;
    private final FinancialHealthApplicationService health;
    private final EstimateCollectionService estimates;
    private final EstimateRevisionApplicationService revisions;
    private final EarningsIntelligenceApplicationService earnings;
    private final PortfolioAnalysisPipelineService portfolio;
    private final ValuationApplicationService valuation;
    private final HoldingAnalysisApplicationService holdingAnalysis;
    private final RecommendationGenerationService recommendations;

    public PostEarningsReanalysisService(
            FundamentalsCollectionService fundamentals,
            FinancialFactNormalizationService normalization,
            FinancialHealthApplicationService health,
            EstimateCollectionService estimates,
            EstimateRevisionApplicationService revisions,
            EarningsIntelligenceApplicationService earnings,
            PortfolioAnalysisPipelineService portfolio,
            ValuationApplicationService valuation,
            HoldingAnalysisApplicationService holdingAnalysis,
            RecommendationGenerationService recommendations) {
        this.fundamentals = fundamentals;
        this.normalization = normalization;
        this.health = health;
        this.estimates = estimates;
        this.revisions = revisions;
        this.earnings = earnings;
        this.portfolio = portfolio;
        this.valuation = valuation;
        this.holdingAnalysis = holdingAnalysis;
        this.recommendations = recommendations;
    }

    public int reanalyze(UUID userId) {
        int affected = fundamentals.collectFundamentals().affected();
        affected += normalization.normalizeAll();
        affected += health.computeAll();
        affected += estimates.collectAll().affected();
        affected += revisions.computeAll();
        affected += earnings.computeReactions();
        affected += portfolio.updateThesesEvents(userId);
        affected += valuation.computeAll();
        affected += earnings.computeRisk();
        affected += holdingAnalysis.analyzeAll(userId).size();
        affected += recommendations.generateAll(userId).size();
        return affected;
    }
}
