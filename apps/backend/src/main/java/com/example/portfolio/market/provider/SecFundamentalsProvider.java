package com.example.portfolio.market.provider;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
@Profile("!test & !local-fixture")
@ConditionalOnProperty(name = "portfolio.providers.fundamentals.type", havingValue = "sec")
public final class SecFundamentalsProvider implements FundamentalsProvider {
    private static final String PROVIDER = "sec-edgar";
    private static final String NORMALIZATION_VERSION = "sec-companyfacts-v1";
    private static final Duration FUNDAMENTAL_FRESHNESS = Duration.ofDays(550);
    private static final Map<String, List<String>> CONCEPTS = conceptMappings();
    private final ProviderHttpClient http;
    private final ProviderProperties properties;
    private final Clock clock;
    private final DataQualityPolicy quality = new DataQualityPolicy();

    public SecFundamentalsProvider(ProviderHttpClient http, ProviderProperties properties, Clock clock) {
        this.http = http;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String providerId() {
        return PROVIDER;
    }

    @Override
    public ProviderModels.FilingIndexResult fetchFilings(String cik) {
        var normalizedCik = normalizeCik(cik);
        var payload = get("/submissions/CIK" + normalizedCik + ".json");
        var recent = path(payload.json(), "filings", "recent");
        var accessions = requiredArray(recent, "accessionNumber");
        var forms = requiredArray(recent, "form");
        var filed = requiredArray(recent, "filingDate");
        var reports = requiredArray(recent, "reportDate");
        var documents = requiredArray(recent, "primaryDocument");
        int count = accessions.size();
        if (forms.size() != count || filed.size() != count || reports.size() != count || documents.size() != count) {
            throw new ProviderCallException(
                    "PROVIDER_MALFORMED", "SEC recent filing arrays are misaligned", 200, false);
        }
        var filings = new ArrayList<ProviderModels.Filing>();
        LocalDate latest = null;
        for (int index = 0; index < count; index++) {
            var accession = accessions.get(index).asText();
            var filingDate = parseDate(filed.get(index).asText(), "filing date", true);
            var periodEnd = parseDate(reports.get(index).asText(), "period end", false);
            var document = documents.get(index).asText();
            filings.add(new ProviderModels.Filing(
                    accession,
                    forms.get(index).asText(),
                    filingDate,
                    periodEnd,
                    filingSource(normalizedCik, accession, document),
                    document));
            if (filingDate != null && (latest == null || filingDate.isAfter(latest))) latest = filingDate;
        }
        var warnings = filings.isEmpty() ? List.of("NO_RECENT_FILINGS") : List.<String>of();
        var sourceTimestamp =
                latest == null ? clock.instant() : latest.atStartOfDay().toInstant(ZoneOffset.UTC);
        var status = quality.assess(
                new DataQualityPolicy.Evidence(
                        1, filings.isEmpty() ? 0 : 1, false, false, sourceTimestamp, FUNDAMENTAL_FRESHNESS),
                clock.instant());
        return new ProviderModels.FilingIndexResult(
                normalizedCik, filings, provenance(payload.raw(), sourceTimestamp, status, warnings));
    }

    @Override
    public ProviderModels.CompanyFactsResult fetchCompanyFacts(String cik) {
        var normalizedCik = normalizeCik(cik);
        var payload = get("/api/xbrl/companyfacts/CIK" + normalizedCik + ".json");
        var usGaap = path(payload.json(), "facts", "us-gaap");
        var facts = new ArrayList<ProviderModels.CompanyFact>();
        var resolvedMetrics = new LinkedHashSet<String>();
        var warnings = new ArrayList<String>();
        boolean unitConflict = false;
        LocalDate latestFiled = null;
        for (var mapping : CONCEPTS.entrySet()) {
            var selected = firstConcept(usGaap, mapping.getValue());
            if (selected == null) {
                warnings.add("MISSING_METRIC:" + mapping.getKey());
                continue;
            }
            var units = selected.node().get("units");
            if (units == null || !units.isObject()) {
                warnings.add("MISSING_UNITS:" + selected.name());
                continue;
            }
            int populatedUnits = 0;
            for (var unitEntry : units.properties()) {
                if (unitEntry.getValue().isArray() && !unitEntry.getValue().isEmpty()) populatedUnits++;
                for (var observation : unitEntry.getValue()) {
                    var parsed =
                            fact(mapping.getKey(), selected.name(), unitEntry.getKey(), normalizedCik, observation);
                    if (parsed != null) {
                        facts.add(parsed);
                        resolvedMetrics.add(mapping.getKey());
                        if (latestFiled == null || parsed.filingDate().isAfter(latestFiled))
                            latestFiled = parsed.filingDate();
                    }
                }
            }
            unitConflict |= populatedUnits > 1;
        }
        if (unitConflict) warnings.add("MULTIPLE_UNITS_FOR_MAPPED_CONCEPT");
        var sourceTimestamp = latestFiled == null
                ? clock.instant()
                : latestFiled.atStartOfDay().toInstant(ZoneOffset.UTC);
        var status = quality.assess(
                new DataQualityPolicy.Evidence(
                        CONCEPTS.size(),
                        resolvedMetrics.size(),
                        false,
                        unitConflict,
                        sourceTimestamp,
                        FUNDAMENTAL_FRESHNESS),
                clock.instant());
        return new ProviderModels.CompanyFactsResult(
                normalizedCik, facts, provenance(payload.raw(), sourceTimestamp, status, warnings));
    }

    private ProviderModels.CompanyFact fact(
            String businessMetric, String concept, String unit, String cik, JsonNode node) {
        var value = node.get("val");
        var filed = parseDate(text(node, "filed"), "fact filing date", false);
        var end = parseDate(text(node, "end"), "fact period end", false);
        var accession = text(node, "accn");
        var form = text(node, "form");
        if (value == null || !value.isNumber() || filed == null || end == null || accession == null || form == null) {
            return null;
        }
        return new ProviderModels.CompanyFact(
                businessMetric,
                "us-gaap",
                concept,
                unit,
                value.decimalValue(),
                parseDate(text(node, "start"), "fact period start", false),
                end,
                filed,
                accession,
                form,
                filingIndexSource(cik, accession),
                integer(node, "fy"),
                text(node, "fp"));
    }

    private ProviderHttpClient.Payload get(String path) {
        var base = properties.fundamentals().baseUrl().replaceAll("/+$", "");
        return http.get(
                URI.create(base + path),
                Map.of("User-Agent", properties.fundamentals().userAgent()));
    }

    private ProviderModels.Provenance provenance(
            String raw, Instant sourceTimestamp, ProviderModels.QualityStatus status, List<String> warnings) {
        return new ProviderModels.Provenance(
                PROVIDER,
                sourceTimestamp,
                clock.instant(),
                ProviderPayloads.sha256(raw),
                NORMALIZATION_VERSION,
                status,
                warnings);
    }

    private static SelectedConcept firstConcept(JsonNode taxonomy, List<String> candidates) {
        for (String candidate : candidates) {
            var node = taxonomy.get(candidate);
            if (node != null && node.isObject()) return new SelectedConcept(candidate, node);
        }
        return null;
    }

    private static JsonNode path(JsonNode root, String... fields) {
        var current = root;
        for (String field : fields) {
            current = current == null ? null : current.get(field);
            if (current == null || current.isNull()) {
                throw new ProviderCallException("PROVIDER_MALFORMED", "Missing SEC field: " + field, 200, false);
            }
        }
        return current;
    }

    private static JsonNode requiredArray(JsonNode root, String field) {
        var value = root.get(field);
        if (value == null || !value.isArray()) {
            throw new ProviderCallException("PROVIDER_MALFORMED", "Missing SEC array: " + field, 200, false);
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }

    private static Integer integer(JsonNode node, String field) {
        var value = node.get(field);
        return value == null || value.isNull() || !value.canConvertToInt() ? null : value.asInt();
    }

    private static LocalDate parseDate(String value, String label, boolean required) {
        if (value == null || value.isBlank()) {
            if (required) throw new ProviderCallException("PROVIDER_MALFORMED", "Missing " + label, 200, false);
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (java.time.format.DateTimeParseException exception) {
            throw new ProviderCallException("PROVIDER_MALFORMED", "Invalid " + label, 200, false);
        }
    }

    static String normalizeCik(String value) {
        if (value == null || !value.strip().matches("\\d{1,10}")) {
            throw new IllegalArgumentException("CIK must contain one to ten digits");
        }
        return String.format("%010d", Long.parseLong(value.strip()));
    }

    private static String filingSource(String cik, String accession, String document) {
        return "https://www.sec.gov/Archives/edgar/data/" + Long.parseLong(cik) + "/" + accession.replace("-", "") + "/"
                + document;
    }

    private static String filingIndexSource(String cik, String accession) {
        return "https://www.sec.gov/Archives/edgar/data/" + Long.parseLong(cik) + "/" + accession.replace("-", "") + "/"
                + accession + "-index.html";
    }

    private static Map<String, List<String>> conceptMappings() {
        var mappings = new LinkedHashMap<String, List<String>>();
        mappings.put(
                "Revenue",
                List.of("RevenueFromContractWithCustomerExcludingAssessedTax", "Revenues", "SalesRevenueNet"));
        mappings.put("GrossProfit", List.of("GrossProfit"));
        mappings.put("OperatingIncome", List.of("OperatingIncomeLoss"));
        mappings.put("NetIncome", List.of("NetIncomeLoss"));
        mappings.put("CashAndCashEquivalents", List.of("CashAndCashEquivalentsAtCarryingValue"));
        mappings.put(
                "LongTermDebt",
                List.of("LongTermDebtNoncurrent", "LongTermDebt", "LongTermDebtAndFinanceLeaseObligationsCurrent"));
        mappings.put("CurrentAssets", List.of("AssetsCurrent"));
        mappings.put("CurrentLiabilities", List.of("LiabilitiesCurrent"));
        mappings.put("ShareholdersEquity", List.of("StockholdersEquity"));
        mappings.put("OperatingCashFlow", List.of("NetCashProvidedByUsedInOperatingActivities"));
        mappings.put("CapitalExpenditures", List.of("PaymentsToAcquirePropertyPlantAndEquipment"));
        mappings.put("DilutedEPS", List.of("EarningsPerShareDiluted"));
        mappings.put("SharesDiluted", List.of("WeightedAverageNumberOfDilutedSharesOutstanding"));
        return Map.copyOf(mappings);
    }

    private record SelectedConcept(String name, JsonNode node) {}
}
