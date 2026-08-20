ALTER TABLE recommendation_acknowledgement
    ADD COLUMN reason_tags JSON NULL AFTER rationale,
    ADD COLUMN reference_price DECIMAL(24,8) NULL AFTER reason_tags,
    ADD CONSTRAINT chk_recommendation_ack_reference_price CHECK (reference_price IS NULL OR reference_price > 0);

ALTER TABLE trade_journal
    ADD COLUMN reason_tags JSON NULL AFTER notes;
