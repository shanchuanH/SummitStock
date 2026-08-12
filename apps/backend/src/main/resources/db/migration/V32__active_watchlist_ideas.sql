ALTER TABLE investment_idea
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE AFTER source_reference,
    ADD KEY ix_investment_idea_active (source_type, active, instrument_id);
