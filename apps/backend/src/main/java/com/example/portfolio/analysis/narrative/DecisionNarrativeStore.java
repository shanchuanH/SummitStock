package com.example.portfolio.analysis.narrative;

import com.example.portfolio.analysis.application.AnalysisChecksum;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Repository
public class DecisionNarrativeStore {
    private final JdbcClient jdbc;
    private final ObjectMapper json;

    public DecisionNarrativeStore(JdbcClient jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void append(UUID recommendationId, NarrativeGenerator.GeneratedNarrative generated, Instant createdAt) {
        var narrative = generated.narrative();
        jdbc.sql(
                        """
                        INSERT IGNORE INTO decision_narrative (
                            id,recommendation_id,source,provider,model_name,headline,one_sentence,
                            why_items,risk_items,watch_next,confidence_explanation,input_checksum,
                            output_checksum,validation_status,created_at
                        ) VALUES (
                            UUID_TO_BIN(:id),UUID_TO_BIN(:recommendationId),:source,:provider,:model,:headline,
                            :oneSentence,CAST(:why AS JSON),CAST(:risks AS JSON),CAST(:watch AS JSON),
                            :confidence,:inputChecksum,:outputChecksum,:validationStatus,:createdAt
                        )
                        """)
                .param("id", UUID.randomUUID().toString())
                .param("recommendationId", recommendationId.toString())
                .param("source", generated.source())
                .param("provider", generated.provider())
                .param("model", generated.model())
                .param("headline", narrative.headline())
                .param("oneSentence", narrative.oneSentence())
                .param("why", serialize(narrative.why()))
                .param("risks", serialize(narrative.risks()))
                .param("watch", serialize(narrative.watchNext()))
                .param("confidence", narrative.confidenceExplanation())
                .param("inputChecksum", AnalysisChecksum.sha256(serialize(generated.input())))
                .param("outputChecksum", AnalysisChecksum.sha256(serialize(narrative)))
                .param("validationStatus", generated.validationStatus())
                .param("createdAt", createdAt)
                .update();
    }

    private String serialize(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Could not serialize decision narrative", exception);
        }
    }
}
