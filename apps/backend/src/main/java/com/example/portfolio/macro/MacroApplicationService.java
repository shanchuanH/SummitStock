package com.example.portfolio.macro;

import com.example.portfolio.analysis.application.StrategyDefinitionLoader;
import com.example.portfolio.analysis.replay.DecisionAsOfContext;
import com.example.portfolio.configuration.PortfolioProperties;
import com.example.portfolio.market.provider.ProviderCallException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class MacroApplicationService {
    public static final List<String> CORE_SERIES =
            List.of("VIXCLS", "VIX3M", "VXNCLS", "BAMLH0A0HYM2", "DGS10", "DGS2", "FEDFUNDS", "DFII10");
    private final MacroDataProvider provider;
    private final JdbcClient jdbc;
    private final Clock clock;
    private final StrategyDefinitionLoader strategies;
    private final PortfolioProperties properties;
    private final MacroFactorEngine engine = new MacroFactorEngine();
    private final VolatilityContextEngine volatilityEngine = new VolatilityContextEngine();

    public MacroApplicationService(
            MacroDataProvider provider,
            JdbcClient jdbc,
            Clock clock,
            StrategyDefinitionLoader strategies,
            PortfolioProperties properties) {
        this.provider = provider;
        this.jdbc = jdbc;
        this.clock = clock;
        this.strategies = strategies;
        this.properties = properties;
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
        var context = DecisionAsOfContext.marketClose(marketDate, properties.strategyVersion());
        var ninetyDaysEarlier =
                new DecisionAsOfContext(marketDate.minusDays(90), context.dataCutoff(), context.strategyVersion());
        var result = engine.evaluate(new MacroFactorEngine.Input(
                latest("VIXCLS", context),
                history("VIXCLS", context),
                latest("BAMLH0A0HYM2", context),
                history("BAMLH0A0HYM2", context),
                latest("DGS10", context),
                latest("DGS10", ninetyDaysEarlier),
                latest("DGS2", context),
                latest("FEDFUNDS", context),
                realizedVolatilityStress));
        var volatilityPolicy = strategies.loadVolatilityResearchPolicy(properties.strategyConfigPath());
        var volatility = volatilityEngine.evaluate(new VolatilityContextEngine.Input(
                latest("VIXCLS", context),
                history("VIXCLS", context),
                latest("VIX3M", context),
                latest("VXNCLS", context),
                history("VXNCLS", context),
                volatilityPolicy.vixTermFlatLower(),
                volatilityPolicy.vixTermBackwardation(),
                volatilityPolicy.techPremiumElevatedRatio()));
        var checksum = sha256(marketDate + "|" + result + "|" + volatility);
        return jdbc.sql(
                        """
                        INSERT INTO macro_factor_snapshot (
                          id,market_date,volatility_stress,credit_stress,rate_stress,curve_state,stress_resilience,
                          quality,evidence_checksum,data_as_of,created_at,vix_level,vix_percentile_5y,vix_delta_1d,
                          vix_delta_2d,vix_delta_5d,vix3m_level,vix_term_ratio,vix_term_state,vxn_level,
                          vxn_percentile_5y,vxn_delta_1d,vxn_delta_2d,vxn_delta_5d,vxn_vix_ratio,vxn_vix_spread,
                          tech_stress_state,ten_year_yield,two_year_yield,fed_funds_rate,ten_year_real_yield
                        ) VALUES (
                          UUID_TO_BIN(:id),:date,:volatility,:credit,:rate,:curve,:resilience,:quality,:checksum,:now,:now,
                          :vix,:vixPercentile,:vixDelta1d,:vixDelta2d,:vixDelta5d,:vix3m,:vixTermRatio,
                          :vixTermState,:vxn,:vxnPercentile,:vxnDelta1d,:vxnDelta2d,:vxnDelta5d,:vxnVixRatio,
                          :vxnVixSpread,:techStressState,:tenYearYield,:twoYearYield,:fedFunds,:tenYearRealYield
                        ) ON DUPLICATE KEY UPDATE
                          volatility_stress=VALUES(volatility_stress),credit_stress=VALUES(credit_stress),
                          rate_stress=VALUES(rate_stress),curve_state=VALUES(curve_state),
                          stress_resilience=VALUES(stress_resilience),quality=VALUES(quality),
                          evidence_checksum=VALUES(evidence_checksum),data_as_of=VALUES(data_as_of),
                          vix_level=VALUES(vix_level),vix_percentile_5y=VALUES(vix_percentile_5y),
                          vix_delta_1d=VALUES(vix_delta_1d),vix_delta_2d=VALUES(vix_delta_2d),
                          vix_delta_5d=VALUES(vix_delta_5d),vix3m_level=VALUES(vix3m_level),
                          vix_term_ratio=VALUES(vix_term_ratio),vix_term_state=VALUES(vix_term_state),
                          vxn_level=VALUES(vxn_level),vxn_percentile_5y=VALUES(vxn_percentile_5y),
                          vxn_delta_1d=VALUES(vxn_delta_1d),vxn_delta_2d=VALUES(vxn_delta_2d),
                          vxn_delta_5d=VALUES(vxn_delta_5d),vxn_vix_ratio=VALUES(vxn_vix_ratio),
                          vxn_vix_spread=VALUES(vxn_vix_spread),tech_stress_state=VALUES(tech_stress_state),
                          ten_year_yield=VALUES(ten_year_yield),two_year_yield=VALUES(two_year_yield),
                          fed_funds_rate=VALUES(fed_funds_rate),ten_year_real_yield=VALUES(ten_year_real_yield)
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
                .param("vix", volatility.vix())
                .param("vixPercentile", volatility.vixPercentile())
                .param("vixDelta1d", volatility.vixDelta1d())
                .param("vixDelta2d", volatility.vixDelta2d())
                .param("vixDelta5d", volatility.vixDelta5d())
                .param("vix3m", volatility.vix3m())
                .param("vixTermRatio", volatility.vixTermRatio())
                .param("vixTermState", volatility.vixTermState().name())
                .param("vxn", volatility.vxn())
                .param("vxnPercentile", volatility.vxnPercentile())
                .param("vxnDelta1d", volatility.vxnDelta1d())
                .param("vxnDelta2d", volatility.vxnDelta2d())
                .param("vxnDelta5d", volatility.vxnDelta5d())
                .param("vxnVixRatio", volatility.vxnVixRatio())
                .param("vxnVixSpread", volatility.vxnVixSpread())
                .param("techStressState", volatility.techStressState().name())
                .param("tenYearYield", latest("DGS10", context))
                .param("twoYearYield", latest("DGS2", context))
                .param("fedFunds", latest("FEDFUNDS", context))
                .param("tenYearRealYield", latest("DFII10", context))
                .update();
    }

    public MacroSnapshot latestFactors(LocalDate marketDate) {
        return latestFactors(DecisionAsOfContext.marketClose(marketDate, "CURRENT"));
    }

    public MacroSnapshot latestFactors(DecisionAsOfContext context) {
        return jdbc.sql(
                        """
                        SELECT stress_resilience stressResilience,volatility_stress volatilityStress,
                               credit_stress creditStress,quality
                        FROM macro_factor_snapshot WHERE market_date<=:date AND data_as_of<=:cutoff
                        ORDER BY market_date DESC,data_as_of DESC LIMIT 1
                        """)
                .param("date", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .query(MacroSnapshot.class)
                .optional()
                .orElse(new MacroSnapshot(null, null, null, "MISSING"));
    }

    public VolatilitySnapshot latestVolatility(LocalDate marketDate) {
        return latestVolatility(DecisionAsOfContext.marketClose(marketDate, "CURRENT"));
    }

    public VolatilitySnapshot latestVolatility(DecisionAsOfContext context) {
        return jdbc.sql(
                        """
                        SELECT vix_level vix,vix_percentile_5y vixPercentile,vix_delta_1d vixDelta1d,
                               vix_delta_2d vixDelta2d,vix_delta_5d vixDelta5d,vix3m_level vix3m,
                               vix_term_ratio vixTermRatio,vix_term_state vixTermState,vxn_level vxn,
                               vxn_percentile_5y vxnPercentile,vxn_delta_1d vxnDelta1d,
                               vxn_delta_2d vxnDelta2d,vxn_delta_5d vxnDelta5d,vxn_vix_ratio vxnVixRatio,
                               vxn_vix_spread vxnVixSpread,tech_stress_state techStressState,quality
                        FROM macro_factor_snapshot WHERE market_date<=:date AND data_as_of<=:cutoff
                        ORDER BY market_date DESC,data_as_of DESC LIMIT 1
                        """)
                .param("date", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .query(VolatilitySnapshot.class)
                .optional()
                .orElse(VolatilitySnapshot.missing());
    }

    public MacroBackgroundSnapshot latestMacroBackground(LocalDate marketDate) {
        return latestMacroBackground(DecisionAsOfContext.marketClose(marketDate, "CURRENT"));
    }

    public MacroBackgroundSnapshot latestMacroBackground(DecisionAsOfContext context) {
        return jdbc.sql(
                        """
                        SELECT ten_year_yield tenYearYield,two_year_yield twoYearYield,
                               fed_funds_rate fedFundsRate,ten_year_real_yield tenYearRealYield,
                               rate_stress rateStress,curve_state curveState,quality
                        FROM macro_factor_snapshot WHERE market_date<=:date AND data_as_of<=:cutoff
                        ORDER BY market_date DESC,data_as_of DESC LIMIT 1
                        """)
                .param("date", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .query(MacroBackgroundSnapshot.class)
                .optional()
                .orElse(new MacroBackgroundSnapshot(null, null, null, null, null, "MISSING", "MISSING"));
    }

    public BigDecimal latestValue(String code, LocalDate marketDate) {
        return latestValue(code, DecisionAsOfContext.marketClose(marketDate, "CURRENT"));
    }

    public BigDecimal latestValue(String code, DecisionAsOfContext context) {
        return latest(code, context);
    }

    private BigDecimal latest(String code, DecisionAsOfContext context) {
        return jdbc.sql(
                        "SELECT value_decimal FROM macro_observation WHERE series_code=:code AND observation_date<=:date AND data_as_of<=:cutoff ORDER BY observation_date DESC,data_as_of DESC LIMIT 1")
                .param("code", code)
                .param("date", context.marketDate())
                .param("cutoff", context.dataCutoff())
                .query(BigDecimal.class)
                .optional()
                .orElse(null);
    }

    List<BigDecimal> history(String code, DecisionAsOfContext context) {
        var observations = jdbc.sql(
                        """
                        SELECT observation_date observationDate,value_decimal value,data_as_of dataAsOf
                        FROM macro_observation
                        WHERE series_code=:code AND observation_date<=:marketDate
                          AND observation_date>=:fromDate AND data_as_of<=:cutoff
                        ORDER BY observation_date,data_as_of,created_at
                        """)
                .param("code", code)
                .param("marketDate", context.marketDate())
                .param("fromDate", context.marketDate().minusYears(5))
                .param("cutoff", context.dataCutoff())
                .query(HistoryObservation.class)
                .list();
        return selectHistoryVersions(observations, context);
    }

    static List<BigDecimal> selectHistoryVersions(List<HistoryObservation> observations, DecisionAsOfContext context) {
        var selected = new LinkedHashMap<LocalDate, HistoryObservation>();
        var fromDate = context.marketDate().minusYears(5);
        observations.stream()
                .filter(value -> !value.observationDate().isBefore(fromDate))
                .filter(value -> !value.observationDate().isAfter(context.marketDate()))
                .filter(value -> !value.dataAsOf().isAfter(context.dataCutoff()))
                .sorted(java.util.Comparator.comparing(HistoryObservation::observationDate)
                        .thenComparing(HistoryObservation::dataAsOf))
                .forEach(value -> selected.put(value.observationDate(), value));
        return selected.values().stream().map(HistoryObservation::value).toList();
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

    public record VolatilitySnapshot(
            BigDecimal vix,
            BigDecimal vixPercentile,
            BigDecimal vixDelta1d,
            BigDecimal vixDelta2d,
            BigDecimal vixDelta5d,
            BigDecimal vix3m,
            BigDecimal vixTermRatio,
            String vixTermState,
            BigDecimal vxn,
            BigDecimal vxnPercentile,
            BigDecimal vxnDelta1d,
            BigDecimal vxnDelta2d,
            BigDecimal vxnDelta5d,
            BigDecimal vxnVixRatio,
            BigDecimal vxnVixSpread,
            String techStressState,
            String quality) {
        static VolatilitySnapshot missing() {
            return new VolatilitySnapshot(
                    null, null, null, null, null, null, null, "MISSING", null, null, null, null, null, null, null,
                    "MISSING", "MISSING");
        }
    }

    public record MacroBackgroundSnapshot(
            BigDecimal tenYearYield,
            BigDecimal twoYearYield,
            BigDecimal fedFundsRate,
            BigDecimal tenYearRealYield,
            BigDecimal rateStress,
            String curveState,
            String quality) {}

    record HistoryObservation(LocalDate observationDate, BigDecimal value, java.time.Instant dataAsOf) {}
}
