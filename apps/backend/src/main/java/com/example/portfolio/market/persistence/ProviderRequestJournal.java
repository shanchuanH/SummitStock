package com.example.portfolio.market.persistence;

import com.example.portfolio.market.provider.ProviderCostControl;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ProviderRequestJournal {
    private final JdbcClient jdbc;
    private final Clock clock;
    private final ProviderCostControl costControl;

    public ProviderRequestJournal(JdbcClient jdbc, Clock clock, ProviderCostControl costControl) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.costControl = costControl;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID start(String requestKey, String provider, String operation, String contextJson) {
        costControl.acquire(provider, operation);
        var id = UUID.randomUUID();
        jdbc.sql(
                        """
                        INSERT INTO provider_request (
                            id, request_key, provider, operation, status, attempt_count,
                            requested_at, normalization_version, request_context
                        ) VALUES (UUID_TO_BIN(:id), :requestKey, :provider, :operation, 'RUNNING', 1,
                                  :requestedAt, 'v1', CAST(:context AS JSON))
                        AS incoming
                        ON DUPLICATE KEY UPDATE
                            attempt_count = provider_request.attempt_count + 1,
                            status = 'RUNNING', requested_at = incoming.requested_at,
                            error_code = NULL, error_detail = NULL
                        """)
                .param("id", id.toString())
                .param("requestKey", requestKey)
                .param("provider", provider)
                .param("operation", operation)
                .param("requestedAt", clock.instant())
                .param("context", contextJson)
                .update();
        return id;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void succeed(
            String requestKey,
            Instant sourceTimestamp,
            String checksum,
            String qualityStatus,
            String responseMetadataJson) {
        jdbc.sql(
                        """
                        UPDATE provider_request
                        SET status = 'SUCCEEDED', completed_at = :completedAt,
                            source_timestamp = :sourceTimestamp, response_checksum = :checksum,
                            quality_status = :qualityStatus,
                            response_metadata = CAST(:metadata AS JSON)
                        WHERE request_key = :requestKey
                        """)
                .param("completedAt", clock.instant())
                .param("sourceTimestamp", sourceTimestamp)
                .param("checksum", checksum)
                .param("qualityStatus", qualityStatus)
                .param("metadata", responseMetadataJson)
                .param("requestKey", requestKey)
                .update();
        jdbc.sql("""
                        UPDATE provider_usage_daily u JOIN provider_request p
                          ON p.provider=u.provider_id AND DATE(p.requested_at)=u.usage_date AND p.operation=u.operation
                        SET u.success_count=u.success_count+1,u.last_success_at=:now,u.last_error_code=NULL
                        WHERE p.request_key=:requestKey
                        """)
                .param("now", clock.instant())
                .param("requestKey", requestKey)
                .update();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(String requestKey, Integer httpStatus, String errorCode, String detail) {
        var spec = jdbc.sql(
                        """
                        UPDATE provider_request
                        SET status = 'FAILED', completed_at = :completedAt,
                            http_status = :httpStatus, error_code = :errorCode, error_detail = :detail
                        WHERE request_key = :requestKey
                        """)
                .param("completedAt", clock.instant());
        if (httpStatus == null) spec = spec.param("httpStatus", null, java.sql.Types.INTEGER);
        else spec = spec.param("httpStatus", httpStatus);
        spec.param("errorCode", errorCode)
                .param("detail", detail)
                .param("requestKey", requestKey)
                .update();
        jdbc.sql("""
                        UPDATE provider_usage_daily u JOIN provider_request p
                          ON p.provider=u.provider_id AND DATE(p.requested_at)=u.usage_date AND p.operation=u.operation
                        SET u.failure_count=u.failure_count+1,u.last_error_code=:errorCode
                        WHERE p.request_key=:requestKey
                        """)
                .param("errorCode", errorCode)
                .param("requestKey", requestKey)
                .update();
    }
}
