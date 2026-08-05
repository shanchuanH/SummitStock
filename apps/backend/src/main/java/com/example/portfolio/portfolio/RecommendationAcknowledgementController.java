package com.example.portfolio.portfolio;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/recommendations")
class RecommendationAcknowledgementController {
    private final JdbcClient jdbc;
    private final Clock clock;

    RecommendationAcknowledgementController(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @PostMapping("/{id}/acknowledge")
    @Transactional
    AcknowledgementResponse acknowledge(
            @PathVariable UUID id, @Valid @RequestBody AcknowledgementRequest request, Principal principal) {
        var recommendation = jdbc.sql(
                        "SELECT COUNT(*) FROM recommendation r JOIN app_user u ON u.id=r.user_id WHERE r.id=UUID_TO_BIN(:id) AND u.email=:email")
                .param("id", id.toString())
                .param("email", principal.getName())
                .query(Long.class)
                .single();
        if (recommendation != 1) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var ackId = UUID.randomUUID();
        var inserted = jdbc.sql(
                        """
                INSERT IGNORE INTO recommendation_acknowledgement (
                    id,user_id,recommendation_id,idempotency_key,decision_type,rationale,acknowledged_at,created_at)
                SELECT UUID_TO_BIN(:ackId),u.id,UUID_TO_BIN(:recommendationId),:key,:decision,:rationale,:now,:now
                FROM app_user u WHERE u.email=:email
                """)
                .param("ackId", ackId.toString())
                .param("recommendationId", id.toString())
                .param("key", request.idempotencyKey())
                .param("decision", request.decisionType())
                .param("rationale", request.rationale())
                .param("now", clock.instant())
                .param("email", principal.getName())
                .update();
        if (inserted == 1)
            jdbc.sql("UPDATE recommendation SET status=:status WHERE id=UUID_TO_BIN(:id)")
                    .param("id", id.toString())
                    .param(
                            "status",
                            switch (request.decisionType()) {
                                case "HANDLED" -> "ACKNOWLEDGED";
                                case "DEFERRED" -> "ACTIVE";
                                case "IGNORED" -> "OVERRIDDEN";
                                default -> throw new IllegalStateException("Validated decision is unsupported");
                            })
                    .update();
        return new AcknowledgementResponse(id, request.decisionType(), inserted == 1, false, clock.instant());
    }

    record AcknowledgementRequest(
            @NotBlank String idempotencyKey,
            @Pattern(regexp = "HANDLED|DEFERRED|IGNORED") String decisionType,
            String rationale) {
        AcknowledgementRequest {
            if (decisionType == null) decisionType = "HANDLED";
        }
    }

    record AcknowledgementResponse(
            UUID recommendationId,
            String decisionType,
            boolean newlyAcknowledged,
            boolean executionSubmitted,
            Instant acknowledgedAt) {}
}
