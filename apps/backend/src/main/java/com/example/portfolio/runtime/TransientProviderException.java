package com.example.portfolio.runtime;

public final class TransientProviderException extends RuntimeException {
    private final String code;

    public TransientProviderException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
