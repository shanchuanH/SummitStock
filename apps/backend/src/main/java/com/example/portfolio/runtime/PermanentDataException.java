package com.example.portfolio.runtime;

public final class PermanentDataException extends RuntimeException {
    private final String code;

    public PermanentDataException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
