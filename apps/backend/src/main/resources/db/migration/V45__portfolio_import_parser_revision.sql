ALTER TABLE portfolio_import_batch
    ADD COLUMN parser_revision VARCHAR(64) NOT NULL DEFAULT 'legacy-v1' AFTER source_checksum,
    DROP INDEX uq_import_source_checksum,
    ADD UNIQUE KEY uq_import_source_parser_checksum (
        user_id, source, parser_revision, source_checksum
    );
