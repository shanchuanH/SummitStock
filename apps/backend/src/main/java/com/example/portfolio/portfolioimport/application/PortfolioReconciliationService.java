package com.example.portfolio.portfolioimport.application;

import com.example.portfolio.analysis.application.PublishedStrategyService;
import com.example.portfolio.market.InstrumentResolutionService;
import com.example.portfolio.portfolioimport.infrastructure.PortfolioImportStore;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PortfolioReconciliationService {
    private final JdbcClient jdbc;
    private final Clock clock;
    private final InstrumentResolutionService instrumentResolution;
    private final PublishedStrategyService strategies;

    public PortfolioReconciliationService(
            JdbcClient jdbc,
            Clock clock,
            InstrumentResolutionService instrumentResolution,
            PublishedStrategyService strategies) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.instrumentResolution = instrumentResolution;
        this.strategies = strategies;
    }

    public ReconciliationResult reconcile(
            String email,
            UUID userId,
            UUID batchId,
            List<PortfolioImportStore.ImportRow> storedRows,
            PortfolioImportConfirmationService.ConfirmCommand command) {
        var overrides = new HashMap<Integer, PortfolioImportConfirmationService.RowOverride>();
        command.rowOverrides().forEach(override -> overrides.put(override.rowNumber(), override));
        var rows = storedRows.stream()
                .map(row -> effective(batchId, row, overrides.get(row.rowNumber())))
                .toList();
        if (rows.stream().anyMatch(row -> "ERROR".equals(row.status()))) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "All import errors must be corrected or ignored");
        }
        for (var row : rows) {
            if (!"HOLDING".equals(row.rowType()) || "IGNORED".equals(row.status())) continue;
            var override = overrides.get(row.rowNumber());
            if (override == null
                    || override.classification() == null
                    || override.classification().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY, "Every holding classification must be confirmed");
            }
            try {
                if (HoldingClassification.valueOf(override.classification()) == HoldingClassification.UNKNOWN) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY, "Every holding classification must be confirmed", exception);
            }
        }

        var mappings = new HashMap<String, PortfolioImportConfirmationService.AccountMapping>();
        command.accountMappings().forEach(mapping -> mappings.put(key(mapping.accountNumberMasked()), mapping));
        var accountIds = new LinkedHashMap<String, UUID>();
        for (var row : rows) {
            if ("IGNORED".equals(row.status())) continue;
            var accountKey = accountKey(row);
            accountIds.computeIfAbsent(
                    accountKey, ignored -> account(email, userId, row, mappings.get(key(row.accountNumberMasked()))));
        }

        var priorOpen = new HashSet<String>();
        accountIds.values().forEach(accountId -> priorOpen.addAll(openPositionKeys(accountId)));
        var incoming = new HashSet<String>();
        rows.stream()
                .filter(row -> "HOLDING".equals(row.rowType()) && !"IGNORED".equals(row.status()))
                .forEach(row -> incoming.add(accountIds.get(accountKey(row)) + "|" + row.symbol()));
        int closed =
                (int) priorOpen.stream().filter(key -> !incoming.contains(key)).count();
        accountIds.values().forEach(this::closeImportedPositions);
        accountIds.values().forEach(accountId -> resetImportedCash(userId, accountId));
        accountIds.values().forEach(this::cancelImportedCompensation);

        var positionIds = new HashSet<UUID>();
        var compensationKeys = new HashSet<String>();
        var cashByAccount = new LinkedHashMap<UUID, BigDecimal>();
        for (var row : rows) {
            if ("IGNORED".equals(row.status())) continue;
            var accountId = accountIds.get(accountKey(row));
            switch (row.rowType()) {
                case "HOLDING" ->
                    positionIds.add(upsertPosition(
                            email,
                            accountId,
                            batchId,
                            row,
                            overrides.get(row.rowNumber()).classification()));
                case "CASH" -> cashByAccount.merge(accountId, row.currentValue(), BigDecimal::add);
                case "UNVESTED_COMPENSATION" -> {
                    upsertCompensation(userId, accountId, batchId, row);
                    compensationKeys.add(accountId + "|" + row.symbol());
                }
                default ->
                    throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Unsupported import row type");
            }
        }
        cashByAccount.forEach((accountId, amount) -> upsertCash(userId, accountId, amount));
        applyCashSetup(userId, batchId, cashByAccount, command.cashSetup());
        return new ReconciliationResult(positionIds.size(), closed, cashByAccount.size(), compensationKeys.size());
    }

    private PortfolioImportStore.ImportRow effective(
            UUID batchId, PortfolioImportStore.ImportRow row, PortfolioImportConfirmationService.RowOverride override) {
        if (override == null) return row;
        if (override.ignored()) {
            updateRow(batchId, row.rowNumber(), row.symbol(), row.assetType(), row.rowType(), "IGNORED");
            return replace(row, row.symbol(), row.assetType(), row.rowType(), "IGNORED");
        }
        var symbol = normalize(override.symbol() == null ? row.symbol() : override.symbol());
        var assetType = normalize(override.assetType() == null ? row.assetType() : override.assetType());
        var rowType = normalize(override.rowType() == null ? row.rowType() : override.rowType());
        boolean valid =
                switch (rowType) {
                    case "HOLDING" ->
                        symbol != null
                                && row.quantity() != null
                                && row.currentValue() != null
                                && !"UNKNOWN".equals(assetType);
                    case "CASH" -> row.currentValue() != null;
                    case "UNVESTED_COMPENSATION" -> symbol != null;
                    default -> false;
                };
        var status = valid ? "VALID" : "ERROR";
        updateRow(batchId, row.rowNumber(), symbol, assetType, rowType, status);
        return replace(row, symbol, assetType, rowType, status);
    }

    private void updateRow(
            UUID batchId, int rowNumber, String symbol, String assetType, String rowType, String status) {
        jdbc.sql(
                        """
                        UPDATE portfolio_import_row
                        SET symbol=:symbol, asset_type=:assetType, row_type=:rowType, status=:status
                        WHERE batch_id=UUID_TO_BIN(:batchId) AND source_row_number=:rowNumber
                        """)
                .param("symbol", symbol)
                .param("assetType", assetType)
                .param("rowType", rowType)
                .param("status", status)
                .param("batchId", batchId.toString())
                .param("rowNumber", rowNumber)
                .update();
    }

    private static PortfolioImportStore.ImportRow replace(
            PortfolioImportStore.ImportRow row, String symbol, String assetType, String rowType, String status) {
        return new PortfolioImportStore.ImportRow(
                row.id(),
                row.rowNumber(),
                row.accountName(),
                row.accountNumberMasked(),
                symbol,
                row.description(),
                assetType,
                row.quantity(),
                row.lastPrice(),
                row.currentValue(),
                row.averageCost(),
                row.costBasis(),
                rowType,
                status,
                row.warnings(),
                row.rawJson());
    }

    private UUID account(
            String email,
            UUID userId,
            PortfolioImportStore.ImportRow row,
            PortfolioImportConfirmationService.AccountMapping mapping) {
        if (mapping != null && mapping.existingAccountId() != null) {
            return jdbc.sql(
                            """
                            SELECT BIN_TO_UUID(a.id) FROM investment_account a
                            JOIN app_user u ON u.id=a.user_id
                            WHERE a.id=UUID_TO_BIN(:id) AND u.email=:email
                            """)
                    .param("id", mapping.existingAccountId().toString())
                    .param("email", email)
                    .query(UUID.class)
                    .optional()
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "Account mapping is not owned by user"));
        }
        var externalKey = accountKey(row);
        var displayName = mapping != null
                        && mapping.displayName() != null
                        && !mapping.displayName().isBlank()
                ? mapping.displayName().trim()
                : row.accountName() == null ? "Fidelity account " + externalKey : row.accountName();
        var id = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO investment_account (
                            id, user_id, account_key, institution, account_type, display_name, currency,
                            active, created_at, updated_at, version, external_account_key, import_source
                        ) VALUES (
                            UUID_TO_BIN(:id), UUID_TO_BIN(:userId), :accountKey, 'Fidelity', 'BROKERAGE',
                            :displayName, 'USD', TRUE, :now, :now, 0, :externalKey, 'FIDELITY_CSV'
                        ) ON DUPLICATE KEY UPDATE display_name=VALUES(display_name), active=TRUE,
                            updated_at=VALUES(updated_at), version=version+1
                        """)
                .param("id", id.toString())
                .param("userId", userId.toString())
                .param("accountKey", "FIDELITY:" + externalKey)
                .param("displayName", displayName)
                .param("externalKey", externalKey)
                .param("now", clock.instant())
                .update();
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(id) FROM investment_account
                        WHERE user_id=UUID_TO_BIN(:userId) AND import_source='FIDELITY_CSV' AND external_account_key=:key
                        """)
                .param("userId", userId.toString())
                .param("key", externalKey)
                .query(UUID.class)
                .single();
    }

    private Set<String> openPositionKeys(UUID accountId) {
        var result = new HashSet<String>();
        jdbc.sql(
                        """
                        SELECT external_position_key FROM position
                        WHERE account_id=UUID_TO_BIN(:accountId) AND import_source='FIDELITY_CSV' AND status='OPEN'
                        """)
                .param("accountId", accountId.toString())
                .query(String.class)
                .list()
                .forEach(key -> result.add(accountId + "|" + key));
        return result;
    }

    private void closeImportedPositions(UUID accountId) {
        jdbc.sql(
                        """
                        UPDATE position SET status='CLOSED', closed_at=:now, updated_at=:now, version=version+1
                        WHERE account_id=UUID_TO_BIN(:accountId) AND import_source='FIDELITY_CSV' AND status='OPEN'
                        """)
                .param("now", clock.instant())
                .param("accountId", accountId.toString())
                .update();
    }

    private UUID upsertPosition(
            String email,
            UUID accountId,
            UUID batchId,
            PortfolioImportStore.ImportRow row,
            String classificationValue) {
        var resolution = instrumentResolution.resolve(email, row.symbol(), row.assetType());
        var classification = HoldingClassification.valueOf(classificationValue);
        var instrumentId = resolution.instrumentId();
        var id = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO position (
                            id, account_id, instrument_id, bucket, classification, classification_confirmed,
                            quantity, average_cost, market_value, status, opened_at, closed_at, created_at,
                            updated_at, version, external_position_key, import_source, data_as_of, data_readiness
                        ) VALUES (
                            UUID_TO_BIN(:id), UUID_TO_BIN(:accountId), UUID_TO_BIN(:instrumentId), :bucket, :classification, TRUE,
                            :quantity, :averageCost, :marketValue, 'OPEN', :now, NULL, :now, :now, 0,
                            :externalKey, 'FIDELITY_CSV', :dataAsOf, :dataReadiness
                        ) ON DUPLICATE KEY UPDATE instrument_id=VALUES(instrument_id), bucket=VALUES(bucket),
                            classification=VALUES(classification), classification_confirmed=TRUE, quantity=VALUES(quantity),
                            average_cost=VALUES(average_cost), market_value=VALUES(market_value), status='OPEN',
                            closed_at=NULL, data_as_of=VALUES(data_as_of), data_readiness=VALUES(data_readiness),
                            updated_at=VALUES(updated_at), version=version+1
                        """)
                .param("id", id.toString())
                .param("accountId", accountId.toString())
                .param("instrumentId", instrumentId.toString())
                .param("bucket", bucket(classification))
                .param("classification", classification.name())
                .param("quantity", row.quantity())
                .param("averageCost", row.averageCost())
                .param("marketValue", row.currentValue())
                .param("now", clock.instant())
                .param("externalKey", row.symbol())
                .param("dataAsOf", clock.instant())
                .param("dataReadiness", resolution.readiness().name())
                .update();
        var positionId = jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(id) FROM position
                        WHERE account_id=UUID_TO_BIN(:accountId) AND import_source='FIDELITY_CSV'
                          AND external_position_key=:externalKey
                        """)
                .param("accountId", accountId.toString())
                .param("externalKey", row.symbol())
                .query(UUID.class)
                .single();
        var evidence = sha256(batchId + "|" + row.rowNumber() + "|" + row.quantity() + "|" + row.currentValue());
        jdbc.sql(
                        """
                        INSERT IGNORE INTO position_snapshot (
                            id, position_id, import_batch_id, quantity, average_cost, market_value,
                            data_as_of, source, evidence_checksum, created_at
                        ) VALUES (
                            UUID_TO_BIN(:id), UUID_TO_BIN(:positionId), UUID_TO_BIN(:batchId), :quantity,
                            :averageCost, :marketValue, :dataAsOf, 'FIDELITY_CSV', :checksum, :now
                        )
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("positionId", positionId.toString())
                .param("batchId", batchId.toString())
                .param("quantity", row.quantity())
                .param("averageCost", row.averageCost())
                .param("marketValue", row.currentValue())
                .param("dataAsOf", clock.instant())
                .param("checksum", evidence)
                .param("now", clock.instant())
                .update();
        return positionId;
    }

    private void applyCashSetup(
            UUID userId,
            UUID batchId,
            LinkedHashMap<UUID, BigDecimal> importedCash,
            PortfolioImportConfirmationService.CashSetup setup) {
        var target = strategies.current().emergencyCashFloor();
        var fidelityAvailable = importedCash.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        var fidelityEmergency = setup.fidelityAmount();
        var externalEmergency = setup.externalAmount();
        try {
            setup.validateAgainst(fidelityAvailable, target);
        } catch (IllegalArgumentException exception) {
            throw invalidCashSetup(exception.getMessage());
        }
        var confirmed = setup.totalAmount();
        protectFidelityCash(userId, fidelityEmergency);
        jdbc.sql("DELETE FROM cash_bucket WHERE user_id=UUID_TO_BIN(:userId) AND bucket_type='EMERGENCY'")
                .param("userId", userId.toString())
                .update();
        jdbc.sql(
                        """
                        INSERT INTO cash_bucket (
                          id,user_id,account_id,bucket_type,target_amount,current_amount,currency,as_of,updated_at,version)
                        VALUES (UUID_TO_BIN(:id),UUID_TO_BIN(:userId),NULL,'EMERGENCY',:target,:amount,'USD',CURRENT_DATE,:now,0)
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("userId", userId.toString())
                .param("target", target)
                .param("amount", confirmed)
                .param("now", clock.instant())
                .update();
        jdbc.sql(
                        """
                        INSERT INTO portfolio_cash_setup (
                          id,user_id,import_batch_id,location_code,emergency_target,fidelity_emergency_amount,
                          external_emergency_amount,external_amount_source,confirmed_total,created_at)
                        VALUES (UUID_TO_BIN(:id),UUID_TO_BIN(:userId),UUID_TO_BIN(:batchId),:location,:target,
                                :fidelity,:external,:externalSource,:confirmed,:now)
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("userId", userId.toString())
                .param("batchId", batchId.toString())
                .param("location", setup.location().name())
                .param("target", target)
                .param("fidelity", fidelityEmergency)
                .param("external", externalEmergency)
                .param(
                        "externalSource",
                        externalEmergency.signum() > 0 ? "USER_CONFIRMED_EXTERNAL" : null,
                        java.sql.Types.VARCHAR)
                .param("confirmed", confirmed)
                .param("now", clock.instant())
                .update();
    }

    private static ResponseStatusException invalidCashSetup(String reason) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, reason);
    }

    private void protectFidelityCash(UUID userId, BigDecimal amount) {
        var remaining = amount;
        for (var bucket : jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(id) id,current_amount amount FROM cash_bucket
                        WHERE user_id=UUID_TO_BIN(:userId) AND bucket_type='ALLOCATED_TRADE'
                        ORDER BY current_amount DESC,id
                        """)
                .param("userId", userId.toString())
                .query(CashBucket.class)
                .list()) {
            if (remaining.signum() == 0) break;
            var protectedAmount = remaining.min(bucket.amount());
            jdbc.sql(
                            "UPDATE cash_bucket SET current_amount=current_amount-:amount,updated_at=:now,version=version+1 WHERE id=UUID_TO_BIN(:id)")
                    .param("amount", protectedAmount)
                    .param("now", clock.instant())
                    .param("id", bucket.id().toString())
                    .update();
            remaining = remaining.subtract(protectedAmount);
        }
    }

    private static String bucket(HoldingClassification classification) {
        return switch (classification) {
            case CORE_BROAD_ETF, CORE_TECH_ETF, CASH_EQUIVALENT -> "CORE";
            default -> "TACTICAL_OVERLAY";
        };
    }

    private void upsertCash(UUID userId, UUID accountId, BigDecimal amount) {
        jdbc.sql(
                        """
                        INSERT INTO cash_bucket (
                            id, user_id, account_id, bucket_type, target_amount, current_amount,
                            currency, as_of, updated_at, version
                        ) VALUES (
                            UUID_TO_BIN(:id), UUID_TO_BIN(:userId), UUID_TO_BIN(:accountId), 'ALLOCATED_TRADE',
                            0, :amount, 'USD', CURRENT_DATE, :now, 0
                        ) ON DUPLICATE KEY UPDATE current_amount=VALUES(current_amount), as_of=VALUES(as_of),
                            updated_at=VALUES(updated_at), version=version+1
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("userId", userId.toString())
                .param("accountId", accountId.toString())
                .param("amount", amount)
                .param("now", clock.instant())
                .update();
    }

    private void resetImportedCash(UUID userId, UUID accountId) {
        jdbc.sql(
                        """
                        UPDATE cash_bucket SET current_amount=0, as_of=CURRENT_DATE,
                            updated_at=:now, version=version+1
                        WHERE user_id=UUID_TO_BIN(:userId) AND account_id=UUID_TO_BIN(:accountId)
                          AND bucket_type='ALLOCATED_TRADE'
                        """)
                .param("userId", userId.toString())
                .param("accountId", accountId.toString())
                .param("now", clock.instant())
                .update();
    }

    private void cancelImportedCompensation(UUID accountId) {
        jdbc.sql(
                        """
                        UPDATE compensation_holding SET vesting_status='CANCELLED', updated_at=:now,
                            version=version+1
                        WHERE account_id=UUID_TO_BIN(:accountId) AND vesting_status='UNVESTED'
                        """)
                .param("accountId", accountId.toString())
                .param("now", clock.instant())
                .update();
    }

    private void upsertCompensation(UUID userId, UUID accountId, UUID batchId, PortfolioImportStore.ImportRow row) {
        jdbc.sql(
                        """
                        INSERT INTO compensation_holding (
                            id, user_id, account_id, external_holding_key, symbol, description,
                            classification, classification_confirmed, quantity, estimated_value,
                            vesting_status, data_as_of, import_batch_id, created_at, updated_at, version
                        ) VALUES (
                            UUID_TO_BIN(:id), UUID_TO_BIN(:userId), UUID_TO_BIN(:accountId), :key, :symbol,
                            :description, 'UNVESTED_COMPENSATION', TRUE, :quantity, :value, 'UNVESTED',
                            :dataAsOf, UUID_TO_BIN(:batchId), :now, :now, 0
                        ) ON DUPLICATE KEY UPDATE description=VALUES(description), quantity=VALUES(quantity),
                            estimated_value=VALUES(estimated_value), vesting_status='UNVESTED',
                            data_as_of=VALUES(data_as_of), import_batch_id=VALUES(import_batch_id),
                            updated_at=VALUES(updated_at), version=version+1
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("userId", userId.toString())
                .param("accountId", accountId.toString())
                .param("key", row.symbol())
                .param("symbol", row.symbol())
                .param("description", row.description())
                .param("quantity", row.quantity())
                .param("value", row.currentValue())
                .param("dataAsOf", clock.instant())
                .param("batchId", batchId.toString())
                .param("now", clock.instant())
                .update();
    }

    private void updateBatchCounts(UUID batchId) {
        jdbc.sql(
                        """
                        UPDATE portfolio_import_batch b SET
                            valid_row_count=(SELECT COUNT(*) FROM portfolio_import_row r
                                             WHERE r.batch_id=b.id AND r.status IN ('VALID','WARNING','IGNORED')),
                            error_row_count=(SELECT COUNT(*) FROM portfolio_import_row r
                                             WHERE r.batch_id=b.id AND r.status='ERROR')
                        WHERE b.id=UUID_TO_BIN(:batchId)
                        """)
                .param("batchId", batchId.toString())
                .update();
    }

    private static String accountKey(PortfolioImportStore.ImportRow row) {
        var number = row.accountNumberMasked() == null
                ? "UNSPECIFIED"
                : row.accountNumberMasked().trim();
        var name =
                row.accountName() == null ? "UNNAMED" : row.accountName().trim().toUpperCase(Locale.ROOT);
        return number + "|" + name;
    }

    private static String key(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record ReconciliationResult(
            int openPositionCount, int closedPositionCount, int cashRowCount, int compensationRowCount) {}

    record CashBucket(UUID id, BigDecimal amount) {}
}
