package com.example.portfolio.analysis.application;

import com.example.portfolio.MySqlIntegrationTest;
import com.example.portfolio.analysis.mark.PositionMarkService;
import com.example.portfolio.market.provider.TradingCalendar;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;

@SpringBootTest
@AutoConfigureMockMvc
abstract class HoldingAnalysisIntegrationFixture extends MySqlIntegrationTest {
    static final UUID USER_ID = UUID.fromString("91000000-0000-0000-0000-000000000001");
    static final String USER_EMAIL = "admin@example.local";
    static final UUID GOOGL_POSITION = UUID.fromString("94000000-0000-0000-0000-000000000001");
    static final UUID DRAM_POSITION = UUID.fromString("94000000-0000-0000-0000-000000000002");
    static final UUID DXYZ_POSITION = UUID.fromString("94000000-0000-0000-0000-000000000003");

    @Autowired
    protected JdbcClient jdbc;

    @Autowired
    protected HoldingAnalysisApplicationService analysis;

    @Autowired
    protected HoldingEvidenceAssembler evidenceAssembler;

    @Autowired
    protected RecommendationGenerationService recommendations;

    @Autowired
    protected PositionMarkService positionMarks;

    @Autowired
    protected TradingCalendar tradingCalendar;

    @Autowired
    protected Clock clock;

    @BeforeEach
    void seedHoldingEvidence() {
        cleanupHoldingEvidence();
        update(
                """
                INSERT INTO app_user (id,email,password_hash,status,timezone,created_at,updated_at)
                VALUES (UUID_TO_BIN('91000000-0000-0000-0000-000000000001'),'admin@example.local','x','ACTIVE','UTC',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
        update(
                """
                INSERT INTO investment_account (id,user_id,account_key,institution,account_type,display_name,currency,active,created_at,updated_at)
                VALUES (UUID_TO_BIN('92000000-0000-0000-0000-000000000001'),UUID_TO_BIN('91000000-0000-0000-0000-000000000001'),'analysis-fixture','Fidelity','BROKERAGE','Analysis fixture','USD',TRUE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
        update(
                """
                INSERT INTO instrument (id,symbol,exchange,asset_type,currency,cik,active,metadata,created_at,updated_at) VALUES
                (UUID_TO_BIN('93000000-0000-0000-0000-000000000001'),'GOOGL','NASDAQ','EQUITY','USD','1652044',TRUE,JSON_OBJECT(),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
                (UUID_TO_BIN('93000000-0000-0000-0000-000000000002'),'DRAM','NASDAQ','ETF','USD',NULL,TRUE,JSON_OBJECT(),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
                (UUID_TO_BIN('93000000-0000-0000-0000-000000000003'),'DXYZ','NYSE','EQUITY','USD',NULL,TRUE,JSON_OBJECT(),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
        update(
                """
                INSERT INTO position (id,account_id,instrument_id,bucket,classification,classification_confirmed,classification_source,quantity,average_cost,market_value,status,opened_at,created_at,updated_at,data_readiness) VALUES
                (UUID_TO_BIN('94000000-0000-0000-0000-000000000001'),UUID_TO_BIN('92000000-0000-0000-0000-000000000001'),UUID_TO_BIN('93000000-0000-0000-0000-000000000001'),'CORE','QUALITY_STOCK',TRUE,'USER_CONFIRMED',100,150,20000,'OPEN',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'RESOLVED'),
                (UUID_TO_BIN('94000000-0000-0000-0000-000000000002'),UUID_TO_BIN('92000000-0000-0000-0000-000000000001'),UUID_TO_BIN('93000000-0000-0000-0000-000000000002'),'CORE','THEMATIC_ETF',TRUE,'USER_CONFIRMED',100,25,3000,'OPEN',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'RESOLVED'),
                (UUID_TO_BIN('94000000-0000-0000-0000-000000000003'),UUID_TO_BIN('92000000-0000-0000-0000-000000000001'),UUID_TO_BIN('93000000-0000-0000-0000-000000000003'),'TACTICAL_OVERLAY','SPECULATIVE',TRUE,'USER_CONFIRMED',50,18,1000,'OPEN',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6),'RESOLVED')
                """);
        update(
                """
                INSERT INTO cash_bucket (id,user_id,bucket_type,target_amount,current_amount,currency,as_of,updated_at) VALUES
                (UUID_TO_BIN('95000000-0000-0000-0000-000000000001'),UUID_TO_BIN('91000000-0000-0000-0000-000000000001'),'EMERGENCY',20000,22000,'USD',CURRENT_DATE,UTC_TIMESTAMP(6)),
                (UUID_TO_BIN('95000000-0000-0000-0000-000000000002'),UUID_TO_BIN('91000000-0000-0000-0000-000000000001'),'TACTICAL_RESERVE',8000,8000,'USD',CURRENT_DATE,UTC_TIMESTAMP(6))
                """);
        update(
                """
                INSERT INTO quote (id,instrument_id,last_price,currency,provider,source_timestamp,checksum,quality_status,data_as_of,created_at) VALUES
                (UUID_TO_BIN('96000000-0000-0000-0000-000000000001'),UUID_TO_BIN('93000000-0000-0000-0000-000000000001'),200,'USD','TEST',UTC_TIMESTAMP(6),SHA2('q1',256),'HEALTHY',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
                (UUID_TO_BIN('96000000-0000-0000-0000-000000000002'),UUID_TO_BIN('93000000-0000-0000-0000-000000000002'),30,'USD','TEST',UTC_TIMESTAMP(6),SHA2('q2',256),'HEALTHY',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
                (UUID_TO_BIN('96000000-0000-0000-0000-000000000003'),UUID_TO_BIN('93000000-0000-0000-0000-000000000003'),20,'USD','TEST',UTC_TIMESTAMP(6),SHA2('q3',256),'HEALTHY',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
        update(
                """
                INSERT INTO price_bar (id,instrument_id,timeframe,bar_start,market_date,open_price,high_price,low_price,close_price,volume,adjusted,provider,source_timestamp,checksum,normalization_version,quality_status,data_as_of,created_at) VALUES
                (UUID_TO_BIN('97000000-0000-0000-0000-000000000001'),UUID_TO_BIN('93000000-0000-0000-0000-000000000001'),'1D',UTC_TIMESTAMP(6),CURRENT_DATE,198,202,197,200,1000,TRUE,'TEST',UTC_TIMESTAMP(6),SHA2('b1',256),'v1','HEALTHY',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
                (UUID_TO_BIN('97000000-0000-0000-0000-000000000002'),UUID_TO_BIN('93000000-0000-0000-0000-000000000002'),'1D',UTC_TIMESTAMP(6),CURRENT_DATE,29,31,28,30,1000,TRUE,'TEST',UTC_TIMESTAMP(6),SHA2('b2',256),'v1','HEALTHY',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
                (UUID_TO_BIN('97000000-0000-0000-0000-000000000003'),UUID_TO_BIN('93000000-0000-0000-0000-000000000003'),'1D',UTC_TIMESTAMP(6),CURRENT_DATE,19,21,18,20,1000,TRUE,'TEST',UTC_TIMESTAMP(6),SHA2('b3',256),'v1','HEALTHY',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
        for (int instrument = 1; instrument <= 3; instrument++) {
            update(
                    """
                    INSERT INTO indicator_snapshot (id,instrument_id,market_date,indicator_code,parameters_hash,adjusted,status,value_double,required_observations,actual_observations,warnings,source_bar_checksum,normalization_version,data_as_of,created_at)
                    VALUES (UUID_TO_BIN('98%06d-0000-0000-0000-000000000001'),UUID_TO_BIN('93000000-0000-0000-0000-00000000000%d'),CURRENT_DATE,'SMA_20',SHA2('p%d',256),TRUE,'READY',10,1,1,JSON_ARRAY(),SHA2('b%d',256),'v1',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                    """
                            .formatted(instrument, instrument, instrument, instrument));
        }
        jdbc.sql("UPDATE price_bar SET market_date=:marketDate WHERE instrument_id IN "
                        + "(UUID_TO_BIN('93000000-0000-0000-0000-000000000001'),"
                        + "UUID_TO_BIN('93000000-0000-0000-0000-000000000002'),"
                        + "UUID_TO_BIN('93000000-0000-0000-0000-000000000003'))")
                .param("marketDate", tradingCalendar.latestCompletedSession(clock.instant()))
                .update();
        positionMarks.captureForUser(USER_ID, clock.instant());
        update(
                """
                INSERT INTO fundamental_observation (id,instrument_id,metric_code,period_type,period_end,value_decimal,unit,currency,provider,source_timestamp,checksum,quality_status,data_as_of,created_at)
                VALUES (UUID_TO_BIN('99000000-0000-0000-0000-000000000001'),UUID_TO_BIN('93000000-0000-0000-0000-000000000001'),'REVENUE','ANNUAL',CURRENT_DATE,1000000,'USD','USD','SEC',UTC_TIMESTAMP(6),SHA2('f1',256),'HEALTHY',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
        update(
                """
                INSERT INTO estimate_revision_snapshot (id,instrument_id,period_end,horizon,revision_7d,revision_30d,revision_90d,overall_revision,analyst_count,quality,evidence_checksum,data_as_of,created_at)
                VALUES (UUID_TO_BIN('99000000-0000-0000-0000-000000000002'),UUID_TO_BIN('93000000-0000-0000-0000-000000000001'),DATE_ADD(CURRENT_DATE,INTERVAL 90 DAY),'NEXT_QUARTER','FLAT','FLAT','FLAT','FLAT',20,'HEALTHY',SHA2('r1',256),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
        update(
                """
                INSERT INTO valuation_snapshot (id,position_id,strategy_version,fundamental_health,valuation_discount,earnings_revisions,price_stabilization,portfolio_capacity,discount_tactical_weight,action,rule_ids,evidence_checksum,data_as_of,valid_until,created_at)
                VALUES (UUID_TO_BIN('99100000-0000-0000-0000-000000000001'),UUID_TO_BIN('94000000-0000-0000-0000-000000000001'),'3.0.0-draft','HEALTHY',FALSE,'STABLE','STABLE',TRUE,0,'HOLD',JSON_ARRAY('VALUATION.TEST'),SHA2('v1',256),UTC_TIMESTAMP(6),DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 30 DAY),UTC_TIMESTAMP(6))
                """);
        update(
                """
                INSERT INTO position_thesis (id,position_id,summary,confirmation_signals,invalidation_signals,status,expires_at,user_confirmed,created_at,updated_at)
                VALUES
                (UUID_TO_BIN('99200000-0000-0000-0000-000000000001'),UUID_TO_BIN('94000000-0000-0000-0000-000000000001'),'Quality compounder thesis',JSON_ARRAY('cash flow'),JSON_ARRAY('margin collapse'),'HEALTHY',DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 365 DAY),TRUE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
                (UUID_TO_BIN('99200000-0000-0000-0000-000000000003'),UUID_TO_BIN('94000000-0000-0000-0000-000000000003'),'Bounded speculative thesis',JSON_ARRAY('risk remains bounded'),JSON_ARRAY('formal stop'),'HEALTHY',DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 90 DAY),TRUE,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
        update(
                """
                INSERT INTO company_event (id,instrument_id,event_type,event_at,market_date,title,source,checksum,quality_status,data_as_of,metadata,created_at) VALUES
                (UUID_TO_BIN('99300000-0000-0000-0000-000000000001'),UUID_TO_BIN('93000000-0000-0000-0000-000000000001'),'EARNINGS',DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 20 DAY),DATE_ADD(CURRENT_DATE,INTERVAL 20 DAY),'GOOGL earnings','TEST',SHA2('e1',256),'HEALTHY',UTC_TIMESTAMP(6),JSON_OBJECT(),UTC_TIMESTAMP(6)),
                (UUID_TO_BIN('99300000-0000-0000-0000-000000000003'),UUID_TO_BIN('93000000-0000-0000-0000-000000000003'),'EARNINGS',DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 10 DAY),DATE_ADD(CURRENT_DATE,INTERVAL 10 DAY),'DXYZ event','TEST',SHA2('e3',256),'HEALTHY',UTC_TIMESTAMP(6),JSON_OBJECT(),UTC_TIMESTAMP(6))
                """);
        update(
                """
                INSERT INTO stop_snapshot (id,position_id,strategy_version,entry_price,atr,structure_stop,volatility_stop,initial_stop,live_stop,soft_alert,catastrophic_stop,close_confirmed,rule_ids,quality_status,evidence_checksum,data_as_of,created_at)
                VALUES (UUID_TO_BIN('99400000-0000-0000-0000-000000000003'),UUID_TO_BIN('94000000-0000-0000-0000-000000000003'),'3.0.0-draft',20,2,15,14,14,15,16,12,FALSE,JSON_ARRAY('STOP.TEST'),'HEALTHY',SHA2('s3',256),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
        update(
                """
                INSERT INTO position_risk_snapshot (id,position_id,strategy_version,current_weight,open_risk_fraction,cluster_risk_fraction,risk_amount,quality_status,evidence_checksum,data_as_of,created_at) VALUES
                (UUID_TO_BIN('99400000-0000-0000-0000-000000000011'),UUID_TO_BIN('94000000-0000-0000-0000-000000000001'),'3.0.0-draft',0.20,0.0001,0,10,'HEALTHY',SHA2('risk1',256),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
                (UUID_TO_BIN('99400000-0000-0000-0000-000000000012'),UUID_TO_BIN('94000000-0000-0000-0000-000000000002'),'3.0.0-draft',0.03,0.0001,0,10,'HEALTHY',SHA2('risk2',256),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)),
                (UUID_TO_BIN('99400000-0000-0000-0000-000000000013'),UUID_TO_BIN('94000000-0000-0000-0000-000000000003'),'3.0.0-draft',0.01,0.0001,0,10,'HEALTHY',SHA2('risk3',256),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
        update(
                """
                INSERT INTO market_regime_snapshot (id,strategy_version,regime_label,total_score,trend_score,momentum_score,breadth_score,stress_score,confidence,tactical_cap_five_percent,quality_status,inputs_json,narratives,rule_ids,evidence_checksum,data_as_of,created_at)
                VALUES (UUID_TO_BIN('99400000-0000-0000-0000-000000000020'),'3.0.0-draft','HEALTHY',75,75,70,70,10,'HIGH',FALSE,'HEALTHY',JSON_OBJECT(),JSON_ARRAY('fixture healthy regime'),JSON_ARRAY('REGIME.TEST'),SHA2('regime-fixture',256),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
        update(
                """
                INSERT INTO instrument_analysis_profile (id,instrument_id,profile_type,fund_profile_available,thematic,top_holding_concentration,fund_liquidity_status,portfolio_overlap_fraction,source,evidence_checksum,data_as_of,created_at)
                VALUES (UUID_TO_BIN('99500000-0000-0000-0000-000000000002'),UUID_TO_BIN('93000000-0000-0000-0000-000000000002'),'ETF',TRUE,TRUE,0.12,'HEALTHY',0.08,'IMPORTED_MAPPING',SHA2('profile2',256),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """);
    }

    @AfterEach
    void cleanupHoldingEvidence() {
        update("DELETE FROM investment_idea WHERE user_id=UUID_TO_BIN('91000000-0000-0000-0000-000000000001')");
        update("DELETE FROM recommendation WHERE user_id=UUID_TO_BIN('91000000-0000-0000-0000-000000000001')");
        update(
                "DELETE FROM earnings_risk_snapshot WHERE position_id IN (UUID_TO_BIN('94000000-0000-0000-0000-000000000001'),UUID_TO_BIN('94000000-0000-0000-0000-000000000002'),UUID_TO_BIN('94000000-0000-0000-0000-000000000003'))");
        update(
                "DELETE FROM holding_analysis_snapshot WHERE position_id IN (UUID_TO_BIN('94000000-0000-0000-0000-000000000001'),UUID_TO_BIN('94000000-0000-0000-0000-000000000002'),UUID_TO_BIN('94000000-0000-0000-0000-000000000003'))");
        update(
                "DELETE FROM instrument_analysis_profile WHERE instrument_id IN (UUID_TO_BIN('93000000-0000-0000-0000-000000000001'),UUID_TO_BIN('93000000-0000-0000-0000-000000000002'),UUID_TO_BIN('93000000-0000-0000-0000-000000000003'))");
        update("DELETE FROM stop_snapshot WHERE position_id=UUID_TO_BIN('94000000-0000-0000-0000-000000000003')");
        update(
                "DELETE FROM position_risk_snapshot WHERE position_id IN (UUID_TO_BIN('94000000-0000-0000-0000-000000000001'),UUID_TO_BIN('94000000-0000-0000-0000-000000000002'),UUID_TO_BIN('94000000-0000-0000-0000-000000000003'))");
        update("DELETE FROM market_regime_snapshot WHERE id=UUID_TO_BIN('99400000-0000-0000-0000-000000000020')");
        update(
                "DELETE FROM position_thesis WHERE position_id IN (UUID_TO_BIN('94000000-0000-0000-0000-000000000001'),UUID_TO_BIN('94000000-0000-0000-0000-000000000003'))");
        update("DELETE FROM valuation_snapshot WHERE position_id=UUID_TO_BIN('94000000-0000-0000-0000-000000000001')");
        update(
                "DELETE FROM company_event WHERE instrument_id IN (UUID_TO_BIN('93000000-0000-0000-0000-000000000001'),UUID_TO_BIN('93000000-0000-0000-0000-000000000003'))");
        update(
                "DELETE FROM fundamental_observation WHERE instrument_id=UUID_TO_BIN('93000000-0000-0000-0000-000000000001')");
        update(
                "DELETE FROM estimate_revision_snapshot WHERE instrument_id=UUID_TO_BIN('93000000-0000-0000-0000-000000000001')");
        update(
                "DELETE FROM indicator_snapshot WHERE instrument_id IN (UUID_TO_BIN('93000000-0000-0000-0000-000000000001'),UUID_TO_BIN('93000000-0000-0000-0000-000000000002'),UUID_TO_BIN('93000000-0000-0000-0000-000000000003'))");
        update(
                "DELETE FROM position_mark_snapshot WHERE position_id IN (UUID_TO_BIN('94000000-0000-0000-0000-000000000001'),UUID_TO_BIN('94000000-0000-0000-0000-000000000002'),UUID_TO_BIN('94000000-0000-0000-0000-000000000003'))");
        update(
                "DELETE FROM price_bar WHERE instrument_id IN (UUID_TO_BIN('93000000-0000-0000-0000-000000000001'),UUID_TO_BIN('93000000-0000-0000-0000-000000000002'),UUID_TO_BIN('93000000-0000-0000-0000-000000000003'))");
        update(
                "DELETE FROM quote WHERE instrument_id IN (UUID_TO_BIN('93000000-0000-0000-0000-000000000001'),UUID_TO_BIN('93000000-0000-0000-0000-000000000002'),UUID_TO_BIN('93000000-0000-0000-0000-000000000003'))");
        update("DELETE FROM cash_bucket WHERE user_id=UUID_TO_BIN('91000000-0000-0000-0000-000000000001')");
        update("DELETE FROM position WHERE account_id=UUID_TO_BIN('92000000-0000-0000-0000-000000000001')");
        update("DELETE FROM investment_account WHERE id=UUID_TO_BIN('92000000-0000-0000-0000-000000000001')");
        update(
                "DELETE FROM instrument WHERE id IN (UUID_TO_BIN('93000000-0000-0000-0000-000000000001'),UUID_TO_BIN('93000000-0000-0000-0000-000000000002'),UUID_TO_BIN('93000000-0000-0000-0000-000000000003'))");
        update("DELETE FROM app_user WHERE id=UUID_TO_BIN('91000000-0000-0000-0000-000000000001')");
    }

    private void update(String sql) {
        jdbc.sql(sql).update();
    }
}
