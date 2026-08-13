package com.example.portfolio.macro;

import com.example.portfolio.market.provider.ProviderCallException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class MacroApplicationService {
    public static final List<String> CORE_SERIES =
            List.of("VIXCLS", "VIX3M", "BAMLH0A0HYM2", "DGS10", "DGS2", "FEDFUNDS");
    private final MacroDataProvider provider;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final MacroFactorEngine engine = new MacroFactorEngine();

    public MacroApplicationService(MacroDataProvider provider, JdbcClient jdbc, Clock clock) {
        this.provider = provider;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public CollectionResult collect(LocalDate marketDate) {
        int observations = 0;
        int affected = 0;
        var failed = new ArrayList<String>();
        var warnings = new ArrayList<String>();
        for (var code : CORE_SERIES) {
            final MacroDataProvider.MacroSeriesResult result;
            try {
                result = provider.fetch(code, marketDate.minusYears(5), marketDate);
            } catch (ProviderCallException exception) {
                failed.add(code);
                warnings.add(code + ":" + exception.code());
                continue;
            }
            observations += result.observations().size();
            for (var value : result.observations()) {
                var checksum = sha256(code + "|" + value + "|" + result.provider());
                affected += jdbc.sql(
                                """
                                INSERT IGNORE INTO macro_observation (
                                  id,series_code,observation_date,value_decimal,provider,quality,evidence_checksum,data_as_of,created_at
                                ) VALUES (UUID_TO_BIN(:id),:code,:date,:value,:provider,:quality,:checksum,:now,:now)
                                """)
                        .param("id", UUID.randomUUID().toString())
                        .param("code", code)
                        .param("date", value.date())
                        .param("value", value.value())
                        .param("provider", result.provider())
                        .param("quality", result.quality().name())
                        .param("checksum", checksum)
                        .param("now", result.dataAsOf())
                        .update();
            }
        }
        return new CollectionResult(observations, affected, failed, warnings);
    }

    public int computeFactors(LocalDate marketDate, BigDecimal realizedVolatilityStress) {
        var result = engine.evaluate(new MacroFactorEngine.Input(
                latest("VIXCLS", marketDate),
                history("VIXCLS", marketDate),
                latest("BAMLH0A0HYM2", marketDate),
                history("BAMLH0A0HYM2", marketDate),
                latest("DGS10", marketDate),
                latest("DGS10", marketDate.minusDays(90)),
                latest("DGS2", marketDate),
                latest("FEDFUNDS", marketDate),
                realizedVolatilityStress));
        var checksum = sha256(marketDate + "|" + result);
        return jdbc.sql(
                        """
                        INSERT IGNORE INTO macro_factor_snapshot (
                          id,market_date,volatility_stress,credit_stress,rate_stress,curve_state,stress_resilience,
                          quality,evidence_checksum,data_as_of,created_at
                        ) VALUES (UUID_TO_BIN(:id),:date,:volatility,:credit,:rate,:curve,:resilience,:quality,:checksum,:now,:now)
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("date", marketDate)
                .param("volatility", result.volatilityStress())
                .param("credit", result.creditStress())
                .param("rate", result.rateStress())
                .param("curve", result.curveState().name())
                .param("resilience", result.stressResilience())
                .param("quality", result.quality().name())
                .param("checksum", checksum)
                .param("now", clock.instant())
                .update();
    }

    public MacroSnapshot latestFactors(LocalDate marketDate) {
        return jdbc.sql(
                        """
                        SELECT stress_resilience stressResilience,volatility_stress volatilityStress,
                               credit_stress creditStress,quality
                        FROM macro_factor_snapshot WHERE market_date<=:date ORDER BY market_date DESC LIMIT 1
                        """)
                .param("date", marketDate)
                .query(MacroSnapshot.class)
                .optional()
                .orElse(new MacroSnapshot(null, null, null, "MISSING"));
    }

    public BigDecimal latestValue(String code, LocalDate marketDate) {
        return latest(code, marketDate);
    }

    private BigDecimal latest(String code, LocalDate date) {
        return jdbc.sql(
                        "SELECT value_decimal FROM macro_observation WHERE series_code=:code AND observation_date<=:date ORDER BY observation_date DESC LIMIT 1")
                .param("code", code)
                .param("date", date)
                .query(BigDecimal.class)
                .optional()
                .orElse(null);
    }

    private List<BigDecimal> history(String code, LocalDate date) {
        return jdbc.sql(
                        "SELECT value_decimal FROM macro_observation WHERE series_code=:code AND observation_date<=:date AND observation_date>=:fromDate ORDER BY observation_date")
                .param("code", code)
                .param("date", date)
                .param("fromDate", date.minusYears(5))
                .query(BigDecimal.class)
                .list();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record CollectionResult(
            int observations, int affected, List<String> failedInstruments, List<String> warnings) {
        public CollectionResult {
            failedInstruments = List.copyOf(failedInstruments);
            warnings = List.copyOf(warnings);
        }

        public CollectionResult(int observations, int affected) {
            this(observations, affected, List.of(), List.of());
        }
    }

    public record MacroSnapshot(
            BigDecimal stressResilience, BigDecimal volatilityStress, BigDecimal creditStress, String quality) {}
}
