package com.example.portfolio.analysis.allocation;

import com.example.portfolio.analysis.application.PublishedStrategyService;
import com.example.portfolio.analysis.capital.CapitalBaseService;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import java.math.BigDecimal;
import java.math.MathContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PortfolioAllocationService {
    private final JdbcClient jdbc;
    private final PublishedStrategyService strategies;
    private final CapitalBaseService capitalBases;
    private final Clock clock;

    public PortfolioAllocationService(
            JdbcClient jdbc, PublishedStrategyService strategies, CapitalBaseService capitalBases, Clock clock) {
        this.jdbc = jdbc;
        this.strategies = strategies;
        this.capitalBases = capitalBases;
        this.clock = clock;
    }

    public Map<PortfolioSleeve, SleeveAllocation> calculate(UUID userId) {
        var strategy = strategies.current();
        var capital = capitalBases.calculate(userId);
        var investable = capital.investableAssets();
        var values = new EnumMap<PortfolioSleeve, BigDecimal>(PortfolioSleeve.class);
        for (var sleeve : PortfolioSleeve.values()) values.put(sleeve, BigDecimal.ZERO);
        for (var row : jdbc.sql(
                        """
                        SELECT p.classification,COALESCE(m.marked_market_value,0) marketValue
                        FROM position p JOIN investment_account a ON a.id=p.account_id
                        LEFT JOIN current_position_mark m ON m.position_id=p.id
                        WHERE a.user_id=UUID_TO_BIN(:userId) AND p.status='OPEN'
                        """)
                .param("userId", userId.toString())
                .query(PositionValue.class)
                .list()) {
            var sleeve = sleeve(HoldingClassification.valueOf(row.classification()));
            if (sleeve != null) values.merge(sleeve, row.marketValue(), BigDecimal::add);
        }
        values.put(PortfolioSleeve.CASH_RESERVE, capital.deployableCash());
        var result = new EnumMap<PortfolioSleeve, SleeveAllocation>(PortfolioSleeve.class);
        for (var sleeve : PortfolioSleeve.values()) {
            var amount = values.get(sleeve);
            var weight = investable.signum() == 0 ? BigDecimal.ZERO : amount.divide(investable, MathContext.DECIMAL64);
            var target = target(sleeve, strategy);
            var gap = target == null ? null : target.subtract(weight).max(BigDecimal.ZERO);
            result.put(
                    sleeve,
                    new SleeveAllocation(
                            sleeve, amount, weight, target, gap, primary(sleeve, strategy), false, capital.quality()));
        }
        return Map.copyOf(result);
    }

    public SleeveAllocation forPosition(UUID userId, HoldingClassification classification, String symbol) {
        var positionSleeve = sleeve(classification);
        if (positionSleeve == null) return null;
        var value = calculate(userId).get(positionSleeve);
        if (value == null) return null;
        return new SleeveAllocation(
                value.sleeve(),
                value.markedMarketValue(),
                value.currentWeight(),
                value.targetWeight(),
                value.gapWeight(),
                value.primaryInstrument(),
                symbol.equalsIgnoreCase(value.primaryInstrument()),
                value.quality());
    }

    @Transactional
    public int capture(UUID userId, Instant dataAsOf) {
        var strategy = strategies.current();
        int inserted = 0;
        for (var value : calculate(userId).values()) {
            var checksum = checksum(userId + ":" + value + ":" + strategy.configHash());
            inserted += jdbc.sql(
                            """
                            INSERT IGNORE INTO portfolio_allocation_snapshot (
                              id,user_id,sleeve_code,marked_market_value,current_weight,target_weight,gap_weight,
                              primary_instrument,quality_status,strategy_version,strategy_config_hash,
                              evidence_checksum,data_as_of,created_at) VALUES (
                              UUID_TO_BIN(:id),UUID_TO_BIN(:userId),:sleeve,:value,:weight,:target,:gap,:primary,
                              :quality,:version,:configHash,:checksum,:asOf,:createdAt)
                            """)
                    .param("id", UUID.randomUUID().toString())
                    .param("userId", userId.toString())
                    .param("sleeve", value.sleeve().name())
                    .param("value", value.markedMarketValue())
                    .param("weight", value.currentWeight())
                    .param("target", value.targetWeight())
                    .param("gap", value.gapWeight())
                    .param("primary", value.primaryInstrument())
                    .param("quality", value.quality().name())
                    .param("version", strategy.version())
                    .param("configHash", strategy.configHash())
                    .param("checksum", checksum)
                    .param("asOf", dataAsOf)
                    .param("createdAt", clock.instant())
                    .update();
        }
        return inserted;
    }

    private static PortfolioSleeve sleeve(HoldingClassification classification) {
        return switch (classification) {
            case CORE_BROAD_ETF -> PortfolioSleeve.BROAD_CORE;
            case CORE_TECH_ETF -> PortfolioSleeve.TECH_CORE;
            case QUALITY_STOCK, QUALITY_GROWTH_HIGH_VOL -> PortfolioSleeve.QUALITY;
            case THEMATIC_ETF -> PortfolioSleeve.THEMATIC;
            case TACTICAL_STOCK, CYCLICAL_TACTICAL, TURNAROUND_TACTICAL -> PortfolioSleeve.TACTICAL;
            case SPECULATIVE -> PortfolioSleeve.SPECULATIVE;
            default -> null;
        };
    }

    private static BigDecimal target(
            PortfolioSleeve sleeve, com.example.portfolio.analysis.domain.StrategyDefinition strategy) {
        return switch (sleeve) {
            case BROAD_CORE -> strategy.broadCoreTarget();
            case TECH_CORE -> strategy.techCoreTarget();
            default -> null;
        };
    }

    private static String primary(
            PortfolioSleeve sleeve, com.example.portfolio.analysis.domain.StrategyDefinition strategy) {
        return switch (sleeve) {
            case BROAD_CORE -> strategy.broadCorePrimaryInstrument();
            case TECH_CORE -> strategy.techCorePrimaryInstrument();
            default -> null;
        };
    }

    private static String checksum(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    record PositionValue(String classification, BigDecimal marketValue) {}
}
