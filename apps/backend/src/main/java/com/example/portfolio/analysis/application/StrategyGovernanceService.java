package com.example.portfolio.analysis.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Makes a successful, bias-clear OOS artifact and human approval mandatory before publication. */
@Service
public class StrategyGovernanceService {
    private final JdbcClient jdbc;
    private final Clock clock;

    public StrategyGovernanceService(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public void approve(String version, UUID backtestRunId, String artifactChecksum, String approver) {
        requireSha256(artifactChecksum);
        if (approver == null || approver.isBlank()) throw new IllegalArgumentException("STRATEGY_APPROVER_REQUIRED");
        var eligible = jdbc.sql(
                        """
                SELECT COUNT(*) FROM strategy_version s JOIN backtest_run b ON b.id=UUID_TO_BIN(:runId)
                WHERE s.version_code=:version AND s.status='DRAFT'
                  AND b.strategy_version=s.version_code AND b.config_checksum=s.config_hash
                  AND b.status='SUCCEEDED' AND b.bias_status='CLEAR'
                  AND b.training_through IS NOT NULL AND b.out_of_sample_from IS NOT NULL
                  AND b.training_through < b.out_of_sample_from
                """)
                .param("runId", backtestRunId.toString())
                .param("version", version)
                .query(Integer.class)
                .single();
        if (eligible != 1) throw new IllegalStateException("STRATEGY_BACKTEST_NOT_ELIGIBLE");
        var updated = jdbc.sql(
                        """
                UPDATE strategy_version SET approved_backtest_run_id=UUID_TO_BIN(:runId),
                  backtest_artifact_checksum=:artifact, approved_by=:approver, approved_at=:approvedAt
                WHERE version_code=:version AND status='DRAFT'
                """)
                .param("runId", backtestRunId.toString())
                .param("artifact", artifactChecksum)
                .param("approver", approver.trim())
                .param("approvedAt", Instant.now(clock))
                .param("version", version)
                .update();
        if (updated != 1) throw new IllegalStateException("STRATEGY_VERSION_NOT_DRAFT");
    }

    @Transactional
    public void publish(String version) {
        var updated = jdbc.sql(
                        """
                UPDATE strategy_version SET status='PUBLISHED', published_at=:publishedAt
                WHERE version_code=:version AND status='DRAFT' AND approved_at IS NOT NULL
                  AND approved_backtest_run_id IS NOT NULL AND backtest_artifact_checksum IS NOT NULL
                """)
                .param("publishedAt", Instant.now(clock))
                .param("version", version)
                .update();
        if (updated != 1) throw new IllegalStateException("STRATEGY_RELEASE_NOT_APPROVED");
    }

    private static void requireSha256(String value) {
        if (value == null || !value.matches("[0-9a-fA-F]{64}"))
            throw new IllegalArgumentException("BACKTEST_ARTIFACT_CHECKSUM_REQUIRED");
    }
}
