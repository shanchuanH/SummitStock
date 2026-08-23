package com.example.portfolio.portfolioimport.infrastructure;

import com.example.portfolio.portfolioimport.domain.ImportBatchStatus;
import com.example.portfolio.portfolioimport.domain.ImportRowStatus;
import com.example.portfolio.portfolioimport.domain.ImportSource;
import com.example.portfolio.portfolioimport.domain.ImportedCash;
import com.example.portfolio.portfolioimport.domain.ImportedHolding;
import com.example.portfolio.portfolioimport.domain.PortfolioImportPreview;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Repository
public class PortfolioImportStore {
    private final JdbcClient jdbc;
    private final ObjectMapper json;
    private final Clock clock;

    public PortfolioImportStore(JdbcClient jdbc, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public PortfolioImportPreview savePreview(
            String email,
            ImportSource source,
            String filename,
            String checksum,
            String parserRevision,
            PortfolioImportPreview parsed) {
        var userId = ensureUser(email);
        var existing = findBatch(email, source.name(), checksum, parserRevision);
        if (existing.isPresent()) return preview(email, existing.orElseThrow().id());
        var batchId = UUID.randomUUID();
        var summary = parsed.summary();
        jdbc.sql(
                        """
                        INSERT INTO portfolio_import_batch (
                            id, user_id, source, filename, status, source_checksum, parser_revision,
                            row_count, valid_row_count, error_row_count, data_as_of,
                            created_at, updated_at, version
                        ) VALUES (
                            UUID_TO_BIN(:id), UUID_TO_BIN(:userId), :source, :filename, 'PREVIEW', :checksum, :parserRevision,
                            :rowCount, :validCount, :errorCount, :dataAsOf, :now, :now, 0
                        )
                        """)
                .param("id", batchId.toString())
                .param("userId", userId.toString())
                .param("source", source.name())
                .param("filename", filename)
                .param("checksum", checksum)
                .param("parserRevision", parserRevision)
                .param("rowCount", summary.rowCount())
                .param("validCount", summary.validRowCount())
                .param("errorCount", summary.errorRowCount())
                .param("dataAsOf", parsed.dataAsOf())
                .param("now", clock.instant())
                .update();
        parsed.holdings().forEach(row -> insertRow(batchId, row));
        parsed.cash().forEach(row -> insertRow(batchId, row));
        return preview(email, batchId);
    }

    public PortfolioImportPreview preview(String email, UUID batchId) {
        var batch = batch(email, batchId);
        var rows = rows(batchId);
        var holdings = new ArrayList<ImportedHolding>();
        var cash = new ArrayList<ImportedCash>();
        var accounts = new LinkedHashMap<String, PortfolioImportPreview.ImportAccount>();
        var warnings = new LinkedHashSet<String>();
        var errors = new ArrayList<String>();
        BigDecimal invested = BigDecimal.ZERO;
        BigDecimal cashValue = BigDecimal.ZERO;
        for (var row : rows) {
            var rowWarnings = strings(row.warnings());
            warnings.addAll(rowWarnings);
            accounts.putIfAbsent(
                    accountKey(row.accountName(), row.accountNumberMasked()),
                    new PortfolioImportPreview.ImportAccount(row.accountName(), row.accountNumberMasked()));
            var status = ImportRowStatus.valueOf(row.status());
            if (status == ImportRowStatus.ERROR) errors.add("Row " + row.rowNumber() + " requires correction.");
            if ("CASH".equals(row.rowType())) {
                cash.add(new ImportedCash(
                        row.rowNumber(),
                        row.accountName(),
                        row.accountNumberMasked(),
                        row.symbol(),
                        row.description(),
                        row.currentValue(),
                        status,
                        rowWarnings));
                if (row.currentValue() != null) cashValue = cashValue.add(row.currentValue());
            } else {
                holdings.add(new ImportedHolding(
                        row.rowNumber(),
                        row.accountName(),
                        row.accountNumberMasked(),
                        row.symbol(),
                        row.description(),
                        row.assetType(),
                        row.quantity(),
                        row.lastPrice(),
                        row.currentValue(),
                        row.averageCost(),
                        row.costBasis(),
                        row.rowType(),
                        status,
                        rowWarnings,
                        row.rawJson()));
                if ("HOLDING".equals(row.rowType()) && row.currentValue() != null) {
                    invested = invested.add(row.currentValue());
                }
            }
        }
        return new PortfolioImportPreview(
                batch.id(),
                ImportBatchStatus.valueOf(batch.status()),
                batch.version(),
                List.copyOf(accounts.values()),
                List.copyOf(holdings),
                List.copyOf(cash),
                List.copyOf(warnings),
                List.copyOf(errors),
                new PortfolioImportPreview.Summary(
                        batch.rowCount(), batch.validRowCount(), batch.errorRowCount(), invested, cashValue),
                batch.dataAsOf() == null ? null : batch.dataAsOf().toInstant(ZoneOffset.UTC));
    }

    public Batch batch(String email, UUID batchId) {
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(b.id) id, BIN_TO_UUID(b.user_id) user_id, b.source, b.filename,
                               b.status, b.source_checksum, b.row_count, b.valid_row_count,
                               b.error_row_count, b.data_as_of, b.confirmed_at, b.version
                        FROM portfolio_import_batch b JOIN app_user u ON u.id=b.user_id
                        WHERE b.id=UUID_TO_BIN(:id) AND u.email=:email
                        """)
                .param("id", batchId.toString())
                .param("email", email)
                .query(Batch.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Import batch not found"));
    }

    public List<ImportRow> rows(UUID batchId) {
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(id) id, source_row_number AS `row_number`, account_name, account_number_masked,
                               symbol, description, asset_type, quantity, last_price, current_value,
                               average_cost, cost_basis, row_type, status, warnings, raw_json
                        FROM portfolio_import_row WHERE batch_id=UUID_TO_BIN(:batchId)
                        ORDER BY source_row_number
                        """)
                .param("batchId", batchId.toString())
                .query(ImportRow.class)
                .list();
    }

    public UUID ensureUser(String email) {
        var id = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT IGNORE INTO app_user (
                            id, email, password_hash, status, timezone, created_at, updated_at, version
                        ) VALUES (UUID_TO_BIN(:id), :email, '{external-session}', 'ACTIVE', 'UTC', :now, :now, 0)
                        """)
                .param("id", id.toString())
                .param("email", email)
                .param("now", clock.instant())
                .update();
        return jdbc.sql("SELECT BIN_TO_UUID(id) FROM app_user WHERE email=:email")
                .param("email", email)
                .query(UUID.class)
                .single();
    }

    private Optional<BatchIdentity> findBatch(String email, String source, String checksum, String parserRevision) {
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(b.id) id
                        FROM portfolio_import_batch b JOIN app_user u ON u.id=b.user_id
                        WHERE u.email=:email AND b.source=:source AND b.source_checksum=:checksum
                          AND b.parser_revision=:parserRevision
                        """)
                .param("email", email)
                .param("source", source)
                .param("checksum", checksum)
                .param("parserRevision", parserRevision)
                .query(BatchIdentity.class)
                .optional();
    }

    private void insertRow(UUID batchId, ImportedHolding row) {
        insertRow(
                batchId,
                row.rowNumber(),
                row.accountName(),
                row.accountNumberMasked(),
                row.symbol(),
                row.description(),
                row.assetType(),
                row.quantity(),
                row.lastPrice(),
                row.currentValue(),
                row.averageCost(),
                row.costBasis(),
                row.rowType(),
                row.status(),
                row.warnings(),
                row.rawJson());
    }

    private void insertRow(UUID batchId, ImportedCash row) {
        insertRow(
                batchId,
                row.rowNumber(),
                row.accountName(),
                row.accountNumberMasked(),
                row.symbol(),
                row.description(),
                "CASH",
                null,
                null,
                row.currentValue(),
                null,
                null,
                "CASH",
                row.status(),
                row.warnings(),
                "{}");
    }

    private void insertRow(
            UUID batchId,
            int rowNumber,
            String accountName,
            String accountNumber,
            String symbol,
            String description,
            String assetType,
            BigDecimal quantity,
            BigDecimal lastPrice,
            BigDecimal currentValue,
            BigDecimal averageCost,
            BigDecimal costBasis,
            String rowType,
            ImportRowStatus status,
            List<String> warnings,
            String rawJson) {
        jdbc.sql(
                        """
                        INSERT INTO portfolio_import_row (
                            id, batch_id, source_row_number, account_name, account_number_masked, symbol,
                            description, asset_type, quantity, last_price, current_value, average_cost,
                            cost_basis, row_type, status, warnings, raw_json, created_at
                        ) VALUES (
                            UUID_TO_BIN(:id), UUID_TO_BIN(:batchId), :rowNumber, :accountName, :accountNumber,
                            :symbol, :description, :assetType, :quantity, :lastPrice, :currentValue, :averageCost,
                            :costBasis, :rowType, :status, CAST(:warnings AS JSON), CAST(:rawJson AS JSON), :now
                        )
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("batchId", batchId.toString())
                .param("rowNumber", rowNumber)
                .param("accountName", accountName)
                .param("accountNumber", accountNumber)
                .param("symbol", symbol)
                .param("description", description)
                .param("assetType", assetType)
                .param("quantity", quantity)
                .param("lastPrice", lastPrice)
                .param("currentValue", currentValue)
                .param("averageCost", averageCost)
                .param("costBasis", costBasis)
                .param("rowType", rowType)
                .param("status", status.name())
                .param("warnings", write(warnings))
                .param("rawJson", rawJson)
                .param("now", clock.instant())
                .update();
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Unable to store portfolio import", exception);
        }
    }

    private List<String> strings(String value) {
        try {
            return json.readValue(value, new TypeReference<>() {});
        } catch (JacksonException exception) {
            throw new IllegalStateException("Stored import warnings are invalid", exception);
        }
    }

    private static String accountKey(String name, String number) {
        return (number == null ? "" : number) + "|" + (name == null ? "" : name);
    }

    record BatchIdentity(UUID id) {}

    public record Batch(
            UUID id,
            UUID userId,
            String source,
            String filename,
            String status,
            String sourceChecksum,
            int rowCount,
            int validRowCount,
            int errorRowCount,
            LocalDateTime dataAsOf,
            LocalDateTime confirmedAt,
            long version) {}

    public record ImportRow(
            UUID id,
            int rowNumber,
            String accountName,
            String accountNumberMasked,
            String symbol,
            String description,
            String assetType,
            BigDecimal quantity,
            BigDecimal lastPrice,
            BigDecimal currentValue,
            BigDecimal averageCost,
            BigDecimal costBasis,
            String rowType,
            String status,
            String warnings,
            String rawJson) {}
}
