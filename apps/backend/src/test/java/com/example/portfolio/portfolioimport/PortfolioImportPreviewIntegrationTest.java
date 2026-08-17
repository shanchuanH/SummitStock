package com.example.portfolio.portfolioimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class PortfolioImportPreviewIntegrationTest extends PortfolioImportIntegrationSupport {
    @Test
    void parserUpgradeDoesNotReuseAStaleLegacyPreviewForTheSameFile() throws Exception {
        var proposedUserId = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT IGNORE INTO app_user (
                            id, email, password_hash, status, timezone, created_at, updated_at, version
                        ) VALUES (UUID_TO_BIN(:id), :email, '{external-session}', 'ACTIVE', 'UTC', :now, :now, 0)
                        """)
                .param("id", proposedUserId.toString())
                .param("email", EMAIL)
                .param("now", Instant.now())
                .update();
        var userId = jdbc.sql("SELECT BIN_TO_UUID(id) FROM app_user WHERE email=:email")
                .param("email", EMAIL)
                .query(UUID.class)
                .single();
        var legacyBatchId = UUID.randomUUID();
        var source = fixture("fidelity-positions.csv");
        var legacyChecksum =
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source));
        jdbc.sql(
                        """
                        INSERT INTO portfolio_import_batch (
                            id, user_id, source, filename, status, source_checksum,
                            row_count, valid_row_count, error_row_count, data_as_of,
                            created_at, updated_at, version
                        ) VALUES (
                            UUID_TO_BIN(:id), UUID_TO_BIN(:userId), 'FIDELITY_CSV', :filename, 'PREVIEW',
                            :checksum, 0, 0, 0, :now, :now, :now, 0
                        )
                        """)
                .param("id", legacyBatchId.toString())
                .param("userId", userId.toString())
                .param("filename", "fidelity-positions.csv")
                .param("checksum", legacyChecksum)
                .param("now", Instant.now())
                .update();

        var refreshed = preview("fidelity-positions.csv");

        assertThat(uuid(refreshed, "batchId")).isNotEqualTo(legacyBatchId);
        assertThat(refreshed.path("summary").path("rowCount").asInt()).isEqualTo(6);
        assertThat(count("SELECT COUNT(*) FROM portfolio_import_batch")).isEqualTo(2);
    }

    @Test
    void previewIsPersistedIdempotentlyWithoutMutatingThePortfolio() throws Exception {
        var first = preview("fidelity-positions.csv");
        var repeated = preview("fidelity-positions.csv");

        assertThat(first.get("status").asString()).isEqualTo("PREVIEW");
        assertThat(first.get("version").asLong()).isZero();
        assertThat(first.get("summary").get("rowCount").asInt()).isEqualTo(6);
        assertThat(first.get("summary").get("validRowCount").asInt()).isEqualTo(5);
        assertThat(first.get("summary").get("errorRowCount").asInt()).isEqualTo(1);
        assertThat(first.get("summary").get("estimatedInvestedValue").asString())
                .isEqualTo("6425.05");
        assertThat(first.get("summary").get("estimatedCashValue").asString()).isEqualTo("14000");
        assertThat(first.get("summary").get("emergencyCashTarget").asString()).isEqualTo("20000");
        assertThat(uuid(repeated, "batchId")).isEqualTo(uuid(first, "batchId"));
        assertThat(count("SELECT COUNT(*) FROM portfolio_import_batch")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM portfolio_import_row")).isEqualTo(6);
        assertThat(
                        count(
                                "SELECT COUNT(*) FROM portfolio_import_row WHERE CAST(raw_json AS CHAR) LIKE '%Z12345678%' OR CAST(raw_json AS CHAR) LIKE '%R87654321%'"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM position WHERE import_source='FIDELITY_CSV'"))
                .isZero();
        assertThat(first.path("holdings").get(0).path("suggestedClassification").asString())
                .isEqualTo("CORE_BROAD_ETF");
        assertThat(count("SELECT COUNT(*) FROM app_user WHERE email='" + EMAIL + "'"))
                .isEqualTo(1);
    }

    @Test
    void pastedAndManualInputsUseTheSamePreviewOnlyBoundary() throws Exception {
        var pasted = "Account Number\tAccount Name\tSymbol\tDescription\tQuantity\tLast Price\tCurrent Value\tType\n"
                + "Z12345678\tPrimary\tSPY\tSPDR ETF\t1.25\t500\t625\tETF";
        mockMvc.perform(post("/api/v1/portfolio-imports/pasted/preview")
                        .with(httpBasic(EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("table", pasted))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREVIEW"))
                .andExpect(jsonPath("$.holdings[0].quantity").value("1.25"));
        mockMvc.perform(
                        post("/api/v1/portfolio-imports/manual/preview")
                                .with(httpBasic(EMAIL, PASSWORD))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {"accountNumber":"***5678","accountName":"Primary","symbol":"AMZN",
                                 "description":"Amazon common stock","quantity":"0.5","lastPrice":"200",
                                 "currentValue":"100","averageCost":"150","costBasis":"75","assetType":"EQUITY"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PREVIEW"))
                .andExpect(jsonPath("$.holdings[0].quantity").value("0.5"));
        assertThat(count("SELECT COUNT(*) FROM position WHERE import_source='FIDELITY_CSV'"))
                .isZero();
        assertThat(count("SELECT COUNT(*) FROM portfolio_import_batch WHERE source IN ('PASTED_TABLE','MANUAL')"))
                .isEqualTo(2);
    }
}
