package com.example.portfolio.analysis.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.portfolio.MySqlIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class StrategyGovernanceIntegrationTest extends MySqlIntegrationTest {
    private static final String USER = "77777777-7777-7777-7777-777777777777";
    private static final String RUN = "88888888-8888-8888-8888-888888888888";
    private static final String STRATEGY = "14.0.0-test";
    private static final String CONFIG = "a".repeat(64);

    @Autowired StrategyGovernanceService governance;
    @Autowired JdbcClient jdbc;

    @BeforeEach
    void seed() {
        clean();
        jdbc.sql("""
                INSERT INTO app_user (id,email,password_hash,status,timezone,created_at,updated_at,version)
                VALUES (UUID_TO_BIN(:id),'t14@example.local','unused','ACTIVE','UTC',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),0)
                """).param("id", USER).update();
        jdbc.sql("""
                INSERT INTO strategy_version (id,version_code,status,config_json,config_hash,created_at)
                VALUES (UUID_TO_BIN(UUID()),:version,'DRAFT',JSON_OBJECT('phase','T14'),:config,UTC_TIMESTAMP(6))
                """).param("version", STRATEGY).param("config", CONFIG).update();
        jdbc.sql("""
                INSERT INTO backtest_run (id,user_id,idempotency_key,strategy_version,period_start,period_end,
                  training_through,out_of_sample_from,universe_checksum,config_checksum,status,bias_status,
                  summary_json,started_at,completed_at,created_at)
                VALUES (UUID_TO_BIN(:run),UUID_TO_BIN(:user),'t14-governance',:version,'2020-01-01','2025-12-31',
                  '2023-12-29','2024-01-02',:universe,:config,'SUCCEEDED','CLEAR',JSON_OBJECT('folds',4),
                  UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """).param("run", RUN).param("user", USER).param("version", STRATEGY)
                .param("universe", "b".repeat(64)).param("config", CONFIG).update();
    }

    @AfterEach
    void clean() {
        jdbc.sql("DELETE FROM backtest_metric WHERE backtest_run_id=UUID_TO_BIN(:run)").param("run", RUN).update();
        jdbc.sql("UPDATE strategy_version SET approved_backtest_run_id=NULL,backtest_artifact_checksum=NULL,approved_by=NULL,approved_at=NULL WHERE version_code=:version")
                .param("version", STRATEGY).update();
        jdbc.sql("DELETE FROM backtest_run WHERE id=UUID_TO_BIN(:run)").param("run", RUN).update();
        jdbc.sql("DELETE FROM strategy_version WHERE version_code=:version").param("version", STRATEGY).update();
        jdbc.sql("DELETE FROM app_user WHERE id=UUID_TO_BIN(:id)").param("id", USER).update();
    }

    @Test
    void publicationRequiresMatchingBiasClearOosArtifactAndHumanApproval() {
        assertThatThrownBy(() -> governance.publish(STRATEGY)).hasMessage("STRATEGY_RELEASE_NOT_APPROVED");
        governance.approve(STRATEGY, UUID.fromString(RUN), "c".repeat(64), "risk-owner@example.local");
        governance.publish(STRATEGY);
        var row = jdbc.sql("SELECT status,approved_by approvedBy,published_at publishedAt FROM strategy_version WHERE version_code=:version")
                .param("version", STRATEGY).query(ReleaseRow.class).single();
        assertThat(row.status()).isEqualTo("PUBLISHED");
        assertThat(row.approvedBy()).isEqualTo("risk-owner@example.local");
        assertThat(row.publishedAt()).isNotNull();
    }

    record ReleaseRow(String status, String approvedBy, java.time.LocalDateTime publishedAt) {}
}
