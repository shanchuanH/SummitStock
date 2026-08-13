package com.example.portfolio.portfolioimport;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.example.portfolio.MySqlIntegrationTest;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
abstract class PortfolioImportIntegrationSupport extends MySqlIntegrationTest {
    static final String EMAIL = "admin@example.local";
    static final String PASSWORD = "change-before-use";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected ObjectMapper json;

    @BeforeEach
    @AfterEach
    void removeImportedPortfolio() {
        update("DELETE ja FROM job_attempt ja JOIN job_run j ON j.id=ja.job_run_id "
                + "JOIN portfolio_analysis_run r ON r.id=j.analysis_run_id JOIN app_user u ON u.id=r.user_id "
                + "WHERE u.email='" + EMAIL + "'");
        update("DELETE j FROM job_run j JOIN portfolio_analysis_run r ON r.id=j.analysis_run_id "
                + "JOIN app_user u ON u.id=r.user_id WHERE u.email='" + EMAIL + "'");
        update("DELETE ja FROM job_attempt ja JOIN job_run j ON j.id=ja.job_run_id "
                + "WHERE j.job_type='PORTFOLIO_ANALYSIS' AND JSON_UNQUOTE(JSON_EXTRACT(j.payload,'$.userEmail'))='"
                + EMAIL + "'");
        update("DELETE FROM job_run WHERE job_type='PORTFOLIO_ANALYSIS' "
                + "AND JSON_UNQUOTE(JSON_EXTRACT(payload,'$.userEmail'))='" + EMAIL + "'");
        update("DELETE s FROM portfolio_analysis_step s JOIN portfolio_analysis_run r ON r.id=s.run_id "
                + "JOIN app_user u ON u.id=r.user_id WHERE u.email='" + EMAIL + "'");
        update("DELETE r FROM portfolio_analysis_run r JOIN app_user u ON u.id=r.user_id WHERE u.email='" + EMAIL
                + "'");
        update("DELETE ra FROM recommendation_acknowledgement ra JOIN app_user u ON u.id=ra.user_id "
                + "WHERE u.email='" + EMAIL + "'");
        update("DELETE r FROM recommendation r JOIN app_user u ON u.id=r.user_id WHERE u.email='" + EMAIL + "'");
        update("DELETE t FROM etf_dip_tranche t JOIN app_user u ON u.id=t.user_id WHERE u.email='" + EMAIL + "'");
        update("DELETE e FROM etf_dip_event e JOIN app_user u ON u.id=e.user_id WHERE u.email='" + EMAIL + "'");
        update("DELETE s FROM risk_cluster_snapshot s JOIN app_user u ON u.id=s.user_id WHERE u.email='" + EMAIL + "'");
        update("DELETE s FROM position_risk_snapshot s JOIN position p ON p.id=s.position_id "
                + "JOIN investment_account a ON a.id=p.account_id JOIN app_user u ON u.id=a.user_id WHERE u.email='"
                + EMAIL + "'");
        update("DELETE m FROM risk_cluster_membership m JOIN risk_cluster c ON c.id=m.risk_cluster_id "
                + "JOIN app_user u ON u.id=c.user_id WHERE u.email='" + EMAIL + "'");
        update("DELETE c FROM risk_cluster c JOIN app_user u ON u.id=c.user_id WHERE u.email='" + EMAIL + "'");
        update("DELETE h FROM holding_analysis_snapshot h JOIN position p ON p.id=h.position_id "
                + "JOIN investment_account a ON a.id=p.account_id JOIN app_user u ON u.id=a.user_id "
                + "WHERE u.email='" + EMAIL + "'");
        update("DELETE e FROM earnings_risk_snapshot e JOIN position p ON p.id=e.position_id "
                + "JOIN investment_account a ON a.id=p.account_id JOIN app_user u ON u.id=a.user_id "
                + "WHERE u.email='" + EMAIL + "'");
        update("DELETE s FROM stop_snapshot s JOIN position p ON p.id=s.position_id "
                + "JOIN investment_account a ON a.id=p.account_id JOIN app_user u ON u.id=a.user_id "
                + "WHERE u.email='" + EMAIL + "'");
        update("DELETE d FROM portfolio_drawdown_snapshot d JOIN app_user u ON u.id=d.user_id WHERE u.email='" + EMAIL
                + "'");
        update("DELETE s FROM portfolio_allocation_snapshot s JOIN app_user u ON u.id=s.user_id WHERE u.email='" + EMAIL
                + "'");
        update("DELETE c FROM compensation_holding c JOIN app_user u ON u.id=c.user_id WHERE u.email='" + EMAIL + "'");
        update("DELETE s FROM position_snapshot s JOIN position p ON p.id=s.position_id "
                + "JOIN investment_account a ON a.id=p.account_id JOIN app_user u ON u.id=a.user_id "
                + "WHERE u.email='" + EMAIL + "' AND p.import_source='FIDELITY_CSV'");
        update("DELETE m FROM position_mark_snapshot m JOIN position p ON p.id=m.position_id "
                + "JOIN investment_account a ON a.id=p.account_id JOIN app_user u ON u.id=a.user_id "
                + "WHERE u.email='" + EMAIL + "' AND p.import_source='FIDELITY_CSV'");
        update("DELETE p FROM position p JOIN investment_account a ON a.id=p.account_id "
                + "JOIN app_user u ON u.id=a.user_id WHERE u.email='" + EMAIL
                + "' AND p.import_source='FIDELITY_CSV'");
        update("DELETE c FROM cash_bucket c JOIN investment_account a ON a.id=c.account_id "
                + "JOIN app_user u ON u.id=a.user_id WHERE u.email='" + EMAIL
                + "' AND a.import_source='FIDELITY_CSV'");
        update("DELETE s FROM portfolio_cash_setup s JOIN app_user u ON u.id=s.user_id WHERE u.email='" + EMAIL + "'");
        update("DELETE c FROM cash_bucket c JOIN app_user u ON u.id=c.user_id WHERE u.email='" + EMAIL
                + "' AND c.bucket_type='EMERGENCY'");
        update("DELETE a FROM audit_log a JOIN app_user u ON u.id=a.user_id WHERE u.email='" + EMAIL
                + "' AND a.event_type IN ('PORTFOLIO_IMPORT_CONFIRMED','NAV_RECONCILIATION_REQUIRED')");
        update("DELETE e FROM portfolio_external_cashflow_event e JOIN app_user u ON u.id=e.user_id WHERE u.email='"
                + EMAIL + "'");
        update("DELETE a FROM investment_account a JOIN app_user u ON u.id=a.user_id WHERE u.email='" + EMAIL
                + "' AND a.import_source='FIDELITY_CSV'");
        update("DELETE b FROM portfolio_import_batch b JOIN app_user u ON u.id=b.user_id WHERE u.email='" + EMAIL
                + "'");
        update("DELETE FROM instrument WHERE exchange='FIDELITY' AND NOT EXISTS "
                + "(SELECT 1 FROM position p WHERE p.instrument_id=instrument.id)");
    }

    protected JsonNode preview(String fixture) throws Exception {
        var file = new MockMultipartFile("file", fixture, "text/csv", fixture(fixture));
        var result = mockMvc.perform(multipart("/api/v1/portfolio-imports/fidelity/preview")
                        .file(file)
                        .with(httpBasic(EMAIL, PASSWORD))
                        .with(csrf()))
                .andReturn();
        assertSuccessful(result);
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected JsonNode confirm(UUID batchId, long version, String rowOverrides) throws Exception {
        return confirm(batchId, version, rowOverrides, "IN_FIDELITY", "14000");
    }

    protected JsonNode confirm(
            UUID batchId, long version, String rowOverrides, String cashLocation, String confirmedAmount)
            throws Exception {
        var current = mockMvc.perform(
                        get("/api/v1/portfolio-imports/{batchId}", batchId).with(httpBasic(EMAIL, PASSWORD)))
                .andReturn();
        assertSuccessful(current);
        var preview = json.readTree(current.getResponse().getContentAsString());
        var additions = new java.util.ArrayList<String>();
        for (var holding : preview.path("holdings")) {
            var rowNumber = holding.path("rowNumber").asInt();
            if (!"HOLDING".equals(holding.path("rowType").asString())
                    || rowOverrides.contains("\"rowNumber\":" + rowNumber)) continue;
            var classification = holding.path("suggestedClassification").asString();
            if ("UNKNOWN".equals(classification)) classification = "QUALITY_STOCK";
            additions.add("{\"rowNumber\":" + rowNumber + ",\"classification\":\"" + classification
                    + "\",\"ignored\":false}");
        }
        var supplied = rowOverrides.substring(1, rowOverrides.length() - 1).trim();
        var merged = new java.util.ArrayList<String>();
        if (!supplied.isBlank()) merged.add(supplied);
        merged.addAll(additions);
        var body = "{\"expectedVersion\":" + version + ",\"accountMappings\":[],\"rowOverrides\":["
                + String.join(",", merged)
                + "],\"cashSetup\":{\"location\":\"" + cashLocation + "\",\"amount\":\""
                + confirmedAmount + "\"}}";
        var result = mockMvc.perform(post("/api/v1/portfolio-imports/{batchId}/confirm", batchId)
                        .with(httpBasic(EMAIL, PASSWORD))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        assertSuccessful(result);
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    protected void update(String sql) {
        jdbc.sql(sql).update();
    }

    protected static byte[] fixture(String name) throws IOException {
        try (var input = PortfolioImportIntegrationSupport.class.getResourceAsStream("/portfolio-import/" + name)) {
            if (input == null) throw new IOException("Missing test fixture " + name);
            return input.readAllBytes();
        }
    }

    protected static UUID uuid(JsonNode node, String field) {
        return UUID.fromString(node.get(field).asString());
    }

    private static void assertSuccessful(MvcResult result) throws Exception {
        int status = result.getResponse().getStatus();
        if (status < 200 || status >= 300) {
            throw new AssertionError(
                    "HTTP " + status + ": " + result.getResponse().getContentAsString());
        }
    }
}
