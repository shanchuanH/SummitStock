package com.example.portfolio.portfolioimport.infrastructure;

import com.example.portfolio.portfolioimport.domain.ImportBatchStatus;
import com.example.portfolio.portfolioimport.domain.ImportRowStatus;
import com.example.portfolio.portfolioimport.domain.ImportedCash;
import com.example.portfolio.portfolioimport.domain.ImportedHolding;
import com.example.portfolio.portfolioimport.domain.PortfolioImportPreview;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class FidelityCsvParser {
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private final ObjectMapper json;
    private final Clock clock;

    public FidelityCsvParser(ObjectMapper json, Clock clock) {
        this.json = json;
        this.clock = clock;
    }

    public PortfolioImportPreview parse(byte[] content, String filename) {
        if (content.length == 0) throw new IllegalArgumentException("The Fidelity CSV is empty");
        if (content.length > MAX_BYTES) throw new IllegalArgumentException("The Fidelity CSV exceeds 5 MB");
        var records = csv(new String(content, StandardCharsets.UTF_8).replace("\uFEFF", ""));
        int headerIndex = findHeader(records);
        var headers = normalizedHeaders(records.get(headerIndex));
        var holdings = new ArrayList<ImportedHolding>();
        var cash = new ArrayList<ImportedCash>();
        var errors = new ArrayList<String>();
        var globalWarnings = new LinkedHashSet<String>();
        int parsedRows = 0;
        int validRows = 0;
        int errorRows = 0;
        BigDecimal invested = BigDecimal.ZERO;
        BigDecimal cashValue = BigDecimal.ZERO;

        for (int index = headerIndex + 1; index < records.size(); index++) {
            var values = records.get(index);
            if (values.stream().allMatch(String::isBlank)) continue;
            var row = row(headers, values);
            if (isFooter(row)) continue;
            parsedRows++;
            int rowNumber = index + 1;
            var accountName = value(row, "accountname");
            var accountNumber = maskAccount(value(row, "accountnumber"));
            var symbol = normalizeSymbol(value(row, "symbol"));
            var description = value(row, "description");
            var sourceType = value(row, "type");
            var warnings = new ArrayList<String>();
            var quantity = decimal(value(row, "quantity"));
            var lastPrice = decimal(value(row, "lastprice"));
            var currentValue = decimal(value(row, "currentvalue"));
            var averageCost = decimal(value(row, "averagecostbasis"));
            var costBasis = decimal(value(row, "costbasistotal"));
            var rowType = rowType(symbol, description, sourceType);
            var assetType = assetType(rowType, description, sourceType);

            if ("HOLDING".equals(rowType) && currentValue == null && quantity != null && lastPrice != null) {
                currentValue = quantity.multiply(lastPrice);
                warnings.add("CURRENT_VALUE_DERIVED_FROM_QUANTITY_AND_LAST_PRICE");
            }
            if ("HOLDING".equals(rowType) && averageCost == null && costBasis == null) {
                warnings.add("COST_BASIS_MISSING");
                globalWarnings.add(
                        "One or more holdings have no cost basis; exact gain/loss analysis will be unavailable.");
            }

            var status = status(rowType, assetType, symbol, quantity, currentValue, warnings);
            if (status == ImportRowStatus.ERROR) {
                errorRows++;
                errors.add("Row " + rowNumber + " could not be classified or is missing required values.");
            } else {
                validRows++;
            }
            if ("CASH".equals(rowType)) {
                cash.add(new ImportedCash(
                        rowNumber,
                        accountName,
                        accountNumber,
                        symbol,
                        description,
                        currentValue,
                        status,
                        List.copyOf(warnings)));
                if (status != ImportRowStatus.ERROR && currentValue != null) {
                    cashValue = cashValue.add(currentValue);
                }
            } else {
                holdings.add(new ImportedHolding(
                        rowNumber,
                        accountName,
                        accountNumber,
                        symbol,
                        description,
                        assetType,
                        quantity,
                        lastPrice,
                        currentValue,
                        averageCost,
                        costBasis,
                        rowType,
                        status,
                        List.copyOf(warnings),
                        rawJson(row)));
                if (status != ImportRowStatus.ERROR && "HOLDING".equals(rowType) && currentValue != null) {
                    invested = invested.add(currentValue);
                }
            }
        }
        if (parsedRows == 0) throw new IllegalArgumentException("The Fidelity CSV has no position rows");
        return new PortfolioImportPreview(
                null,
                ImportBatchStatus.PREVIEW,
                0,
                accounts(holdings, cash),
                List.copyOf(holdings),
                List.copyOf(cash),
                List.copyOf(globalWarnings),
                List.copyOf(errors),
                new PortfolioImportPreview.Summary(parsedRows, validRows, errorRows, invested, cashValue),
                clock.instant());
    }

    private static int findHeader(List<List<String>> records) {
        for (int index = 0; index < records.size(); index++) {
            var normalized = records.get(index).stream()
                    .map(FidelityCsvParser::normalizeHeader)
                    .toList();
            if (normalized.contains("symbol")
                    && (normalized.contains("quantity") || normalized.contains("currentvalue"))) {
                return index;
            }
        }
        throw new IllegalArgumentException("The Fidelity CSV header is not recognized");
    }

    private static List<String> normalizedHeaders(List<String> values) {
        return values.stream().map(FidelityCsvParser::normalizeHeader).toList();
    }

    private static String normalizeHeader(String value) {
        var normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return switch (normalized) {
            case "account", "accountno", "accountnumbermasked" -> "accountnumber";
            case "name", "accounttitle" -> "accountname";
            case "securitydescription" -> "description";
            case "shares", "sharequantity" -> "quantity";
            case "price", "last" -> "lastprice";
            case "marketvalue", "value" -> "currentvalue";
            case "averagecost", "avgcostbasis", "averagecostpershare" -> "averagecostbasis";
            case "costbasis", "totalcostbasis" -> "costbasistotal";
            case "securitytype", "assettype" -> "type";
            default -> normalized;
        };
    }

    private static Map<String, String> row(List<String> headers, List<String> values) {
        var result = new LinkedHashMap<String, String>();
        for (int index = 0; index < headers.size(); index++) {
            result.put(
                    headers.get(index),
                    index < values.size() ? values.get(index).trim() : "");
        }
        return result;
    }

    private static String value(Map<String, String> row, String key) {
        var value = row.get(key);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static boolean isFooter(Map<String, String> row) {
        var marker = String.join(" ", row.values()).toUpperCase(Locale.ROOT);
        return marker.contains("ACCOUNT TOTAL")
                || marker.contains("TOTAL ACCOUNT VALUE")
                || marker.contains("PENDING ACTIVITY")
                || marker.contains("THE DATA AND INFORMATION IN THIS SPREADSHEET")
                || marker.contains("BROKERAGE SERVICES ARE PROVIDED BY FIDELITY")
                || marker.contains("DATE DOWNLOADED");
    }

    private static String normalizeSymbol(String symbol) {
        if (symbol == null) return null;
        var normalized = symbol.toUpperCase(Locale.ROOT).replace("*", "").trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String maskAccount(String account) {
        if (account == null) return null;
        var compact = account.replaceAll("\\s", "");
        if (compact.length() <= 4 || compact.matches("^[Xx*.-]+\\d{1,4}$")) return compact;
        return "***" + compact.substring(compact.length() - 4);
    }

    static BigDecimal decimal(String raw) {
        if (raw == null) return null;
        var value = raw.trim();
        if (value.isEmpty() || value.equalsIgnoreCase("N/A") || value.equals("--")) return null;
        boolean parentheses = value.startsWith("(") && value.endsWith(")");
        value = value.replace("$", "")
                .replace(",", "")
                .replace("%", "")
                .replace("+", "")
                .trim();
        if (parentheses) value = "-" + value.substring(1, value.length() - 1);
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String rowType(String symbol, String description, String type) {
        var evidence =
                ((description == null ? "" : description) + " " + (type == null ? "" : type)).toUpperCase(Locale.ROOT);
        if (evidence.contains("UNVESTED") || evidence.contains(" RSU") || evidence.contains("RESTRICTED STOCK")) {
            return "UNVESTED_COMPENSATION";
        }
        if ("SPAXX".equals(symbol)
                || evidence.contains("MONEY MARKET")
                || evidence.equals("CASH")
                || evidence.contains("CASH BALANCE")) {
            return "CASH";
        }
        if (symbol != null) return "HOLDING";
        return "UNKNOWN";
    }

    private static String assetType(String rowType, String description, String type) {
        if ("CASH".equals(rowType)) return "CASH";
        if ("UNVESTED_COMPENSATION".equals(rowType)) return "COMPENSATION";
        var evidence =
                ((description == null ? "" : description) + " " + (type == null ? "" : type)).toUpperCase(Locale.ROOT);
        if (evidence.contains("ETF") || evidence.contains("EXCHANGE TRADED")) return "ETF";
        if (evidence.contains("MUTUAL FUND")) return "MUTUAL_FUND";
        if (evidence.contains("EQUITY")
                || evidence.contains("STOCK")
                || evidence.contains("COMMON")
                || evidence.contains(" INC")
                || evidence.contains("CORP")
                || evidence.contains(" COM")
                || evidence.contains("CAP STK")
                || evidence.contains(" ADR")
                || evidence.contains(" ORD")
                || evidence.contains(" LTD")
                || evidence.contains(" PLC")) return "EQUITY";
        return "UNKNOWN";
    }

    private static ImportRowStatus status(
            String rowType,
            String assetType,
            String symbol,
            BigDecimal quantity,
            BigDecimal currentValue,
            List<String> warnings) {
        if ("UNKNOWN".equals(rowType)) return ImportRowStatus.ERROR;
        if ("CASH".equals(rowType)) return currentValue == null ? ImportRowStatus.ERROR : ImportRowStatus.VALID;
        if ("UNVESTED_COMPENSATION".equals(rowType)) {
            return symbol == null ? ImportRowStatus.ERROR : ImportRowStatus.WARNING;
        }
        if (symbol == null
                || quantity == null
                || currentValue == null
                || quantity.signum() < 0
                || currentValue.signum() < 0
                || "UNKNOWN".equals(assetType)) {
            return ImportRowStatus.ERROR;
        }
        return warnings.isEmpty() ? ImportRowStatus.VALID : ImportRowStatus.WARNING;
    }

    private String rawJson(Map<String, String> row) {
        try {
            var sanitized = new LinkedHashMap<>(row);
            sanitized.computeIfPresent("accountnumber", (ignored, value) -> maskAccount(value));
            return json.writeValueAsString(sanitized);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Unable to normalize Fidelity row", exception);
        }
    }

    private static List<PortfolioImportPreview.ImportAccount> accounts(
            List<ImportedHolding> holdings, List<ImportedCash> cash) {
        var accounts = new LinkedHashMap<String, PortfolioImportPreview.ImportAccount>();
        holdings.forEach(row -> accounts.putIfAbsent(
                accountKey(row.accountName(), row.accountNumberMasked()),
                new PortfolioImportPreview.ImportAccount(row.accountName(), row.accountNumberMasked())));
        cash.forEach(row -> accounts.putIfAbsent(
                accountKey(row.accountName(), row.accountNumberMasked()),
                new PortfolioImportPreview.ImportAccount(row.accountName(), row.accountNumberMasked())));
        return List.copyOf(accounts.values());
    }

    private static String accountKey(String name, String number) {
        return (number == null ? "" : number) + "|" + (name == null ? "" : name);
    }

    private static List<List<String>> csv(String content) {
        var records = new ArrayList<List<String>>();
        var row = new ArrayList<String>();
        var field = new StringBuilder();
        boolean quoted = false;
        for (int index = 0; index < content.length(); index++) {
            char current = content.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < content.length() && content.charAt(index + 1) == '"') {
                    field.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                row.add(field.toString());
                field.setLength(0);
            } else if ((current == '\n' || current == '\r') && !quoted) {
                if (current == '\r' && index + 1 < content.length() && content.charAt(index + 1) == '\n') index++;
                row.add(field.toString());
                field.setLength(0);
                records.add(List.copyOf(row));
                row.clear();
            } else {
                field.append(current);
            }
        }
        if (quoted) throw new IllegalArgumentException("The Fidelity CSV has an unterminated quoted field");
        if (!row.isEmpty() || !field.isEmpty()) {
            row.add(field.toString());
            records.add(List.copyOf(row));
        }
        return records;
    }
}
