package com.example.portfolio.brief;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ExecutiveBriefStore {
    private final JdbcClient jdbc;

    ExecutiveBriefStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    Evidence evidence(String email) {
        var positions = jdbc.sql(
                        """
                        SELECT COUNT(*) open_positions,
                               COALESCE(SUM(NOT EXISTS (
                                   SELECT 1 FROM price_bar b
                                   WHERE b.instrument_id=p.instrument_id AND b.adjusted=TRUE
                                     AND b.quality_status NOT IN ('SUSPECT', 'MISSING')
                               )), 0) missing_market_positions,
                               COALESCE(SUM(p.classification IN ('QUALITY_STOCK','QUALITY_GROWTH_HIGH_VOL')), 0) required_fundamental_positions,
                               COALESCE(SUM(p.classification IN ('QUALITY_STOCK','QUALITY_GROWTH_HIGH_VOL') AND NOT EXISTS (
                                   SELECT 1 FROM financial_health_snapshot f
                                   WHERE f.instrument_id=p.instrument_id
                                     AND f.quality NOT IN ('SUSPECT', 'MISSING')
                                     AND f.overall_status<>'MISSING'
                               ) AND NOT EXISTS (
                                   SELECT 1 FROM fundamental_observation legacy
                                   WHERE legacy.instrument_id=p.instrument_id
                                     AND legacy.quality_status NOT IN ('SUSPECT', 'MISSING')
                               )), 0) missing_fundamental_positions,
                               COALESCE(SUM(EXISTS (
                                   SELECT 1 FROM holding_analysis_snapshot h WHERE h.position_id=p.id
                               )), 0) any_analysis_positions,
                               COALESCE(SUM(
                                   (SELECT h.analysis_status FROM holding_analysis_snapshot h
                                    WHERE h.position_id=p.id ORDER BY h.data_as_of DESC LIMIT 1)
                                       IN ('READY','ANALYSIS_READY','PARTIAL')
                                   AND (SELECT h.valid_until FROM holding_analysis_snapshot h
                                        WHERE h.position_id=p.id ORDER BY h.data_as_of DESC LIMIT 1)
                                       > UTC_TIMESTAMP(6)
                               ), 0) analyzed_positions,
                               COALESCE(SUM(EXISTS (
                                   SELECT 1 FROM holding_analysis_snapshot h WHERE h.position_id=p.id
                               ) AND (SELECT h.valid_until FROM holding_analysis_snapshot h
                                      WHERE h.position_id=p.id ORDER BY h.data_as_of DESC LIMIT 1)
                                   <= UTC_TIMESTAMP(6)), 0) stale_analysis_positions,
                               COALESCE(SUM(
                                   (SELECT h.analysis_status FROM holding_analysis_snapshot h
                                    WHERE h.position_id=p.id ORDER BY h.data_as_of DESC LIMIT 1)='BLOCKED'
                               ), 0) blocked_analysis_positions,
                               COALESCE(SUM(
                                   (SELECT h.analysis_status FROM holding_analysis_snapshot h
                                    WHERE h.position_id=p.id ORDER BY h.data_as_of DESC LIMIT 1)='FAILED'
                                   AND NOT EXISTS (
                                       SELECT 1 FROM holding_analysis_snapshot h
                                       WHERE h.position_id=p.id AND h.analysis_status IN ('READY','ANALYSIS_READY')
                                         AND h.valid_until > UTC_TIMESTAMP(6)
                                   )
                               ), 0) failed_analysis_positions
                        FROM position p
                        JOIN investment_account a ON a.id=p.account_id
                        JOIN app_user u ON u.id=a.user_id
                        WHERE u.email=:email AND p.status='OPEN'
                        """)
                .param("email", email)
                .query(PositionEvidence.class)
                .single();
        var jobs = jdbc.sql(
                        """
                        SELECT COALESCE(SUM(status IN ('PENDING','RUNNING')
                                            AND job_type IN ('PORTFOLIO_ANALYSIS','ANALYZE_PORTFOLIO')), 0) queued,
                               COALESCE(SUM(status IN ('FAILED','DEAD')), 0) failed
                        FROM job_run
                        WHERE JSON_UNQUOTE(JSON_EXTRACT(payload, '$.userEmail'))=:email
                           OR JSON_UNQUOTE(JSON_EXTRACT(payload, '$.email'))=:email
                        """)
                .param("email", email)
                .query(JobEvidence.class)
                .single();
        var workflow = jdbc.sql(
                        """
                        SELECT COALESCE(SUM(b.status='PREVIEW'),0) import_pending,
                               COALESCE(SUM(b.status='IMPORTING'),0) importing,
                               (SELECT COUNT(*) FROM portfolio_analysis_run r
                                JOIN app_user ru ON ru.id=r.user_id WHERE ru.email=:email) analysis_runs,
                               (SELECT COUNT(*) FROM portfolio_analysis_run r
                                JOIN app_user ru ON ru.id=r.user_id WHERE ru.email=:email
                                  AND r.status='QUEUED') analysis_queued,
                               (SELECT COUNT(*) FROM portfolio_analysis_run r
                                JOIN app_user ru ON ru.id=r.user_id WHERE ru.email=:email
                                  AND r.status='FAILED') analysis_failed,
                               (SELECT COUNT(*) FROM portfolio_analysis_run r
                                JOIN app_user ru ON ru.id=r.user_id WHERE ru.email=:email
                                  AND r.status='BLOCKED') analysis_blocked
                        FROM portfolio_import_batch b JOIN app_user u ON u.id=b.user_id
                        WHERE u.email=:email
                        """)
                .param("email", email)
                .query(WorkflowEvidence.class)
                .single();
        var blocked = jdbc.sql(
                        """
                        SELECT COUNT(*)
                        FROM data_quality_event q
                        JOIN position p ON p.instrument_id=q.instrument_id AND p.status='OPEN'
                        JOIN investment_account a ON a.id=p.account_id
                        JOIN app_user u ON u.id=a.user_id
                        WHERE u.email=:email AND q.status='OPEN' AND q.severity='CRITICAL'
                        """)
                .param("email", email)
                .query(Long.class)
                .single();
        return new Evidence(
                positions.openPositions(),
                positions.missingMarketPositions(),
                positions.requiredFundamentalPositions(),
                positions.missingFundamentalPositions(),
                positions.anyAnalysisPositions(),
                positions.analyzedPositions(),
                positions.staleAnalysisPositions(),
                workflow.importPending() > 0,
                workflow.importing() > 0,
                workflow.analysisRuns() > 0,
                jobs.queued() > 0 || workflow.analysisQueued() > 0,
                jobs.failed() + workflow.analysisFailed() + positions.failedAnalysisPositions(),
                blocked > 0 || workflow.analysisBlocked() > 0 || positions.blockedAnalysisPositions() > 0);
    }

    CashSummary cashSummary(String email) {
        return jdbc.sql(
                        """
                        SELECT COALESCE(SUM(c.current_amount),0) tracked_cash,
                               COALESCE(SUM(CASE WHEN c.bucket_type='EMERGENCY' THEN c.current_amount ELSE 0 END),0) emergency_cash,
                               COALESCE(SUM(CASE WHEN c.bucket_type='TACTICAL_RESERVE' THEN c.current_amount ELSE 0 END),0) tactical_reserve
                        FROM cash_bucket c JOIN app_user u ON u.id=c.user_id
                        WHERE u.email=:email
                        """)
                .param("email", email)
                .query(CashSummary.class)
                .single();
    }

    MarketSnapshot latestMarket() {
        return jdbc.sql(
                        """
                        SELECT regime_label regime,total_score score,confidence,quality_status qualityStatus,
                               data_as_of dataAsOf
                        FROM market_regime_snapshot
                        ORDER BY data_as_of DESC,created_at DESC LIMIT 1
                        """)
                .query(MarketSnapshot.class)
                .optional()
                .orElse(new MarketSnapshot("UNKNOWN", null, "WAIT_FOR_DATA", "MISSING", null));
    }

    PortfolioMetrics portfolioMetrics(String email) {
        var exposure = jdbc.sql(
                        """
                        WITH owned AS (
                            SELECT p.id,COALESCE(m.marked_market_value,0) market_value,p.classification
                            FROM position p JOIN investment_account a ON a.id=p.account_id
                            JOIN app_user u ON u.id=a.user_id
                            LEFT JOIN current_position_mark m ON m.position_id=p.id
                            WHERE u.email=:email AND p.status='OPEN'
                        ), latest_risk AS (
                            SELECT r.position_id,r.open_risk_fraction,r.cluster_risk_fraction,
                                   ROW_NUMBER() OVER (PARTITION BY r.position_id ORDER BY r.data_as_of DESC,r.created_at DESC) rn
                            FROM position_risk_snapshot r JOIN owned o ON o.id=r.position_id
                        ), cluster_totals AS (
                            SELECT m.risk_cluster_id,SUM(r.open_risk_fraction) cluster_risk
                            FROM risk_cluster_membership m JOIN latest_risk r ON r.position_id=m.position_id AND r.rn=1
                            GROUP BY m.risk_cluster_id
                        )
                        SELECT COALESCE(SUM(o.market_value),0) investedValue,
                               COALESCE(SUM(CASE WHEN o.classification IN ('CORE_BROAD_ETF','CORE_TECH_ETF') THEN o.market_value ELSE 0 END),0) coreValue,
                               COALESCE(SUM(CASE WHEN o.classification NOT IN ('CORE_BROAD_ETF','CORE_TECH_ETF') THEN o.market_value ELSE 0 END),0) tacticalValue,
                               COALESCE((SELECT SUM(open_risk_fraction) FROM latest_risk WHERE rn=1),0) openPlannedRisk,
                               COALESCE((SELECT MAX(cluster_risk) FROM cluster_totals),
                                        (SELECT MAX(cluster_risk_fraction) FROM latest_risk WHERE rn=1),0) clusterRisk
                        FROM owned o
                        """)
                .param("email", email)
                .query(ExposureMetrics.class)
                .single();
        var technology = technologyExposure(email);
        var compensation = jdbc.sql(
                        """
                        SELECT COALESCE(SUM(c.estimated_value),0)
                        FROM compensation_holding c JOIN app_user u ON u.id=c.user_id
                        WHERE u.email=:email AND c.vesting_status='UNVESTED'
                        """)
                .param("email", email)
                .query(BigDecimal.class)
                .single();
        var employer = jdbc.sql(
                        """
                        WITH owner AS (SELECT id FROM app_user WHERE email=:email),
                        liquid AS (
                            SELECT COALESCE(SUM(m.marked_market_value),0)+COALESCE((SELECT SUM(c.current_amount)
                                   FROM cash_bucket c WHERE c.user_id=(SELECT id FROM owner)),0) value
                            FROM position p JOIN investment_account a ON a.id=p.account_id
                            LEFT JOIN current_position_mark m ON m.position_id=p.id
                            WHERE a.user_id=(SELECT id FROM owner) AND p.status='OPEN'
                        ), compensation AS (
                            SELECT c.symbol,COALESCE(SUM(c.estimated_value),0) value
                            FROM compensation_holding c
                            WHERE c.user_id=(SELECT id FROM owner) AND c.vesting_status='UNVESTED'
                            GROUP BY c.symbol
                        ), employer AS (
                            SELECT c.symbol,c.value + COALESCE(SUM(m.marked_market_value),0) value
                            FROM compensation c
                            LEFT JOIN instrument i ON i.symbol=c.symbol
                            LEFT JOIN position p ON p.instrument_id=i.id AND p.status='OPEN'
                              AND p.account_id IN (SELECT id FROM investment_account WHERE user_id=(SELECT id FROM owner))
                            LEFT JOIN current_position_mark m ON m.position_id=p.id
                            GROUP BY c.symbol,c.value
                        )
                        SELECT CASE WHEN (SELECT value FROM liquid)+(SELECT COALESCE(SUM(value),0) FROM compensation)=0
                            THEN NULL ELSE COALESCE(MAX(value),0)/((SELECT value FROM liquid)+(SELECT COALESCE(SUM(value),0) FROM compensation)) END
                        FROM employer
                        """)
                .param("email", email)
                .query(BigDecimal.class)
                .optional()
                .orElse(null);
        var drawdown = jdbc.sql(
                        """
                        SELECT drawdown_fraction drawdownFraction,source_classification drawdownSource
                        FROM portfolio_drawdown_snapshot d JOIN app_user u ON u.id=d.user_id
                        WHERE u.email=:email ORDER BY d.data_as_of DESC,d.created_at DESC LIMIT 1
                        """)
                .param("email", email)
                .query(DrawdownMetrics.class)
                .optional()
                .orElse(new DrawdownMetrics(null, null));
        return new PortfolioMetrics(
                exposure.investedValue(),
                exposure.coreValue(),
                exposure.tacticalValue(),
                technology,
                employer,
                exposure.clusterRisk(),
                exposure.openPlannedRisk(),
                compensation,
                drawdown.drawdownFraction(),
                drawdown.drawdownSource());
    }

    private BigDecimal technologyExposure(String email) {
        return jdbc.sql(
                        """
                        SELECT CASE WHEN SUM(m.marked_market_value)=0 THEN NULL ELSE
                            SUM(CASE WHEN EXISTS (
                                SELECT 1 FROM risk_cluster_membership m
                                JOIN risk_cluster c ON c.id=m.risk_cluster_id
                                WHERE m.position_id=p.id AND c.cluster_code IN ('TECHNOLOGY','TECH')
                            ) THEN m.marked_market_value ELSE 0 END) / SUM(m.marked_market_value) END
                        FROM position p
                        JOIN investment_account a ON a.id=p.account_id
                        JOIN app_user u ON u.id=a.user_id
                        LEFT JOIN current_position_mark m ON m.position_id=p.id
                        WHERE u.email=:email AND p.status='OPEN'
                        """)
                .param("email", email)
                .query(BigDecimal.class)
                .optional()
                .orElse(null);
    }

    record ExposureMetrics(
            BigDecimal investedValue,
            BigDecimal coreValue,
            BigDecimal tacticalValue,
            BigDecimal openPlannedRisk,
            BigDecimal clusterRisk) {}

    record MarketSnapshot(
            String regime, Double score, String confidence, String qualityStatus, LocalDateTime dataAsOf) {}

    record DrawdownMetrics(BigDecimal drawdownFraction, String drawdownSource) {}

    record PortfolioMetrics(
            BigDecimal investedValue,
            BigDecimal coreValue,
            BigDecimal tacticalValue,
            BigDecimal technologyExposureFraction,
            BigDecimal employerExposureFraction,
            BigDecimal clusterRiskFraction,
            BigDecimal openPlannedRiskFraction,
            BigDecimal unvestedCompensationValue,
            BigDecimal drawdownFraction,
            String drawdownSource) {}

    AnalysisMetadata analysisMetadata(String email) {
        return jdbc.sql(
                        """
                        SELECT MAX(h.data_as_of) data_as_of,
                               SUBSTRING_INDEX(GROUP_CONCAT(h.strategy_version ORDER BY h.data_as_of DESC), ',', 1) strategy_version
                        FROM holding_analysis_snapshot h
                        JOIN position p ON p.id=h.position_id
                        JOIN investment_account a ON a.id=p.account_id
                        JOIN app_user u ON u.id=a.user_id
                        WHERE u.email=:email AND p.status='OPEN'
                        """)
                .param("email", email)
                .query(AnalysisMetadata.class)
                .single();
    }

    AnalysisRunMetadata latestAnalysisRun(String email) {
        return jdbc.sql(
                        """
                        SELECT BIN_TO_UUID(r.id) run_id, r.status, r.strategy_version,
                               r.data_as_of, r.created_at
                        FROM portfolio_analysis_run r JOIN app_user u ON u.id=r.user_id
                        WHERE u.email=:email ORDER BY r.created_at DESC LIMIT 1
                        """)
                .param("email", email)
                .query(AnalysisRunMetadata.class)
                .optional()
                .orElse(null);
    }

    String currentStrategyVersion(String email) {
        return jdbc.sql(
                        """
                        SELECT COALESCE(
                            (SELECT ip.strategy_version_code FROM investment_policy ip
                             JOIN app_user u ON u.id=ip.user_id WHERE u.email=:email
                             ORDER BY ip.updated_at DESC LIMIT 1),
                            (SELECT version_code FROM strategy_version
                             ORDER BY FIELD(status,'PUBLISHED','DRAFT','RETIRED'), created_at DESC LIMIT 1)
                        )
                        """)
                .param("email", email)
                .query(String.class)
                .optional()
                .orElse(null);
    }

    List<NextEvent> nextEvents(String email) {
        return jdbc.sql(
                        """
                        SELECT i.symbol, e.event_type, e.title, e.event_at, e.quality_status
                        FROM company_event e
                        JOIN instrument i ON i.id=e.instrument_id
                        JOIN position p ON p.instrument_id=e.instrument_id AND p.status='OPEN'
                        JOIN investment_account a ON a.id=p.account_id
                        JOIN app_user u ON u.id=a.user_id
                        WHERE u.email=:email AND e.event_at >= UTC_TIMESTAMP(6)
                        ORDER BY e.event_at
                        LIMIT 5
                        """)
                .param("email", email)
                .query(NextEvent.class)
                .list();
    }

    record PositionEvidence(
            long openPositions,
            long missingMarketPositions,
            long requiredFundamentalPositions,
            long missingFundamentalPositions,
            long anyAnalysisPositions,
            long analyzedPositions,
            long staleAnalysisPositions,
            long blockedAnalysisPositions,
            long failedAnalysisPositions) {}

    record JobEvidence(long queued, long failed) {}

    record WorkflowEvidence(
            long importPending,
            long importing,
            long analysisRuns,
            long analysisQueued,
            long analysisFailed,
            long analysisBlocked) {}

    record Evidence(
            long openPositions,
            long missingMarketPositions,
            long requiredFundamentalPositions,
            long missingFundamentalPositions,
            long anyAnalysisPositions,
            long analyzedPositions,
            long staleAnalysisPositions,
            boolean importPending,
            boolean importing,
            boolean analysisRunExists,
            boolean analysisQueued,
            long failedJobCount,
            boolean blocked) {}

    record CashSummary(BigDecimal trackedCash, BigDecimal emergencyCash, BigDecimal tacticalReserve) {}

    record AnalysisMetadata(LocalDateTime dataAsOf, String strategyVersion) {}

    record AnalysisRunMetadata(
            UUID runId, String status, String strategyVersion, LocalDateTime dataAsOf, LocalDateTime createdAt) {}

    record NextEvent(String symbol, String eventType, String title, LocalDateTime eventAt, String qualityStatus) {}
}
