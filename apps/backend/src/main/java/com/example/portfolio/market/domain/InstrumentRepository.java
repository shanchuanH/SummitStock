package com.example.portfolio.market.domain;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InstrumentRepository extends JpaRepository<Instrument, UUID> {
    Optional<Instrument> findFirstBySymbolIgnoreCaseAndActiveTrue(String symbol);
}
