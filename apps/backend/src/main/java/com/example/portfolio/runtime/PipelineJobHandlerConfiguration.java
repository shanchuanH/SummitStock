package com.example.portfolio.runtime;

import com.example.portfolio.analysis.application.HoldingAnalysisApplicationService;
import com.example.portfolio.analysis.application.RecommendationGenerationService;
import com.example.portfolio.earnings.EarningsIntelligenceApplicationService;
import com.example.portfolio.estimates.EstimateCollectionService;
import com.example.portfolio.estimates.EstimateRevisionApplicationService;
import com.example.portfolio.fundamentals.FinancialFactNormalizationService;
import com.example.portfolio.fundamentals.FinancialHealthApplicationService;
import com.example.portfolio.fundamentals.FundamentalsCollectionService;
import com.example.portfolio.macro.MacroApplicationService;
import com.example.portfolio.market.EodMarketPipelineService;
import com.example.portfolio.market.PriceStateApplicationService;
import com.example.portfolio.portfolio.IntradayStopAlertService;
import com.example.portfolio.valuation.ValuationApplicationService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
class PipelineJobHandlerConfiguration {
    @Bean
    JobHandler portfolioAnalysisJobHandler(AnalysisRunOrchestrator orchestrator, ObjectMapper json, Clock clock) {
        return handler("PORTFOLIO_ANALYSIS", context -> {
            var values = payload(context, json);
            var runId = context.analysisRunId() == null ? values.runId() : context.analysisRunId();
            if (runId == null) throw new PermanentDataException("MISSING_ANALYSIS_RUN", "Analysis run is required");
            orchestrator.initialize(runId);
            return success(1, clock.instant());
        });
    }

    @Bean
    JobHandler collectQuotesJobHandler(EodMarketPipelineService market, IntradayStopAlertService alerts, Clock clock) {
        return handler("COLLECT_QUOTES", context -> {
            var count = market.collectQuotes();
            var breaches = alerts.evaluateLatestQuotes();
            var warnings = count.observations() == 0 ? List.of("NO_OBSERVATIONS") : List.<String>of();
            return new JobExecutionResult(
                    warnings.isEmpty() ? "SUCCEEDED" : "PARTIAL",
                    "{\"observations\":" + count.observations() + ",\"affected\":" + count.affected()
                            + ",\"catastrophicAlerts\":" + breaches + "}",
                    warnings,
                    clock.instant());
        });
    }

    @Bean
    JobHandler collectBarsJobHandler(EodMarketPipelineService market, ObjectMapper json, Clock clock) {
        return handler(
                "COLLECT_BARS",
                context -> count(market.collectBars(payload(context, json).marketDate()), clock.instant()));
    }

    @Bean
    JobHandler validateBarsJobHandler(EodMarketPipelineService market, ObjectMapper json, Clock clock) {
        return handler(
                "VALIDATE_BARS",
                context -> count(market.validateBars(payload(context, json).marketDate()), clock.instant()));
    }

    @Bean
    JobHandler collectCorporateActionsJobHandler(EodMarketPipelineService market, ObjectMapper json, Clock clock) {
        return handler(
                "COLLECT_CORPORATE_ACTIONS",
                context -> count(
                        market.collectCorporateActions(payload(context, json).marketDate()), clock.instant()));
    }

    @Bean
    JobHandler checkFilingsJobHandler(FundamentalsCollectionService fundamentals, DurableJobStore jobs, Clock clock) {
        return handler("CHECK_FILINGS", context -> {
            var result = fundamentals.checkFilings();
            if (context.analysisRunId() == null && result.affected() > 0) {
                enqueueNext(jobs, context, "COLLECT_FUNDAMENTALS", clock.instant());
            }
            return count(result, clock.instant());
        });
    }

    @Bean
    JobHandler collectFundamentalsJobHandler(
            FundamentalsCollectionService fundamentals, DurableJobStore jobs, Clock clock) {
        return handler("COLLECT_FUNDAMENTALS", context -> {
            var result = fundamentals.collectFundamentals();
            if (context.analysisRunId() == null) {
                enqueueNext(jobs, context, "NORMALIZE_FINANCIALS", clock.instant());
            }
            return count(result, clock.instant());
        });
    }

    @Bean
    JobHandler normalizeFinancialsJobHandler(
            FinancialFactNormalizationService normalization, DurableJobStore jobs, Clock clock) {
        return handler("NORMALIZE_FINANCIALS", context -> {
            var affected = normalization.normalizeAll();
            if (context.analysisRunId() == null) {
                enqueueNext(jobs, context, "COMPUTE_FINANCIAL_HEALTH", clock.instant());
            }
            return success(affected, clock.instant());
        });
    }

    @Bean
    JobHandler computeFinancialHealthJobHandler(FinancialHealthApplicationService financialHealth, Clock clock) {
        return handler("COMPUTE_FINANCIAL_HEALTH", context -> success(financialHealth.computeAll(), clock.instant()));
    }

    @Bean
    JobHandler collectEstimatesJobHandler(EstimateCollectionService estimates, Clock clock) {
        return handler("COLLECT_ESTIMATES", context -> count(estimates.collectAll(), clock.instant()));
    }

    @Bean
    JobHandler computeRevisionsJobHandler(EstimateRevisionApplicationService revisions, Clock clock) {
        return handler("COMPUTE_REVISIONS", context -> success(revisions.computeAll(), clock.instant()));
    }

    @Bean
    JobHandler collectEarningsCalendarJobHandler(EarningsIntelligenceApplicationService earnings, Clock clock) {
        return handler("COLLECT_EARNINGS_CALENDAR", context -> {
            var result = earnings.collectCalendar();
            var warnings = result.observations() == 0 ? List.of("EARNINGS_CALENDAR_UNAVAILABLE") : List.<String>of();
            return new JobExecutionResult(
                    warnings.isEmpty() ? "SUCCEEDED" : "PARTIAL",
                    "{\"observations\":" + result.observations() + ",\"affected\":" + result.affected() + "}",
                    warnings,
                    clock.instant());
        });
    }

    @Bean
    JobHandler computeValuationJobHandler(ValuationApplicationService valuation, Clock clock) {
        return handler("COMPUTE_VALUATION", context -> success(valuation.computeAll(), clock.instant()));
    }

    @Bean
    JobHandler computeEarningsRiskJobHandler(EarningsIntelligenceApplicationService earnings, Clock clock) {
        return handler("COMPUTE_EARNINGS_RISK", context -> {
            var affected = earnings.computeReactions() + earnings.computeRisk();
            return success(affected, clock.instant());
        });
    }

    @Bean
    JobHandler postEarningsReanalysisJobHandler(
            PostEarningsReanalysisService reanalysis, ObjectMapper json, Clock clock) {
        return handler(
                "POST_EARNINGS_REANALYSIS",
                context -> success(reanalysis.reanalyze(requiredUser(payload(context, json))), clock.instant()));
    }

    @Bean
    JobHandler computeIndicatorsJobHandler(EodMarketPipelineService market, ObjectMapper json, Clock clock) {
        return handler(
                "COMPUTE_INDICATORS",
                context -> count(market.computeIndicators(payload(context, json).marketDate()), clock.instant()));
    }

    @Bean
    JobHandler computePriceStateJobHandler(PriceStateApplicationService priceState, ObjectMapper json, Clock clock) {
        return handler(
                "COMPUTE_PRICE_STATE",
                context -> success(priceState.computeAll(payload(context, json).marketDate()), clock.instant()));
    }

    @Bean
    JobHandler collectMacroJobHandler(
            PortfolioAnalysisPipelineService portfolio, MacroApplicationService macro, ObjectMapper json, Clock clock) {
        return handler("COLLECT_MACRO", context -> {
            var date = payload(context, json).marketDate();
            var result = macro.collect(date);
            var breadth = portfolio.collectBreadthMacro(date);
            var warnings = result.observations() == 0 ? List.of("MACRO_DATA_UNAVAILABLE") : List.<String>of();
            return new JobExecutionResult(
                    warnings.isEmpty() ? "SUCCEEDED" : "PARTIAL",
                    "{\"observations\":" + result.observations() + ",\"affected\":" + (result.affected() + breadth)
                            + "}",
                    warnings,
                    clock.instant());
        });
    }

    @Bean
    JobHandler computeRegimeJobHandler(PortfolioAnalysisPipelineService portfolio, ObjectMapper json, Clock clock) {
        return handler(
                "COMPUTE_REGIME",
                context ->
                        success(portfolio.computeRegime(payload(context, json).marketDate()), clock.instant()));
    }

    @Bean
    JobHandler syncPortfolioJobHandler(PortfolioAnalysisPipelineService portfolio, ObjectMapper json, Clock clock) {
        return handler(
                "SYNC_PORTFOLIO",
                context -> success(portfolio.syncPortfolio(requiredUser(payload(context, json))), clock.instant()));
    }

    @Bean
    JobHandler capturePositionMarksJobHandler(
            PortfolioAnalysisPipelineService portfolio, ObjectMapper json, Clock clock) {
        return handler("CAPTURE_POSITION_MARKS", context -> {
            var result = portfolio.capturePositionMarks(requiredUser(payload(context, json)));
            var warnings = new java.util.ArrayList<String>();
            if (result.missing() > 0) warnings.add("POSITION_MARK_MISSING");
            if (result.stale() > 0) warnings.add("POSITION_MARK_STALE");
            return new JobExecutionResult(
                    result.healthy() ? "SUCCEEDED" : "PARTIAL",
                    "{\"marked\":" + result.marked() + ",\"missing\":" + result.missing() + ",\"stale\":"
                            + result.stale() + "}",
                    warnings,
                    clock.instant());
        });
    }

    @Bean
    JobHandler computeDrawdownJobHandler(PortfolioAnalysisPipelineService portfolio, ObjectMapper json, Clock clock) {
        return handler("COMPUTE_DRAWDOWN_SOURCE", context -> {
            var payload = payload(context, json);
            return success(portfolio.computeDrawdown(requiredUser(payload), payload.marketDate()), clock.instant());
        });
    }

    @Bean
    JobHandler recalculateStopsJobHandler(PortfolioAnalysisPipelineService portfolio, ObjectMapper json, Clock clock) {
        return handler("RECALCULATE_STOPS", context -> {
            var payload = payload(context, json);
            return success(portfolio.recalculateStops(requiredUser(payload), payload.marketDate()), clock.instant());
        });
    }

    @Bean
    JobHandler updateThesesEventsJobHandler(
            PortfolioAnalysisPipelineService portfolio, ObjectMapper json, Clock clock) {
        return handler(
                "UPDATE_THESES_EVENTS",
                context ->
                        success(portfolio.updateThesesEvents(requiredUser(payload(context, json))), clock.instant()));
    }

    @Bean
    JobHandler computeHoldingAnalysisJobHandler(
            HoldingAnalysisApplicationService analysis, ObjectMapper json, Clock clock) {
        return handler(
                "COMPUTE_HOLDING_ANALYSIS",
                context -> success(
                        analysis.analyzeAll(requiredUser(payload(context, json)))
                                .size(),
                        clock.instant()));
    }

    @Bean
    JobHandler updateDipEventsJobHandler(PortfolioAnalysisPipelineService portfolio, ObjectMapper json, Clock clock) {
        return handler(
                "UPDATE_DIP_EVENTS",
                context -> success(portfolio.updateDipEvents(requiredUser(payload(context, json))), clock.instant()));
    }

    @Bean
    JobHandler generateRecommendationsJobHandler(
            RecommendationGenerationService recommendations, ObjectMapper json, Clock clock) {
        return handler(
                "GENERATE_RECOMMENDATIONS",
                context -> success(
                        recommendations
                                .generateAll(requiredUser(payload(context, json)))
                                .size(),
                        clock.instant()));
    }

    @Bean
    JobHandler generateDailyDigestJobHandler(
            PortfolioAnalysisPipelineService portfolio, ObjectMapper json, Clock clock) {
        return handler(
                "GENERATE_DAILY_DIGEST",
                context -> success(portfolio.dailyDigest(requiredUser(payload(context, json))), clock.instant()));
    }

    @Bean
    JobHandler weeklyMemoJobHandler(PortfolioAnalysisPipelineService portfolio, Clock clock) {
        return handler("WEEKLY_MEMO", context -> success(portfolio.weeklyMemo(), clock.instant()));
    }

    @Bean
    JobHandler monthlyReviewJobHandler(PortfolioAnalysisPipelineService portfolio, Clock clock) {
        return handler("MONTHLY_REVIEW", context -> success(portfolio.monthlyReview(), clock.instant()));
    }

    private static JobHandler handler(String type, Function<JobExecutionContext, JobExecutionResult> action) {
        return new FunctionalJobHandler(type, action);
    }

    private static JobExecutionResult count(EodMarketPipelineService.StageCount count, Instant at) {
        var warnings = count.observations() == 0 ? List.of("NO_OBSERVATIONS") : List.<String>of();
        return new JobExecutionResult(
                warnings.isEmpty() ? "SUCCEEDED" : "PARTIAL",
                "{\"observations\":" + count.observations() + ",\"affected\":" + count.affected() + "}",
                warnings,
                at);
    }

    private static JobExecutionResult count(FundamentalsCollectionService.CollectionResult count, Instant at) {
        var warnings = count.observations() == 0 ? List.of("NO_OBSERVATIONS") : List.<String>of();
        return new JobExecutionResult(
                warnings.isEmpty() ? "SUCCEEDED" : "PARTIAL",
                "{\"observations\":" + count.observations() + ",\"affected\":" + count.affected() + "}",
                warnings,
                at);
    }

    private static JobExecutionResult count(EstimateCollectionService.CollectionResult count, Instant at) {
        var warnings = count.observations() == 0 ? List.of("ESTIMATES_UNAVAILABLE") : List.<String>of();
        return new JobExecutionResult(
                warnings.isEmpty() ? "SUCCEEDED" : "PARTIAL",
                "{\"observations\":" + count.observations() + ",\"affected\":" + count.affected() + "}",
                warnings,
                at);
    }

    private static JobExecutionResult success(int affected, Instant at) {
        return JobExecutionResult.succeeded("{\"affected\":" + affected + "}", at);
    }

    private static void enqueueNext(
            DurableJobStore jobs, JobExecutionContext context, String type, Instant scheduledAt) {
        var base = context.idempotencyKey().replaceFirst(":(?:COLLECT_FUNDAMENTALS|NORMALIZE_FINANCIALS)$", "");
        if (!jobs.enqueue(type, base + ":" + type, context.payloadJson(), 30, scheduledAt)) {
            throw new IllegalStateException("Fundamentals dependency could not be enqueued: " + type);
        }
    }

    private static PipelinePayload payload(JobExecutionContext context, ObjectMapper json) {
        try {
            var node = json.readTree(context.payloadJson());
            var runId = uuid(node, "runId");
            var userId = uuid(node, "userId");
            var date = node.get("marketDate") == null
                    ? LocalDate.now()
                    : LocalDate.parse(node.get("marketDate").asText());
            return new PipelinePayload(runId, userId, date);
        } catch (JacksonException | java.time.format.DateTimeParseException exception) {
            throw new PermanentDataException("INVALID_JOB_PAYLOAD", "Job payload is invalid");
        }
    }

    private static UUID uuid(tools.jackson.databind.JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) return null;
        try {
            return UUID.fromString(value.asText());
        } catch (IllegalArgumentException exception) {
            throw new PermanentDataException("INVALID_JOB_PAYLOAD", "Job UUID is invalid");
        }
    }

    private static UUID requiredUser(PipelinePayload payload) {
        if (payload.userId() == null) throw new PermanentDataException("MISSING_JOB_USER", "Job user is required");
        return payload.userId();
    }

    record PipelinePayload(UUID runId, UUID userId, LocalDate marketDate) {}

    private record FunctionalJobHandler(String jobType, Function<JobExecutionContext, JobExecutionResult> action)
            implements JobHandler {
        @Override
        public JobExecutionResult execute(JobExecutionContext context) {
            return action.apply(context);
        }
    }
}
