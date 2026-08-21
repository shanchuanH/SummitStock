package com.example.portfolio.analysis.domain;

public enum PortfolioAnalysisState {
    NO_PORTFOLIO,
    IMPORT_PENDING_CONFIRMATION,
    IMPORTING,
    PORTFOLIO_READY,
    ANALYSIS_QUEUED,
    UPDATING,
    WAIT_FOR_MARKET_DATA,
    WAIT_FOR_FUNDAMENTALS,
    PARTIAL_ANALYSIS,
    ANALYSIS_READY,
    STALE,
    BLOCKED,
    FAILED
}
