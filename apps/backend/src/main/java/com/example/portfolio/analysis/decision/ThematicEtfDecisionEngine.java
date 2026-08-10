package com.example.portfolio.analysis.decision;

import static com.example.portfolio.analysis.decision.DecisionCandidates.of;

import com.example.portfolio.analysis.domain.RecommendationAction;
import com.example.portfolio.analysis.domain.RecommendationCandidate;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class ThematicEtfDecisionEngine implements AssetDecisionEngine {
    @Override
    public Set<HoldingClassification> supports() {
        return Set.of(HoldingClassification.THEMATIC_ETF);
    }

    @Override
    public List<RecommendationCandidate> evaluate(DecisionContext context) {
        var e = context.evidence();
        var values = new ArrayList<RecommendationCandidate>();
        if (!e.profile().fundProfileAvailable() || "MISSING".equals(e.profile().liquidityStatus())) {
            values.add(of(
                    RecommendationAction.WAIT_FOR_DATA,
                    "DO_NOT",
                    1,
                    "THEMATIC_ETF.PROFILE.REQUIRED",
                    "Fund liquidity and holdings profile are required.",
                    "Company earnings cannot substitute for ETF evidence."));
        }
        if ((e.profile().topHoldingConcentration() != null
                        && e.profile().topHoldingConcentration().compareTo(new BigDecimal("0.35")) > 0
                || e.profile().portfolioOverlap() != null
                        && e.profile().portfolioOverlap().compareTo(new BigDecimal("0.50")) > 0)) {
            values.add(of(
                    RecommendationAction.DO_NOT_ADD,
                    "DO_NOT",
                    7,
                    "THEMATIC_ETF.CONCENTRATION",
                    "Fund concentration or portfolio overlap is too high.",
                    "Theme exposure can duplicate existing cluster risk."));
        }
        if (context.normalMax() != null
                && e.currentWeight().compareTo(context.normalMax()) < 0
                && ("UPTREND".equals(e.indicators().priceState())
                        || "STRONG_UPTREND".equals(e.indicators().priceState()))) {
            values.add(of(
                    RecommendationAction.ADD,
                    "NORMAL",
                    10,
                    "THEMATIC_ETF.TREND.ADD",
                    "Theme trend, relative price evidence, and capacity permit an add.",
                    "Fund composition and liquidity can change."));
        }
        values.add(of(
                RecommendationAction.HOLD,
                "NORMAL",
                11,
                "THEMATIC_ETF.HOLD",
                "No higher-priority thematic-fund rule requires action.",
                "Single-company earnings are intentionally ignored."));
        return List.copyOf(values);
    }
}
