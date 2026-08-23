package com.example.portfolio.context;

import com.example.portfolio.analysis.application.PublishedStrategyService;
import com.example.portfolio.configuration.PortfolioProperties;
import com.example.portfolio.context.MarketContextStore.DrawdownWrite;
import com.example.portfolio.context.MarketContextStore.RegimeWrite;
import com.example.portfolio.strategy.market.DrawdownEngine;
import com.example.portfolio.strategy.market.MarketRegimeEngine;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MarketContextService {
    private final MarketContextStore store;
    private final PortfolioProperties properties;
    private final Clock clock;
    private final PublishedStrategyService strategies;

    public MarketContextService(
            MarketContextStore store,
            PortfolioProperties properties,
            PublishedStrategyService strategies,
            Clock clock) {
        this.store = store;
        this.properties = properties;
        this.strategies = strategies;
        this.clock = clock;
    }

    public SavedRegime calculateRegime(MarketRegimeEngine.Input input, Instant dataAsOf) {
        var result = MarketRegimeEngine.classify(input);
        var evidence = CanonicalMarketRegimeEvidence.from(input);
        var inputs = evidence.canonicalJson();
        var checksum = evidence.checksum(properties.strategyVersion(), dataAsOf);
        int inserted = store.appendRegime(new RegimeWrite(
                UUID.randomUUID(),
                properties.strategyVersion(),
                result.label().name(),
                result.score(),
                result.trendScore(),
                result.momentumScore(),
                result.breadthScore(),
                result.stressScore(),
                result.confidence().name(),
                result.tacticalCapFivePercent(),
                input.quality().name(),
                inputs,
                jsonArray(result.narratives()),
                jsonArray(result.ruleIds()),
                checksum,
                dataAsOf,
                clock.instant()));
        return new SavedRegime(result, inserted == 1, checksum);
    }

    public SavedDrawdown calculateDrawdown(
            UUID userId,
            DrawdownEngine.Input input,
            String positionAttributionJson,
            String clusterAttributionJson,
            Instant dataAsOf) {
        return calculateDrawdown(
                userId, input, positionAttributionJson, clusterAttributionJson, dataAsOf, input.currentEquity());
    }

    public SavedDrawdown calculateDrawdown(
            UUID userId,
            DrawdownEngine.Input input,
            String positionAttributionJson,
            String clusterAttributionJson,
            Instant dataAsOf,
            BigDecimal accountEquity) {
        var strategy = strategies.current();
        var result = DrawdownEngine.classify(
                input,
                new DrawdownEngine.Thresholds(
                        strategy.stopNewSpeculationAt().doubleValue(),
                        strategy.reduceTacticalCapacityAt().doubleValue(),
                        strategy.etfDipSetupAt().doubleValue(),
                        strategy.marketDrivenEtfDeploymentAt().doubleValue(),
                        strategy.painLine().doubleValue()));
        var canonical = userId + ":" + accountEquity + ":" + input + ":" + positionAttributionJson + ":"
                + clusterAttributionJson;
        var checksum = sha256(properties.strategyVersion() + ":drawdown:" + dataAsOf + ":" + canonical);
        int inserted = store.appendDrawdown(new DrawdownWrite(
                UUID.randomUUID(),
                userId,
                properties.strategyVersion(),
                accountEquity,
                result.highWaterMark(),
                BigDecimal.valueOf(result.drawdown()),
                result.state().name(),
                result.source().name(),
                result.marketDriven(),
                BigDecimal.valueOf(input.spyReturnFromPeak()),
                BigDecimal.valueOf(input.qqqReturnFromPeak()),
                BigDecimal.valueOf(input.breadth50()),
                BigDecimal.valueOf(input.stressLevel()),
                positionAttributionJson,
                clusterAttributionJson,
                result.confidence().name(),
                input.quality().name(),
                jsonArray(result.narratives()),
                jsonArray(result.ruleIds()),
                checksum,
                dataAsOf,
                clock.instant()));
        return new SavedDrawdown(result, inserted == 1, checksum);
    }

    private static String jsonArray(List<String> values) {
        return values.stream()
                .map(value -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static String sha256(String content) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record SavedRegime(MarketRegimeEngine.Result result, boolean inserted, String evidenceChecksum) {}

    public record SavedDrawdown(DrawdownEngine.Result result, boolean inserted, String evidenceChecksum) {}
}
