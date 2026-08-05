package com.example.portfolio.market.persistence;

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

    public ProviderRequestJournal(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID start(String requestKey, String provider, String operation, String contextJson) {
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
    }
}
