package com.example.portfolio.fundamentals;

import com.example.portfolio.market.provider.ProviderModels;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Repository
public class FinancialEvidenceStore {
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public FinancialEvidenceStore(JdbcClient jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    public List<InstrumentRef> eligibleInstruments() {
        return jdbc.sql(
                        "SELECT BIN_TO_UUID(id) id, symbol, cik FROM instrument WHERE active=TRUE AND asset_type='EQUITY' AND cik IS NOT NULL")
                .query(InstrumentRef.class)
                .list();
    }

    @Transactional
    public int saveFilingPeriods(UUID instrumentId, ProviderModels.FilingIndexResult result) {
        int inserted = 0;
        for (var filing : result.filings()) {
            var type = periodType(filing.form());
            if (type == null || filing.periodEnd() == null || filing.filingDate() == null) continue;
            inserted += jdbc.sql(
                            """
                            INSERT IGNORE INTO financial_period (
                                id, instrument_id, fiscal_year, fiscal_quarter, period_type, start_date,
                                end_date, filed_at, accession_number, form_type, source, quality, created_at
                            ) VALUES (
                                UUID_TO_BIN(:id), UUID_TO_BIN(:instrumentId), :year, :quarter, :type, NULL,
                                :endDate, :filedAt, :accession, :form, :source, :quality, :now
                            )
                            """)
                    .param("id", UUID.randomUUID().toString())
                    .param("instrumentId", instrumentId.toString())
                    .param("year", filing.periodEnd().getYear())
                    .param("quarter", null)
                    .param("type", type)
                    .param("endDate", filing.periodEnd())
                    .param("filedAt", filing.filingDate())
                    .param("accession", filing.accessionNumber())
                    .param("form", filing.form())
                    .param("source", filing.sourceUrl())
                    .param("quality", result.provenance().qualityStatus().name())
                    .param("now", clock.instant())
                    .update();
        }
        if (inserted > 0) invalidateDerivedEvidence(instrumentId);
        return inserted;
    }

    private void invalidateDerivedEvidence(UUID instrumentId) {
        jdbc.sql(
                        """
                        UPDATE valuation_snapshot v JOIN position p ON p.id=v.position_id
                        SET v.valid_until=:now
                        WHERE p.instrument_id=UUID_TO_BIN(:instrumentId) AND v.valid_until>:now
                        """)
                .param("instrumentId", instrumentId.toString())
                .param("now", clock.instant())
                .update();
        jdbc.sql(
                        """
                        UPDATE recommendation r JOIN position p ON p.id=r.position_id
                        SET r.valid_until=:now
                        WHERE p.instrument_id=UUID_TO_BIN(:instrumentId) AND r.valid_until>:now
                        """)
                .param("instrumentId", instrumentId.toString())
                .param("now", clock.instant())
                .update();
    }

    @Transactional
    public int saveFacts(UUID instrumentId, ProviderModels.CompanyFactsResult result, FinancialConceptMapping mapping) {
        int inserted = 0;
        for (var fact : result.facts()) {
            var canonical = mapping.resolve(fact.taxonomy(), fact.concept())
                    .map(Enum::name)
                    .orElse(null);
            var checksum = sha256(String.join(
                    "|",
                    instrumentId.toString(),
                    fact.taxonomy(),
                    fact.concept(),
                    fact.unit(),
                    fact.value().toPlainString(),
                    fact.periodEnd().toString(),
                    fact.accessionNumber()));
            inserted += jdbc.sql(
                            """
                            INSERT IGNORE INTO financial_fact_observation (
                                id, instrument_id, canonical_metric, taxonomy, concept, unit, value_decimal,
                                period_start, period_end, filed_at, accession_number, form_type, source,
                                provider_fiscal_year, provider_fiscal_period,
                                provider, quality, checksum, data_as_of, created_at
                            ) VALUES (
                                UUID_TO_BIN(:id), UUID_TO_BIN(:instrumentId), :metric, :taxonomy, :concept, :unit, :value,
                                :periodStart, :periodEnd, :filedAt, :accession, :form, :source,
                                :fiscalYear, :fiscalPeriod,
                                :provider, :quality, :checksum, :dataAsOf, :now
                            )
                            """)
                    .param("id", UUID.randomUUID().toString())
                    .param("instrumentId", instrumentId.toString())
                    .param("metric", canonical)
                    .param("taxonomy", fact.taxonomy())
                    .param("concept", fact.concept())
                    .param("unit", fact.unit())
                    .param("value", fact.value())
                    .param("periodStart", fact.periodStart())
                    .param("periodEnd", fact.periodEnd())
                    .param("filedAt", fact.filingDate())
                    .param("accession", fact.accessionNumber())
                    .param("form", fact.form())
                    .param("source", fact.sourceUri())
                    .param("fiscalYear", fact.fiscalYear())
                    .param("fiscalPeriod", fact.fiscalPeriod())
                    .param("provider", result.provenance().provider())
                    .param("quality", result.provenance().qualityStatus().name())
                    .param("checksum", checksum)
                    .param("dataAsOf", result.provenance().fetchedAt())
                    .param("now", clock.instant())
                    .update();
        }
        return inserted;
    }

    public List<ProviderModels.CompanyFact> facts(UUID instrumentId) {
        return jdbc.sql(
                        """
                        SELECT canonical_metric businessMetric, taxonomy, concept, unit, value_decimal value,
                               period_start periodStart, period_end periodEnd, filed_at filingDate,
                               accession_number accessionNumber, form_type form, source sourceUri,
                               provider_fiscal_year fiscalYear, provider_fiscal_period fiscalPeriod
                        FROM financial_fact_observation
                        WHERE instrument_id=UUID_TO_BIN(:instrumentId)
                        ORDER BY period_end, filed_at
                        """)
                .param("instrumentId", instrumentId.toString())
                .query(ProviderModels.CompanyFact.class)
                .list();
    }

    @Transactional
    public UUID savePeriod(UUID instrumentId, FinancialPeriodResolver.ResolvedPeriod period) {
        jdbc.sql(
                        """
                        INSERT INTO financial_period (
                            id, instrument_id, fiscal_year, fiscal_quarter, period_type, start_date,
                            end_date, filed_at, accession_number, form_type, source, quality, created_at
                        ) VALUES (
                            UUID_TO_BIN(:id), UUID_TO_BIN(:instrumentId), :year, :quarter, :type, :startDate,
                            :endDate, :filedAt, :accession, :form, :source, :quality, :now
                        ) ON DUPLICATE KEY UPDATE
                            fiscal_year=VALUES(fiscal_year), fiscal_quarter=VALUES(fiscal_quarter),
                            start_date=COALESCE(VALUES(start_date), start_date), filed_at=VALUES(filed_at),
                            source=VALUES(source), quality=VALUES(quality)
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("instrumentId", instrumentId.toString())
                .param("year", period.fiscalYear())
                .param("quarter", period.fiscalQuarter())
                .param("type", period.periodType().name())
                .param("startDate", period.startDate())
                .param("endDate", period.endDate())
                .param("filedAt", period.filedAt())
                .param("accession", period.accessionNumber())
                .param("form", period.formType())
                .param("source", period.source())
                .param("quality", period.quality().name())
                .param("now", clock.instant())
                .update();
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(id) FROM financial_period
                        WHERE instrument_id=UUID_TO_BIN(:instrumentId) AND accession_number=:accession
                          AND period_type=:type AND end_date=:endDate
                        """)
                .param("instrumentId", instrumentId.toString())
                .param("accession", period.accessionNumber())
                .param("type", period.periodType().name())
                .param("endDate", period.endDate())
                .query(UUID.class)
                .single();
    }

    @Transactional
    public int saveMetrics(
            UUID instrumentId, UUID periodId, FinancialMetricEngine.MetricResult result, LocalDate filedAt) {
        int inserted = 0;
        var evidence = sha256(result.values().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue().toPlainString())
                .collect(java.util.stream.Collectors.joining("|")));
        for (var metric : result.values().entrySet()) {
            var unit = unit(metric.getKey());
            var provenance = result.provenance()
                    .getOrDefault(
                            metric.getKey(),
                            new FinancialPeriodResolver.MetricProvenance(
                                    java.util.List.of(), "financial-derived-v2", "DERIVED_FORMULA"));
            inserted += jdbc.sql(
                            """
                            INSERT IGNORE INTO financial_metric_snapshot (
                                id, instrument_id, period_id, metric_code, value_decimal, unit,
                                calculation_version, source_concepts, mapping_version, aggregation_method,
                                quality, evidence_checksum, data_as_of, created_at
                            ) VALUES (
                                UUID_TO_BIN(:id), UUID_TO_BIN(:instrumentId), UUID_TO_BIN(:periodId), :metric, :value, :unit,
                                'financial-v2', CAST(:sourceConcepts AS JSON), :mappingVersion, :aggregationMethod,
                                :quality, :checksum, :dataAsOf, :now
                            )
                            """)
                    .param("id", UUID.randomUUID().toString())
                    .param("instrumentId", instrumentId.toString())
                    .param("periodId", periodId.toString())
                    .param("metric", metric.getKey().name())
                    .param("value", metric.getValue())
                    .param("unit", unit)
                    .param("sourceConcepts", json.writeValueAsString(provenance.sourceConcepts()))
                    .param("mappingVersion", provenance.mappingVersion())
                    .param("aggregationMethod", provenance.aggregationMethod())
                    .param("quality", result.quality().name())
                    .param("checksum", sha256(evidence + "|" + metric.getKey()))
                    .param("dataAsOf", filedAt.atStartOfDay().toInstant(ZoneOffset.UTC))
                    .param("now", clock.instant())
                    .update();
        }
        return inserted;
    }

    public List<MetricPeriod> metricPeriods(UUID instrumentId) {
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(p.id) periodId, p.end_date endDate, p.filed_at filedAt,
                               m.metric_code metricCode, m.value_decimal value, m.quality quality
                        FROM financial_period p JOIN financial_metric_snapshot m ON m.period_id=p.id
                        WHERE p.instrument_id=UUID_TO_BIN(:instrumentId)
                        ORDER BY p.end_date DESC, p.filed_at DESC
                        """)
                .param("instrumentId", instrumentId.toString())
                .query(MetricPeriod.class)
                .list();
    }

    @Transactional
    public int saveHealth(
            UUID instrumentId,
            UUID periodId,
            String strategyVersion,
            String configHash,
            FinancialHealthEngine.HealthResult result,
            LocalDate filedAt,
            String evidenceChecksum) {
        return jdbc.sql(
                        """
                        INSERT IGNORE INTO financial_health_snapshot (
                            id, instrument_id, period_id, strategy_version, config_hash, overall_status,
                            growth_status, profitability_status, cash_flow_status, balance_sheet_status,
                            dilution_status, positives, negatives, quality, evidence_checksum, data_as_of, created_at
                        ) VALUES (
                            UUID_TO_BIN(:id), UUID_TO_BIN(:instrumentId), UUID_TO_BIN(:periodId), :strategyVersion, :configHash, :overall,
                            :growth, :profitability, :cashFlow, :balanceSheet, :dilution,
                            CAST(:positives AS JSON), CAST(:negatives AS JSON), :quality, :checksum, :dataAsOf, :now
                        )
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("instrumentId", instrumentId.toString())
                .param("periodId", periodId.toString())
                .param("strategyVersion", strategyVersion)
                .param("configHash", configHash)
                .param("overall", result.overall().name())
                .param("growth", result.growth().name())
                .param("profitability", result.profitability().name())
                .param("cashFlow", result.cashFlow().name())
                .param("balanceSheet", result.balanceSheet().name())
                .param("dilution", result.dilution().name())
                .param("positives", json.writeValueAsString(result.positives()))
                .param("negatives", json.writeValueAsString(result.negatives()))
                .param("quality", result.quality().name())
                .param("checksum", evidenceChecksum)
                .param("dataAsOf", filedAt.atStartOfDay().toInstant(ZoneOffset.UTC))
                .param("now", clock.instant())
                .update();
    }

    static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String periodType(String form) {
        if (form == null) return null;
        var normalized = form.toUpperCase(java.util.Locale.ROOT);
        if (normalized.startsWith("10-K") || normalized.startsWith("20-F") || normalized.startsWith("40-F")) {
            return "ANNUAL";
        }
        return normalized.startsWith("10-Q") ? "QUARTERLY" : null;
    }

    private static boolean isRatio(FinancialMetric metric) {
        return metric.name().endsWith("MARGIN")
                || metric.name().endsWith("YOY")
                || metric.name().endsWith("CAGR")
                || metric == FinancialMetric.FCF_CONVERSION
                || metric == FinancialMetric.NET_DEBT_TO_FCF
                || metric == FinancialMetric.CURRENT_RATIO;
    }

    private static String unit(FinancialMetric metric) {
        if (isRatio(metric)) return "RATIO";
        if (metric == FinancialMetric.DILUTED_SHARES) return "SHARES";
        if (metric == FinancialMetric.DILUTED_EPS) return "USD_PER_SHARE";
        return "USD";
    }

    public record InstrumentRef(UUID id, String symbol, String cik) {}

    public record MetricPeriod(
            UUID periodId, LocalDate endDate, LocalDate filedAt, String metricCode, BigDecimal value, String quality) {}
}
