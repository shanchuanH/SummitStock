package com.example.portfolio.analysis.capital;

import com.example.portfolio.analysis.application.PublishedStrategyService;
import com.example.portfolio.strategy.market.EvidenceQuality;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public final class CapitalBaseService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final JdbcClient jdbc;
    private final PublishedStrategyService strategies;

    public CapitalBaseService(JdbcClient jdbc, PublishedStrategyService strategies) {
        this.jdbc = jdbc;
        this.strategies = strategies;
    }

    public CapitalBase calculate(UUID userId) {
        var values = jdbc.sql(
                        """
                        SELECT COALESCE((SELECT SUM(p.market_value) FROM position p
                                         JOIN investment_account a ON a.id=p.account_id
                                         WHERE a.user_id=UUID_TO_BIN(:userId) AND p.status='OPEN'),0) invested,
                               COALESCE((SELECT SUM(c.current_amount) FROM cash_bucket c
                                         WHERE c.user_id=UUID_TO_BIN(:userId)),0) trackedCash,
                               COALESCE((SELECT SUM(c.current_amount) FROM cash_bucket c
                                         WHERE c.user_id=UUID_TO_BIN(:userId) AND c.bucket_type='EMERGENCY'),0) emergencyAllocated,
                               COALESCE((SELECT SUM(h.estimated_value) FROM compensation_holding h
                                         WHERE h.user_id=UUID_TO_BIN(:userId) AND h.vesting_status='UNVESTED'),0) compensation
                        """)
                .param("userId", userId.toString())
                .query(CapitalRow.class)
                .single();
        var emergency = values.emergencyAllocated()
                .min(values.trackedCash())
                .min(strategies.current().emergencyCashFloor());
        var deployable = values.trackedCash().subtract(emergency).max(ZERO);
        var investable = values.invested().add(deployable);
        var totalLiquid = values.invested().add(values.trackedCash());
        var quality = totalLiquid.signum() == 0 ? EvidenceQuality.MISSING : EvidenceQuality.HEALTHY;
        return new CapitalBase(
                values.invested(),
                values.trackedCash(),
                emergency,
                deployable,
                investable,
                totalLiquid,
                values.compensation(),
                quality);
    }

    public boolean capture(UUID userId, Instant dataAsOf) {
        var capital = calculate(userId);
        var strategy = strategies.current();
        var checksum = checksum(userId + ":" + strategy.configHash() + ":" + capital);
        return jdbc.sql(
                                """
                        INSERT IGNORE INTO portfolio_capital_snapshot (
                            id,user_id,strategy_version,strategy_config_hash,invested_tradable_assets,
                            tracked_cash,emergency_reserve,deployable_cash,investable_assets,total_liquid_assets,
                            unvested_compensation_value,quality_status,evidence_checksum,data_as_of,created_at
                        ) VALUES (
                            UUID_TO_BIN(:id),UUID_TO_BIN(:userId),:version,:configHash,:invested,
                            :cash,:emergency,:deployable,:investable,:liquid,:compensation,:quality,:checksum,:asOf,UTC_TIMESTAMP(6)
                        )
                        """)
                        .param("id", UUID.randomUUID().toString())
                        .param("userId", userId.toString())
                        .param("version", strategy.version())
                        .param("configHash", strategy.configHash())
                        .param("invested", capital.investedTradableAssets())
                        .param("cash", capital.trackedCash())
                        .param("emergency", capital.emergencyReserve())
                        .param("deployable", capital.deployableCash())
                        .param("investable", capital.investableAssets())
                        .param("liquid", capital.totalLiquidAssets())
                        .param("compensation", capital.unvestedCompensationValue())
                        .param("quality", capital.quality().name())
                        .param("checksum", checksum)
                        .param("asOf", dataAsOf)
                        .update()
                == 1;
    }

    private static String checksum(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    record CapitalRow(
            BigDecimal invested, BigDecimal trackedCash, BigDecimal emergencyAllocated, BigDecimal compensation) {}
}
