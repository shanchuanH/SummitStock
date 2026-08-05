package com.example.portfolio.market.provider;

public class ProviderCallException extends RuntimeException {
    private final String code;
    private final Integer httpStatus;
    private final boolean retryable;

    public ProviderCallException(String code, String message, Integer httpStatus, boolean retryable) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    public boolean retryable() {
        return retryable;
    }
}
