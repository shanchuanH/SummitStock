package com.example.portfolio.analysis.capital;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.MySqlIntegrationTest;
import com.example.portfolio.analysis.application.HoldingEvidenceAssembler;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
class CapitalBaseServiceTest extends MySqlIntegrationTest {
    private static final UUID USER = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID POSITION = UUID.fromString("a4000000-0000-0000-0000-000000000001");

    @Autowired
    JdbcClient jdbc;

    @Autowired
    CapitalBaseService capitalBases;

    @Autowired
    HoldingEvidenceAssembler evidenceAssembler;

    @BeforeEach
    void seed() {
        cleanup();
        update(
                """
                INSERT INTO app_user (id,email,password_hash,status,timezone,created_at,updated_at)
                VALUES (UUID_TO_BIN('a1000000-0000-0000-0000-000000000001'),'capital@example.local','x','ACTIVE','UTC',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
        update(
                """
                INSERT INTO investment_account (id,user_id,account_key,institution,account_type,display_name,currency,active,created_at,updated_at)
                VALUES (UUID_TO_BIN('a2000000-0000-0000-0000-000000000001'),UUID_TO_BIN('a1000000-0000-0000-0000-000000000001'),'capital-test','Fidelity','BROKERAGE','Capital test','USD',TRUE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
        update(
                """
                INSERT INTO instrument (id,symbol,exchange,asset_type,currency,active,metadata,created_at,updated_at) VALUES
                (UUID_TO_BIN('a3000000-0000-0000-0000-000000000001'),'CAP1','TEST','EQUITY','USD',TRUE,JSON_OBJECT(),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
                (UUID_TO_BIN('a3000000-0000-0000-0000-000000000002'),'CAP2','TEST','EQUITY','USD',TRUE,JSON_OBJECT(),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
                (UUID_TO_BIN('a3000000-0000-0000-0000-000000000003'),'CAP3','TEST','EQUITY','USD',TRUE,JSON_OBJECT(),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
        update(
                """
                INSERT INTO position (id,account_id,instrument_id,bucket,classification,classification_confirmed,classification_source,quantity,average_cost,market_value,status,opened_at,created_at,updated_at,data_readiness) VALUES
                (UUID_TO_BIN('a4000000-0000-0000-0000-000000000001'),UUID_TO_BIN('a2000000-0000-0000-0000-000000000001'),UUID_TO_BIN('a3000000-0000-0000-0000-000000000001'),'CORE','QUALITY_STOCK',TRUE,'USER_CONFIRMED',80,100,8000,'OPEN',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'RESOLVED'),
                (UUID_TO_BIN('a4000000-0000-0000-0000-000000000002'),UUID_TO_BIN('a2000000-0000-0000-0000-000000000001'),UUID_TO_BIN('a3000000-0000-0000-0000-000000000002'),'CORE','QUALITY_STOCK',TRUE,'USER_CONFIRMED',120,100,12000,'OPEN',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'RESOLVED'),
                (UUID_TO_BIN('a4000000-0000-0000-0000-000000000003'),UUID_TO_BIN('a2000000-0000-0000-0000-000000000001'),UUID_TO_BIN('a3000000-0000-0000-0000-000000000003'),'CORE','CORE_BROAD_ETF',TRUE,'USER_CONFIRMED',600,100,60000,'OPEN',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'RESOLVED')
                """);
        update(
                """
                INSERT INTO cash_bucket (id,user_id,bucket_type,target_amount,current_amount,currency,as_of,updated_at)
                VALUES (UUID_TO_BIN('a5000000-0000-0000-0000-000000000001'),UUID_TO_BIN('a1000000-0000-0000-0000-000000000001'),'EMERGENCY',20000,20000,'USD',CURRENT_DATE,UTC_TIMESTAMP(6))
                """);
    }

    @AfterEach
    void cleanup() {
        update(
                "DELETE FROM portfolio_capital_snapshot WHERE user_id=UUID_TO_BIN('a1000000-0000-0000-0000-000000000001')");
        update(
                "DELETE FROM risk_cluster_membership WHERE position_id IN (UUID_TO_BIN('a4000000-0000-0000-0000-000000000001'),UUID_TO_BIN('a4000000-0000-0000-0000-000000000002'))");
        update("DELETE FROM risk_cluster WHERE user_id=UUID_TO_BIN('a1000000-0000-0000-0000-000000000001')");
        update("DELETE FROM position WHERE account_id=UUID_TO_BIN('a2000000-0000-0000-0000-000000000001')");
        update("DELETE FROM cash_bucket WHERE user_id=UUID_TO_BIN('a1000000-0000-0000-0000-000000000001')");
        update(
                "DELETE FROM instrument WHERE id IN (UUID_TO_BIN('a3000000-0000-0000-0000-000000000001'),UUID_TO_BIN('a3000000-0000-0000-0000-000000000002'),UUID_TO_BIN('a3000000-0000-0000-0000-000000000003'))");
        update("DELETE FROM investment_account WHERE id=UUID_TO_BIN('a2000000-0000-0000-0000-000000000001')");
        update("DELETE FROM app_user WHERE id=UUID_TO_BIN('a1000000-0000-0000-0000-000000000001')");
    }

    @Test
    void emergencyCashIsExcludedFromInvestableAssetsAndSnapshotIsVersioned() {
        var capital = capitalBases.calculate(USER);

        assertThat(capital.investedTradableAssets()).isEqualByComparingTo("80000");
        assertThat(capital.trackedCash()).isEqualByComparingTo("20000");
        assertThat(capital.emergencyReserve()).isEqualByComparingTo("20000");
        assertThat(capital.deployableCash()).isEqualByComparingTo("0");
        assertThat(capital.investableAssets()).isEqualByComparingTo("80000");
        assertThat(capital.totalLiquidAssets()).isEqualByComparingTo("100000");
        assertThat(capitalBases.capture(USER, Instant.parse("2026-08-09T20:00:00Z")))
                .isTrue();
        assertThat(capitalBases.capture(USER, Instant.parse("2026-08-09T20:00:00Z")))
                .isFalse();
        assertThat(jdbc.sql("SELECT strategy_version FROM portfolio_capital_snapshot WHERE user_id=UUID_TO_BIN(:id)")
                        .param("id", USER.toString())
                        .query(String.class)
                        .single())
                .isEqualTo("2.0.0-draft");
    }

    @Test
    void positionWeightUsesInvestableAssets() {
        assertThat(evidenceAssembler.assemble(USER, POSITION).currentWeight()).isEqualByComparingTo("0.1");
    }

    @Test
    void clusterWeightUsesInvestableAssets() {
        update(
                """
                INSERT INTO risk_cluster (id,user_id,cluster_code,display_name,risk_cap_fraction,created_at,updated_at)
                VALUES (UUID_TO_BIN('a6000000-0000-0000-0000-000000000001'),UUID_TO_BIN('a1000000-0000-0000-0000-000000000001'),'CAPITAL_CLUSTER','Capital cluster',0.25,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
        update(
                """
                INSERT INTO risk_cluster_membership (id,risk_cluster_id,position_id,contribution_weight,created_at) VALUES
                (UUID_TO_BIN('a7000000-0000-0000-0000-000000000001'),UUID_TO_BIN('a6000000-0000-0000-0000-000000000001'),UUID_TO_BIN('a4000000-0000-0000-0000-000000000001'),1,UTC_TIMESTAMP(6)),
                (UUID_TO_BIN('a7000000-0000-0000-0000-000000000002'),UUID_TO_BIN('a6000000-0000-0000-0000-000000000001'),UUID_TO_BIN('a4000000-0000-0000-0000-000000000002'),1,UTC_TIMESTAMP(6))
                """);

        assertThat(evidenceAssembler.assemble(USER, POSITION).clusterWeight()).isEqualByComparingTo("0.25");
    }

    private void update(String sql) {
        jdbc.sql(sql).update();
    }
}
