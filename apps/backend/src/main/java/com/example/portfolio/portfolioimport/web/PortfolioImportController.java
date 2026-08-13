package com.example.portfolio.portfolioimport.web;

import com.example.portfolio.analysis.application.PublishedStrategyService;
import com.example.portfolio.portfolioimport.application.ImportClassificationSuggester;
import com.example.portfolio.portfolioimport.application.PortfolioImportConfirmationService;
import com.example.portfolio.portfolioimport.application.PortfolioImportPreviewService;
import com.example.portfolio.portfolioimport.application.PortfolioImportQueryService;
import com.example.portfolio.portfolioimport.domain.ImportedCash;
import com.example.portfolio.portfolioimport.domain.ImportedHolding;
import com.example.portfolio.portfolioimport.domain.PortfolioImportPreview;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/portfolio-imports")
public class PortfolioImportController {
    private final PortfolioImportPreviewService previews;
    private final PortfolioImportQueryService queries;
    private final PortfolioImportConfirmationService confirmations;
    private final ImportClassificationSuggester classifications;
    private final PublishedStrategyService strategies;

    public PortfolioImportController(
            PortfolioImportPreviewService previews,
            PortfolioImportQueryService queries,
            PortfolioImportConfirmationService confirmations,
            ImportClassificationSuggester classifications,
            PublishedStrategyService strategies) {
        this.previews = previews;
        this.queries = queries;
        this.confirmations = confirmations;
        this.classifications = classifications;
        this.strategies = strategies;
    }

    @PostMapping(value = "/fidelity/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    PreviewResponse preview(@RequestPart("file") MultipartFile file, Principal principal) {
        try {
            return PreviewResponse.from(
                    previews.previewFidelity(principal.getName(), file.getBytes(), file.getOriginalFilename()),
                    classifications,
                    strategies.current().emergencyCashFloor());
        } catch (IOException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/pasted/preview")
    PreviewResponse previewPasted(@Valid @RequestBody PastedTableRequest request, Principal principal) {
        try {
            return PreviewResponse.from(
                    previews.previewPastedTable(principal.getName(), request.table()),
                    classifications,
                    strategies.current().emergencyCashFloor());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/manual/preview")
    PreviewResponse previewManual(@Valid @RequestBody ManualHoldingRequest request, Principal principal) {
        try {
            return PreviewResponse.from(
                    previews.previewManual(principal.getName(), request.toHolding()),
                    classifications,
                    strategies.current().emergencyCashFloor());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/{batchId}")
    PreviewResponse find(@PathVariable UUID batchId, Principal principal) {
        return PreviewResponse.from(
                queries.find(principal.getName(), batchId),
                classifications,
                strategies.current().emergencyCashFloor());
    }

    @PostMapping("/{batchId}/confirm")
    ConfirmationResponse confirm(
            @PathVariable UUID batchId, @Valid @RequestBody ConfirmationRequest request, Principal principal) {
        return ConfirmationResponse.from(confirmations.confirm(principal.getName(), batchId, request.toCommand()));
    }

    public record ConfirmationRequest(
            long expectedVersion,
            List<AccountMappingRequest> accountMappings,
            List<RowOverrideRequest> rowOverrides,
            @NotNull CashSetupRequest cashSetup) {
        PortfolioImportConfirmationService.ConfirmCommand toCommand() {
            return new PortfolioImportConfirmationService.ConfirmCommand(
                    expectedVersion,
                    accountMappings == null
                            ? List.of()
                            : accountMappings.stream()
                                    .map(AccountMappingRequest::toCommand)
                                    .toList(),
                    rowOverrides == null
                            ? List.of()
                            : rowOverrides.stream()
                                    .map(RowOverrideRequest::toCommand)
                                    .toList(),
                    cashSetup.toCommand());
        }
    }

    public record PastedTableRequest(@NotNull String table) {}

    public record ManualHoldingRequest(
            String accountNumber,
            @NotNull String accountName,
            @NotNull String symbol,
            String description,
            @NotNull String quantity,
            String lastPrice,
            @NotNull String currentValue,
            String averageCost,
            String costBasis,
            @NotNull String assetType) {
        PortfolioImportPreviewService.ManualHolding toHolding() {
            return new PortfolioImportPreviewService.ManualHolding(
                    accountNumber,
                    accountName,
                    symbol,
                    description,
                    quantity,
                    lastPrice,
                    currentValue,
                    averageCost,
                    costBasis,
                    assetType);
        }
    }

    public record AccountMappingRequest(String accountNumberMasked, UUID existingAccountId, String displayName) {
        PortfolioImportConfirmationService.AccountMapping toCommand() {
            return new PortfolioImportConfirmationService.AccountMapping(
                    accountNumberMasked, existingAccountId, displayName);
        }
    }

    public record RowOverrideRequest(
            int rowNumber, String symbol, String assetType, String rowType, String classification, boolean ignored) {
        PortfolioImportConfirmationService.RowOverride toCommand() {
            return new PortfolioImportConfirmationService.RowOverride(
                    rowNumber, symbol, assetType, rowType, classification, ignored);
        }
    }

    public record CashSetupRequest(@NotNull String location, String externalEmergencyAmount) {
        PortfolioImportConfirmationService.CashSetup toCommand() {
            try {
                return new PortfolioImportConfirmationService.CashSetup(
                        PortfolioImportConfirmationService.CashLocation.valueOf(location),
                        externalEmergencyAmount == null || externalEmergencyAmount.isBlank()
                                ? BigDecimal.ZERO
                                : new BigDecimal(externalEmergencyAmount));
            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid safety-cash setup", exception);
            }
        }
    }

    public record PreviewResponse(
            UUID batchId,
            String status,
            long version,
            List<PortfolioImportPreview.ImportAccount> accounts,
            List<HoldingResponse> holdings,
            List<CashResponse> cash,
            List<String> warnings,
            List<String> errors,
            SummaryResponse summary,
            java.time.Instant dataAsOf) {
        static PreviewResponse from(
                PortfolioImportPreview value,
                ImportClassificationSuggester classifications,
                BigDecimal emergencyCashTarget) {
            return new PreviewResponse(
                    value.batchId(),
                    value.status().name(),
                    value.version(),
                    value.accounts(),
                    value.holdings().stream()
                            .map(row -> HoldingResponse.from(row, classifications))
                            .toList(),
                    value.cash().stream().map(CashResponse::from).toList(),
                    value.warnings(),
                    value.errors(),
                    SummaryResponse.from(value.summary(), emergencyCashTarget),
                    value.dataAsOf());
        }
    }

    public record HoldingResponse(
            int rowNumber,
            String accountName,
            String accountNumberMasked,
            String symbol,
            String description,
            String assetType,
            String quantity,
            String lastPrice,
            String currentValue,
            String averageCost,
            String costBasis,
            String rowType,
            String status,
            List<String> warnings,
            String suggestedClassification,
            String classificationReason) {
        static HoldingResponse from(ImportedHolding value, ImportClassificationSuggester classifications) {
            var suggestion =
                    classifications.suggest(value.symbol(), value.assetType(), value.description(), value.rowType());
            return new HoldingResponse(
                    value.rowNumber(),
                    value.accountName(),
                    value.accountNumberMasked(),
                    value.symbol(),
                    value.description(),
                    value.assetType(),
                    decimal(value.quantity()),
                    decimal(value.lastPrice()),
                    decimal(value.currentValue()),
                    decimal(value.averageCost()),
                    decimal(value.costBasis()),
                    value.rowType(),
                    value.status().name(),
                    value.warnings(),
                    suggestion.classification(),
                    suggestion.reason());
        }
    }

    public record CashResponse(
            int rowNumber,
            String accountName,
            String accountNumberMasked,
            String symbol,
            String description,
            String currentValue,
            String status,
            List<String> warnings) {
        static CashResponse from(ImportedCash value) {
            return new CashResponse(
                    value.rowNumber(),
                    value.accountName(),
                    value.accountNumberMasked(),
                    value.symbol(),
                    value.description(),
                    decimal(value.currentValue()),
                    value.status().name(),
                    value.warnings());
        }
    }

    public record SummaryResponse(
            int rowCount,
            int validRowCount,
            int errorRowCount,
            @NotNull String estimatedInvestedValue,
            @NotNull String estimatedCashValue,
            @NotNull String emergencyCashTarget) {
        static SummaryResponse from(PortfolioImportPreview.Summary value, BigDecimal emergencyCashTarget) {
            return new SummaryResponse(
                    value.rowCount(),
                    value.validRowCount(),
                    value.errorRowCount(),
                    decimal(value.estimatedInvestedValue()),
                    decimal(value.estimatedCashValue()),
                    decimal(emergencyCashTarget));
        }
    }

    public record ConfirmationResponse(
            UUID batchId,
            String status,
            long version,
            UUID analysisRunId,
            String analysisState,
            int openPositionCount,
            int closedPositionCount,
            int cashRowCount,
            int compensationRowCount,
            boolean idempotentReplay) {
        static ConfirmationResponse from(PortfolioImportConfirmationService.ConfirmationResult value) {
            return new ConfirmationResponse(
                    value.batchId(),
                    value.status(),
                    value.version(),
                    value.analysisRunId(),
                    value.analysisState(),
                    value.openPositionCount(),
                    value.closedPositionCount(),
                    value.cashRowCount(),
                    value.compensationRowCount(),
                    value.idempotentReplay());
        }
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }
}
