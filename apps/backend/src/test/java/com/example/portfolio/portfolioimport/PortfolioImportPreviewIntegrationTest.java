package com.example.portfolio.portfolioimport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class PortfolioImportPreviewIntegrationTest extends PortfolioImportIntegrationSupport {
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
        assertThat(count("SELECT COUNT(*) FROM portfolio_import_batch WHERE parser_revision='fidelity-v2'"))
                .isEqualTo(1);
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
    void parserRevisionChangeReparsesTheSameSourceFile() throws Exception {
        var legacy = preview("fidelity-positions.csv");
        update("UPDATE portfolio_import_batch SET parser_revision='legacy-v1' WHERE id=UUID_TO_BIN('"
                + uuid(legacy, "batchId") + "')");

        var reparsed = preview("fidelity-positions.csv");

        assertThat(uuid(reparsed, "batchId")).isNotEqualTo(uuid(legacy, "batchId"));
        assertThat(count("SELECT COUNT(*) FROM portfolio_import_batch")).isEqualTo(2);
        assertThat(count("SELECT COUNT(*) FROM portfolio_import_batch WHERE parser_revision='fidelity-v2'"))
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
