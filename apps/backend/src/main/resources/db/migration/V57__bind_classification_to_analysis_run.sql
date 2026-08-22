CREATE TABLE analysis_run_position_classification (
    id BINARY(16) NOT NULL,
    analysis_run_id BINARY(16) NOT NULL,
    position_id BINARY(16) NOT NULL,
    classification_snapshot_id BINARY(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_run_position_classification (analysis_run_id, position_id),
    KEY ix_run_classification_snapshot (classification_snapshot_id),
    CONSTRAINT fk_run_classification_run FOREIGN KEY (analysis_run_id)
        REFERENCES portfolio_analysis_run(id) ON DELETE CASCADE,
    CONSTRAINT fk_run_classification_position FOREIGN KEY (position_id)
        REFERENCES `position`(id) ON DELETE CASCADE,
    CONSTRAINT fk_run_classification_snapshot FOREIGN KEY (classification_snapshot_id)
        REFERENCES position_classification_snapshot(id) ON DELETE RESTRICT
);

INSERT IGNORE INTO analysis_run_position_classification (
    id, analysis_run_id, position_id, classification_snapshot_id, created_at
)
SELECT UUID_TO_BIN(UUID()), r.id, p.id,
       (
           SELECT s.id
           FROM position_classification_snapshot s
           WHERE s.position_id = p.id AND s.created_at <= r.created_at
           ORDER BY s.created_at DESC, s.id DESC
           LIMIT 1
       ),
       r.created_at
FROM portfolio_analysis_run r
JOIN investment_account a ON a.user_id = r.user_id
JOIN position p ON p.account_id = a.id
WHERE EXISTS (
    SELECT 1 FROM position_classification_snapshot s
    WHERE s.position_id = p.id AND s.created_at <= r.created_at
);
