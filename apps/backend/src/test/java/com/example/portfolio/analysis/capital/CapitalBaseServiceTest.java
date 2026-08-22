package com.example.portfolio.analysis.capital;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.portfolio.MySqlIntegrationTest;
import com.example.portfolio.analysis.allocation.PortfolioAllocationService;
import com.example.portfolio.analysis.allocation.PortfolioSleeve;
import com.example.portfolio.analysis.application.HoldingEvidenceAssembler;
import com.example.portfolio.analysis.mark.PositionMarkService;
import com.example.portfolio.analysis.replay.DecisionAsOfContext;
import com.example.portfolio.analysis.risk.ClusterRiskService;
import com.example.portfolio.market.provider.TradingCalendar;
import com.example.portfolio.strategy.portfolio.HoldingClassification;
import java.time.Clock;
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

    @Autowired
    PositionMarkService positionMarks;

    @Autowired
    PortfolioAllocationService allocations;

    @Autowired
    ClusterRiskService clusterRisks;

    @Autowired
    TradingCalendar calendar;

    @Autowired
    Clock clock;

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
                (UUID_TO_BIN('a4000000-0000-0000-0000-000000000001'),UUID_TO_BIN('a2000000-0000-0000-0000-000000000001'),UUID_TO_BIN('a3000000-0000-0000-0000-000000000001'),'CORE','CORE_TECH_ETF',TRUE,'USER_CONFIRMED',80,100,8000,'OPEN',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'RESOLVED'),
                (UUID_TO_BIN('a4000000-0000-0000-0000-000000000002'),UUID_TO_BIN('a2000000-0000-0000-0000-000000000001'),UUID_TO_BIN('a3000000-0000-0000-0000-000000000002'),'CORE','CORE_TECH_ETF',TRUE,'USER_CONFIRMED',120,100,12000,'OPEN',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'RESOLVED'),
                (UUID_TO_BIN('a4000000-0000-0000-0000-000000000003'),UUID_TO_BIN('a2000000-0000-0000-0000-000000000001'),UUID_TO_BIN('a3000000-0000-0000-0000-000000000003'),'CORE','CORE_BROAD_ETF',TRUE,'USER_CONFIRMED',600,100,60000,'OPEN',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'RESOLVED')
                """);
        update(
                """
                INSERT INTO cash_bucket (id,user_id,bucket_type,target_amount,current_amount,currency,as_of,updated_at)
                VALUES (UUID_TO_BIN('a5000000-0000-0000-0000-000000000001'),UUID_TO_BIN('a1000000-0000-0000-0000-000000000001'),'EMERGENCY',20000,20000,'USD',CURRENT_DATE,UTC_TIMESTAMP(6))
                """);
        var completedSession = calendar.latestCompletedSession(clock.instant());
        jdbc.sql(
                        """
                        INSERT INTO price_bar (id,instrument_id,timeframe,bar_start,market_date,open_price,high_price,
                          low_price,close_price,volume,adjusted,provider,source_timestamp,checksum,
                          normalization_version,quality_status,data_as_of,created_at) VALUES
                        (UUID_TO_BIN('a8000000-0000-0000-0000-000000000001'),UUID_TO_BIN('a3000000-0000-0000-0000-000000000001'),'1D',:now,:date,100,100,100,100,1000,TRUE,'TEST',:now,SHA2('cap1',256),'v1','HEALTHY',:now,:now),
                        (UUID_TO_BIN('a8000000-0000-0000-0000-000000000002'),UUID_TO_BIN('a3000000-0000-0000-0000-000000000002'),'1D',:now,:date,100,100,100,100,1000,TRUE,'TEST',:now,SHA2('cap2',256),'v1','HEALTHY',:now,:now),
                        (UUID_TO_BIN('a8000000-0000-0000-0000-000000000003'),UUID_TO_BIN('a3000000-0000-0000-0000-000000000003'),'1D',:now,:date,100,100,100,100,1000,TRUE,'TEST',:now,SHA2('cap3',256),'v1','HEALTHY',:now,:now)
                        """)
                .param("now", clock.instant())
                .param("date", completedSession)
                .update();
        positionMarks.captureForUser(USER, clock.instant());
    }

    @AfterEach
    void cleanup() {
        update(
                "DELETE FROM portfolio_capital_snapshot WHERE user_id=UUID_TO_BIN('a1000000-0000-0000-0000-000000000001')");
        update(
                "DELETE FROM portfolio_allocation_snapshot WHERE user_id=UUID_TO_BIN('a1000000-0000-0000-0000-000000000001')");
        update("DELETE FROM risk_cluster_snapshot WHERE user_id=UUID_TO_BIN('a1000000-0000-0000-0000-000000000001')");
        update(
                "DELETE FROM position_risk_snapshot WHERE position_id IN (UUID_TO_BIN('a4000000-0000-0000-0000-000000000001'),UUID_TO_BIN('a4000000-0000-0000-0000-000000000002'),UUID_TO_BIN('a4000000-0000-0000-0000-000000000003'))");
        update(
                "DELETE FROM position_mark_snapshot WHERE position_id IN (UUID_TO_BIN('a4000000-0000-0000-0000-000000000001'),UUID_TO_BIN('a4000000-0000-0000-0000-000000000002'),UUID_TO_BIN('a4000000-0000-0000-0000-000000000003'))");
        update(
                "DELETE FROM risk_cluster_membership WHERE position_id IN (UUID_TO_BIN('a4000000-0000-0000-0000-000000000001'),UUID_TO_BIN('a4000000-0000-0000-0000-000000000002'))");
        update("DELETE FROM risk_cluster WHERE user_id=UUID_TO_BIN('a1000000-0000-0000-0000-000000000001')");
        update("DELETE FROM position WHERE account_id=UUID_TO_BIN('a2000000-0000-0000-0000-000000000001')");
        update("DELETE FROM cash_bucket WHERE user_id=UUID_TO_BIN('a1000000-0000-0000-0000-000000000001')");
        update(
                "DELETE FROM price_bar WHERE instrument_id IN (UUID_TO_BIN('a3000000-0000-0000-0000-000000000001'),UUID_TO_BIN('a3000000-0000-0000-0000-000000000002'),UUID_TO_BIN('a3000000-0000-0000-0000-000000000003'))");
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
        assertThat(capital.requiredEmergencyFloor()).isEqualByComparingTo("20000");
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
                .isEqualTo("3.0.0-draft");
    }

    @Test
    void ownerProtectedCashAboveRequiredFloorIsFullyExcluded() {
        update(
                "UPDATE cash_bucket SET target_amount=35000,current_amount=35000 WHERE user_id=UUID_TO_BIN('a1000000-0000-0000-0000-000000000001')");

        var capital = capitalBases.calculate(USER);

        assertThat(capital.requiredEmergencyFloor()).isEqualByComparingTo("20000");
        assertThat(capital.protectedEmergencyAmount()).isEqualByComparingTo("35000");
        assertThat(capital.deployableCash()).isZero();
        assertThat(capital.strategyNav()).isEqualByComparingTo("80000");
        assertThat(capital.totalLiquidAssets()).isEqualByComparingTo("115000");
    }

    @Test
    void positionWeightUsesInvestableAssets() {
        assertThat(evidenceAssembler.assemble(USER, POSITION).currentWeight()).isEqualByComparingTo("0.1");
    }

    @Test
    void allocationAggregatesMultiplePositionsIntoOneSleeve() {
        var values = allocations.calculate(USER);

        assertThat(values.get(PortfolioSleeve.TECH_CORE).markedMarketValue()).isEqualByComparingTo("20000");
        assertThat(values.get(PortfolioSleeve.TECH_CORE).currentWeight()).isEqualByComparingTo("0.25");
        assertThat(values.get(PortfolioSleeve.TECH_CORE).gapWeight()).isZero();
        assertThat(values.get(PortfolioSleeve.BROAD_CORE).markedMarketValue()).isEqualByComparingTo("60000");
        assertThat(allocations.capture(USER, clock.instant())).isEqualTo(PortfolioSleeve.values().length);
    }

    @Test
    void historicalAllocationAndClassificationIgnoreLaterCurrentStateChanges() {
        var cutoff = clock.instant().plusSeconds(1);
        jdbc.sql(
                        """
                INSERT INTO position_classification_snapshot (
                  id,position_id,classification,classification_confirmed,classification_source,
                  evidence_checksum,data_as_of,created_at)
                VALUES (UUID_TO_BIN(UUID()),UUID_TO_BIN('a4000000-0000-0000-0000-000000000001'),
                  'CORE_TECH_ETF',TRUE,'USER_CONFIRMED',SHA2('capital-t0-classification',256),
                  DATE_SUB(UTC_TIMESTAMP(6),INTERVAL 1 SECOND),UTC_TIMESTAMP(6))
                """)
                .update();
        capitalBases.capture(USER, cutoff);
        allocations.capture(USER, cutoff);
        var context = new DecisionAsOfContext(calendar.latestCompletedSession(cutoff), cutoff, "3.0.0-draft");

        update("UPDATE position SET classification='SPECULATIVE',classification_confirmed=TRUE "
                + "WHERE id=UUID_TO_BIN('a4000000-0000-0000-0000-000000000001')");
        jdbc.sql(
                        """
                INSERT INTO position_classification_snapshot (
                  id,position_id,classification,classification_confirmed,classification_source,
                  evidence_checksum,data_as_of,created_at)
                VALUES (UUID_TO_BIN(UUID()),UUID_TO_BIN('a4000000-0000-0000-0000-000000000001'),
                  'SPECULATIVE',TRUE,'USER_OVERRIDE',SHA2('capital-t1-classification',256),
                  DATE_ADD(:cutoff,INTERVAL 1 SECOND),DATE_ADD(:cutoff,INTERVAL 1 SECOND))
                """)
                .param("cutoff", cutoff)
                .update();

        var sleeve = allocations.forPosition(USER, HoldingClassification.CORE_TECH_ETF, "CAP1", context);
        var historical = evidenceAssembler.assemble(USER, POSITION, context);

        assertThat(sleeve.markedMarketValue()).isEqualByComparingTo("20000");
        assertThat(sleeve.currentWeight()).isEqualByComparingTo("0.25");
        assertThat(historical.position().classification()).isEqualTo(HoldingClassification.CORE_TECH_ETF);
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

        update(
                """
                INSERT INTO position_risk_snapshot (id,position_id,strategy_version,current_weight,open_risk_fraction,
                  cluster_risk_fraction,risk_amount,quality_status,evidence_checksum,data_as_of,created_at) VALUES
                (UUID_TO_BIN('a9000000-0000-0000-0000-000000000001'),UUID_TO_BIN('a4000000-0000-0000-0000-000000000001'),'3.0.0-draft',0.10,0.0030,0,240,'HEALTHY',SHA2('risk1',256),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
                (UUID_TO_BIN('a9000000-0000-0000-0000-000000000002'),UUID_TO_BIN('a4000000-0000-0000-0000-000000000002'),'3.0.0-draft',0.15,0.0025,0,200,'HEALTHY',SHA2('risk2',256),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);

        assertThat(clusterRisks.capture(USER, clock.instant())).isEqualTo(1);
        assertThat(clusterRisks.forPosition(POSITION).openRiskAmount()).isEqualByComparingTo("440");
        assertThat(clusterRisks.forPosition(POSITION).openRiskFraction()).isEqualByComparingTo("0.0055");

        assertThat(evidenceAssembler.assemble(USER, POSITION).clusterWeight()).isEqualByComparingTo("0.25");
        assertThat(evidenceAssembler.assemble(USER, POSITION).clusterOpenRisk()).isEqualByComparingTo("0.0055");
    }

    private void update(String sql) {
        jdbc.sql(sql).update();
    }
}
