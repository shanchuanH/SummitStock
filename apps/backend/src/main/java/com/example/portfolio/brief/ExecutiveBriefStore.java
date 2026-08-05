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
                                     AND b.quality_status NOT IN ('INVALID', 'MISSING_DATA')
                               )), 0) missing_market_positions,
                               COALESCE(SUM(p.classification='QUALITY_STOCK'), 0) required_fundamental_positions,
                               COALESCE(SUM(p.classification='QUALITY_STOCK' AND NOT EXISTS (
                                   SELECT 1 FROM fundamental_observation f
                                   WHERE f.instrument_id=p.instrument_id
                                     AND f.quality_status NOT IN ('INVALID', 'MISSING_DATA')
                               )), 0) missing_fundamental_positions,
                               COALESCE(SUM(EXISTS (
                                   SELECT 1 FROM holding_analysis_snapshot h WHERE h.position_id=p.id
                               )), 0) any_analysis_positions,
                               COALESCE(SUM(
                                   (SELECT h.analysis_status FROM holding_analysis_snapshot h
                                    WHERE h.position_id=p.id ORDER BY h.data_as_of DESC LIMIT 1)
                                       IN ('READY','ANALYSIS_READY')
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

    BigDecimal technologyExposure(String email) {
        return jdbc.sql(
                        """
                        SELECT CASE WHEN SUM(p.market_value)=0 THEN NULL ELSE
                            SUM(CASE WHEN EXISTS (
                                SELECT 1 FROM risk_cluster_membership m
                                JOIN risk_cluster c ON c.id=m.risk_cluster_id
                                WHERE m.position_id=p.id AND c.cluster_code IN ('TECHNOLOGY','TECH')
                            ) THEN p.market_value ELSE 0 END) / SUM(p.market_value) END
                        FROM position p
                        JOIN investment_account a ON a.id=p.account_id
                        JOIN app_user u ON u.id=a.user_id
                        WHERE u.email=:email AND p.status='OPEN'
                        """)
                .param("email", email)
                .query(BigDecimal.class)
                .optional()
                .orElse(null);
    }

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
