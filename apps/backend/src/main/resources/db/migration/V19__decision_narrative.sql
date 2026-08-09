CREATE TABLE decision_narrative (
    id BINARY(16) NOT NULL,
    recommendation_id BINARY(16) NOT NULL,
    source VARCHAR(32) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model_name VARCHAR(128) NOT NULL,
    headline VARCHAR(256) NOT NULL,
    one_sentence VARCHAR(2048) NOT NULL,
    why_items JSON NOT NULL,
    risk_items JSON NOT NULL,
    watch_next JSON NOT NULL,
    confidence_explanation VARCHAR(2048) NOT NULL,
    input_checksum CHAR(64) NOT NULL,
    output_checksum CHAR(64) NOT NULL,
    validation_status VARCHAR(1024) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_decision_narrative_recommendation (recommendation_id),
    CONSTRAINT fk_decision_narrative_recommendation
        FOREIGN KEY (recommendation_id) REFERENCES recommendation (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
