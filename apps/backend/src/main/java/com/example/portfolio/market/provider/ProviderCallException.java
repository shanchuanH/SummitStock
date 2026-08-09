package com.example.portfolio.market.provider;

public class ProviderCallException extends RuntimeException {
    private final ProviderErrorCode errorCode;
    private final Integer httpStatus;
    private final boolean retryable;

    public ProviderCallException(String code, String message, Integer httpStatus, boolean retryable) {
        this(parse(code), message, httpStatus, retryable);
    }

    public ProviderCallException(ProviderErrorCode errorCode, String message, Integer httpStatus, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public String code() {
        return errorCode.name();
    }

    public ProviderErrorCode errorCode() {
        return errorCode;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    public boolean retryable() {
        return retryable;
    }

    private static ProviderErrorCode parse(String code) {
        try {
            return ProviderErrorCode.valueOf(code);
        } catch (IllegalArgumentException exception) {
            return ProviderErrorCode.PROVIDER_UNAVAILABLE;
        }
    }
}
