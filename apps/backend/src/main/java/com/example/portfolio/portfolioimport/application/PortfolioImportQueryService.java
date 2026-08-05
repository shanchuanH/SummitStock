package com.example.portfolio.portfolioimport.application;

import com.example.portfolio.portfolioimport.domain.PortfolioImportPreview;
import com.example.portfolio.portfolioimport.infrastructure.PortfolioImportStore;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PortfolioImportQueryService {
    private final PortfolioImportStore store;

    public PortfolioImportQueryService(PortfolioImportStore store) {
        this.store = store;
    }

    public PortfolioImportPreview find(String email, UUID batchId) {
        return store.preview(email, batchId);
    }
}
