package com.example.portfolio.portfolio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.util.Set;
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
    private final ObjectMapper json;

    RecommendationAcknowledgementController(JdbcClient jdbc, Clock clock, ObjectMapper json) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.json = json;
    }

    @PostMapping("/{id}/acknowledge")
    @Transactional
    AcknowledgementResponse acknowledge(
            @PathVariable UUID id, @Valid @RequestBody AcknowledgementRequest request, Principal principal) {
        var recommendation = jdbc.sql(
                        """
                        SELECT (SELECT q.last_price FROM quote q JOIN position p ON p.instrument_id=q.instrument_id
                                WHERE p.id=r.position_id ORDER BY q.data_as_of DESC,q.created_at DESC LIMIT 1) referencePrice
                        FROM recommendation r JOIN app_user u ON u.id=r.user_id
                        WHERE r.id=UUID_TO_BIN(:id) AND u.email=:email
                        """)
                .param("id", id.toString())
                .param("email", principal.getName())
                .query(RecommendationRow.class)
                .optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var ackId = UUID.randomUUID();
        var inserted = jdbc.sql(
                        """
                INSERT IGNORE INTO recommendation_acknowledgement (
                    id,user_id,recommendation_id,idempotency_key,decision_type,rationale,reason_tags,reference_price,
                    acknowledged_at,created_at)
                SELECT UUID_TO_BIN(:ackId),u.id,UUID_TO_BIN(:recommendationId),:key,:decision,:rationale,:reasonTags,
                       :referencePrice,:now,:now
                FROM app_user u WHERE u.email=:email
                """)
                .param("ackId", ackId.toString())
                .param("recommendationId", id.toString())
                .param("key", request.idempotencyKey())
                .param("decision", request.decisionType())
                .param("rationale", request.rationale())
                .param("reasonTags", reasonTagsJson(request.reasonTags()))
                .param("referencePrice", recommendation.referencePrice())
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
            String rationale,
            Set<DecisionReasonTag> reasonTags) {
        AcknowledgementRequest {
            if (decisionType == null) decisionType = "HANDLED";
            reasonTags = reasonTags == null ? Set.of() : Set.copyOf(reasonTags);
        }
    }

    private String reasonTagsJson(Set<DecisionReasonTag> reasonTags) {
        try {
            return json.writeValueAsString(reasonTags);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Validated reason tags could not be serialized", exception);
        }
    }

    record RecommendationRow(BigDecimal referencePrice) {}

    record AcknowledgementResponse(
            UUID recommendationId,
            String decisionType,
            boolean newlyAcknowledged,
            boolean executionSubmitted,
            Instant acknowledgedAt) {}
}
