ALTER TABLE portfolio_import_batch
    ADD COLUMN parser_revision VARCHAR(32) NOT NULL DEFAULT 'legacy-v1' AFTER source_checksum,
    DROP INDEX uq_import_source_checksum,
    ADD UNIQUE KEY uq_import_source_checksum_revision (user_id, source, source_checksum, parser_revision);
