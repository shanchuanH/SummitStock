package com.example.portfolio.market;

public final class MarketPipelineException extends RuntimeException {
    private final String code;
    private final boolean retryable;

    MarketPipelineException(String code, boolean retryable, Throwable cause) {
        super("Market pipeline provider call failed", cause);
        this.code = code;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }
}
