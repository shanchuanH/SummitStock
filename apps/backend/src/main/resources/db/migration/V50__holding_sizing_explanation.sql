ALTER TABLE holding_analysis_snapshot
    ADD COLUMN sizing_limiting_constraint VARCHAR(64) NULL AFTER recommended_quantity_max,
    ADD COLUMN projected_position_weight DECIMAL(20,10) NULL AFTER sizing_limiting_constraint,
    ADD COLUMN projected_total_risk DECIMAL(20,10) NULL AFTER projected_position_weight,
    ADD COLUMN projected_cluster_risk DECIMAL(20,10) NULL AFTER projected_total_risk,
    ADD COLUMN sizing_risk_per_share DECIMAL(20,6) NULL AFTER projected_cluster_risk,
    ADD COLUMN quantity_before_limiting_constraint DECIMAL(20,6) NULL AFTER sizing_risk_per_share;
