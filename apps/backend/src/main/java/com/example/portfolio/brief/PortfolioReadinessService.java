package com.example.portfolio.brief;

import com.example.portfolio.analysis.domain.PortfolioAnalysisState;
import org.springframework.stereotype.Service;

@Service
public class PortfolioReadinessService {
    public PortfolioAnalysisState assess(ReadinessFacts facts) {
        if (facts.openPositions() == 0) return PortfolioAnalysisState.NO_PORTFOLIO;
        if (facts.importPendingConfirmation()) return PortfolioAnalysisState.IMPORT_PENDING_CONFIRMATION;
        if (facts.importing()) return PortfolioAnalysisState.IMPORTING;
        if (!facts.analysisRunExists()) return PortfolioAnalysisState.PORTFOLIO_READY;
        if (facts.analysisQueued()) return PortfolioAnalysisState.ANALYSIS_QUEUED;
        if (facts.missingMarketPositions() > 0) return PortfolioAnalysisState.WAIT_FOR_MARKET_DATA;
        if (facts.missingFundamentalPositions() > 0) {
            return facts.analyzedPositions() == 0
                    ? PortfolioAnalysisState.WAIT_FOR_FUNDAMENTALS
                    : PortfolioAnalysisState.PARTIAL_ANALYSIS;
        }
        if (facts.analyzedPositions() < facts.openPositions()
                && facts.staleAnalysisPositions() == 0
                && !facts.blocked()
                && !facts.failedWithoutFallback()) {
            return PortfolioAnalysisState.PARTIAL_ANALYSIS;
        }
        if (facts.staleAnalysisPositions() > 0) return PortfolioAnalysisState.STALE;
        if (facts.blocked()) return PortfolioAnalysisState.BLOCKED;
        if (facts.failedWithoutFallback()) return PortfolioAnalysisState.FAILED;
        return PortfolioAnalysisState.ANALYSIS_READY;
    }

    public record ReadinessFacts(
            long openPositions,
            boolean importPendingConfirmation,
            boolean importing,
            boolean analysisRunExists,
            boolean analysisQueued,
            long missingMarketPositions,
            long requiredFundamentalPositions,
            long missingFundamentalPositions,
            long analyzedPositions,
            long staleAnalysisPositions,
            boolean blocked,
            boolean failedWithoutFallback) {
        public ReadinessFacts {
            if (openPositions < 0
                    || missingMarketPositions < 0
                    || requiredFundamentalPositions < 0
                    || missingFundamentalPositions < 0
                    || analyzedPositions < 0
                    || staleAnalysisPositions < 0) {
                throw new IllegalArgumentException("Readiness counts cannot be negative");
            }
        }
    }
}
