CREATE TABLE position_classification_snapshot (
    id BINARY(16) NOT NULL,
    position_id BINARY(16) NOT NULL,
    classification VARCHAR(64) NOT NULL,
    classification_confirmed BOOLEAN NOT NULL,
    classification_source VARCHAR(32) NOT NULL,
    evidence_checksum CHAR(64) NOT NULL,
    data_as_of DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_position_classification_evidence (position_id,evidence_checksum),
    KEY ix_position_classification_as_of (position_id,data_as_of,created_at),
    CONSTRAINT fk_position_classification_position FOREIGN KEY (position_id) REFERENCES position (id) ON DELETE CASCADE,
    CONSTRAINT chk_position_classification_snapshot_source CHECK (
        classification_source IN ('SYSTEM_RULE','IMPORTED_MAPPING','USER_CONFIRMED','USER_OVERRIDE')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Existing positions become replayable only after this migration instant. This deliberately avoids
-- backdating today's classification into historical analysis runs.
INSERT INTO position_classification_snapshot (
    id,position_id,classification,classification_confirmed,classification_source,
    evidence_checksum,data_as_of,created_at)
SELECT UUID_TO_BIN(UUID()),p.id,p.classification,p.classification_confirmed,p.classification_source,
       SHA2(CONCAT(BIN_TO_UUID(p.id),':',p.classification,':',p.classification_confirmed,':',
                   p.classification_source,':V55'),256),UTC_TIMESTAMP(6),UTC_TIMESTAMP(6)
FROM position p;
