package com.example.portfolio.portfolioimport.application;

import com.example.portfolio.portfolioimport.domain.ImportSource;
import com.example.portfolio.portfolioimport.domain.PortfolioImportPreview;
import com.example.portfolio.portfolioimport.infrastructure.FidelityCsvParser;
import com.example.portfolio.portfolioimport.infrastructure.PastedTableParser;
import com.example.portfolio.portfolioimport.infrastructure.PortfolioImportStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;

@Service
public class PortfolioImportPreviewService {
    private static final String FIDELITY_PARSER_REVISION = "fidelity-v2";
    private static final String PASTED_TABLE_PARSER_REVISION = "pasted-v1";
    private static final String MANUAL_PARSER_REVISION = "manual-v1";
    private final FidelityCsvParser parser;
    private final PastedTableParser pastedTables;
    private final PortfolioImportStore store;

    public PortfolioImportPreviewService(
            FidelityCsvParser parser, PastedTableParser pastedTables, PortfolioImportStore store) {
        this.parser = parser;
        this.pastedTables = pastedTables;
        this.store = store;
    }

    public PortfolioImportPreview previewFidelity(String email, byte[] content, String filename) {
        var parsed = parser.parse(content, filename);
        return store.savePreview(
                email,
                ImportSource.FIDELITY_CSV,
                safeFilename(filename),
                sha256(content),
                FIDELITY_PARSER_REVISION,
                parsed);
    }

    public PortfolioImportPreview previewPastedTable(String email, String table) {
        var content = table == null ? new byte[0] : table.getBytes(StandardCharsets.UTF_8);
        var parsed = pastedTables.parse(table);
        return store.savePreview(
                email, ImportSource.PASTED_TABLE, null, sha256(content), PASTED_TABLE_PARSER_REVISION, parsed);
    }

    public PortfolioImportPreview previewManual(String email, ManualHolding holding) {
        if (holding == null) throw new IllegalArgumentException("The manual holding is required");
        var table = String.join(
                        "\t",
                        "Account Number",
                        "Account Name",
                        "Symbol",
                        "Description",
                        "Quantity",
                        "Last Price",
                        "Current Value",
                        "Average Cost Basis",
                        "Cost Basis Total",
                        "Type")
                + "\n"
                + String.join(
                        "\t",
                        clean(holding.accountNumber()),
                        clean(holding.accountName()),
                        clean(holding.symbol()),
                        clean(holding.description()),
                        clean(holding.quantity()),
                        clean(holding.lastPrice()),
                        clean(holding.currentValue()),
                        clean(holding.averageCost()),
                        clean(holding.costBasis()),
                        clean(holding.assetType()));
        var parsed = pastedTables.parse(table);
        return store.savePreview(
                email,
                ImportSource.MANUAL,
                null,
                sha256(table.getBytes(StandardCharsets.UTF_8)),
                MANUAL_PARSER_REVISION,
                parsed);
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.replace("\t", " ").replace("\r", " ").replace("\n", " ").trim();
    }

    private static String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "fidelity-positions.csv";
        var normalized = filename.replace('\\', '/');
        var base = normalized.substring(normalized.lastIndexOf('/') + 1);
        return base.length() > 255 ? base.substring(base.length() - 255) : base;
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record ManualHolding(
            String accountNumber,
            String accountName,
            String symbol,
            String description,
            String quantity,
            String lastPrice,
            String currentValue,
            String averageCost,
            String costBasis,
            String assetType) {}
}
