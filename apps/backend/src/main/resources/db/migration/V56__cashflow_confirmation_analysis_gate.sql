ALTER TABLE portfolio_import_batch
    DROP CHECK chk_import_batch_status,
    MODIFY COLUMN status VARCHAR(48) NOT NULL,
    ADD CONSTRAINT chk_import_batch_status CHECK (
        status IN ('PREVIEW','IMPORTING','WAITING_FOR_CASHFLOW_CONFIRMATION','CONFIRMED','FAILED')
    );
