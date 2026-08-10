CREATE TABLE risk_cluster_snapshot (
    id BINARY(16) NOT NULL,
    risk_cluster_id BINARY(16) NOT NULL,
    user_id BINARY(16) NOT NULL,
    open_risk_amount DECIMAL(24,8) NOT NULL,
    open_risk_fraction DECIMAL(18,10) NOT NULL,
    member_count INT NOT NULL,
    quality VARCHAR(32) NOT NULL,
    data_as_of DATETIME(6) NOT NULL,
    strategy_version VARCHAR(64) NOT NULL,
    strategy_config_hash CHAR(64) NOT NULL,
    evidence_checksum CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_cluster_risk_evidence (risk_cluster_id, strategy_version, data_as_of, evidence_checksum),
    KEY ix_cluster_risk_latest (user_id, risk_cluster_id, data_as_of, created_at),
    CONSTRAINT fk_cluster_risk_cluster FOREIGN KEY (risk_cluster_id) REFERENCES risk_cluster (id),
    CONSTRAINT fk_cluster_risk_user FOREIGN KEY (user_id) REFERENCES app_user (id),
    CONSTRAINT chk_cluster_risk_values CHECK (
        open_risk_amount >= 0 AND open_risk_fraction >= 0 AND member_count >= 0
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE position_risk_snapshot
    MODIFY cluster_risk_fraction DECIMAL(18,10) NOT NULL DEFAULT 0 COMMENT 'Deprecated; use risk_cluster_snapshot';

UPDATE position_risk_snapshot SET cluster_risk_fraction=0;
