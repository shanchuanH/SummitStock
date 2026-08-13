ALTER TABLE trade_journal
    ADD COLUMN quantity_delta DECIMAL(28,10) NULL AFTER tax_status,
    ADD COLUMN execution_price DECIMAL(24,8) NULL AFTER quantity_delta,
    ADD COLUMN realized_pnl DECIMAL(24,8) NULL AFTER execution_price,
    ADD CONSTRAINT chk_trade_journal_execution CHECK (
        (quantity_delta IS NULL AND execution_price IS NULL)
        OR (quantity_delta <> 0 AND execution_price > 0)
    );

