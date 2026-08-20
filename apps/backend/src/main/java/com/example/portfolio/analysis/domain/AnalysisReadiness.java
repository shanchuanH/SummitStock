package com.example.portfolio.analysis.domain;

public enum AnalysisReadiness {
    READY,
    PARTIAL,
    WAIT_FOR_MARKET_DATA,
    WAIT_FOR_FUNDAMENTALS,
    WAIT_FOR_CATALYST,
    WAIT_FOR_CLASSIFICATION,
    STALE,
    BLOCKED
}
