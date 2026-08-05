package com.example.portfolio.quant;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

public record QuantBar(
        LocalDate marketDate,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal volume,
        boolean completed) {
    public QuantBar {
        Objects.requireNonNull(marketDate);
        Objects.requireNonNull(open);
        Objects.requireNonNull(high);
        Objects.requireNonNull(low);
        Objects.requireNonNull(close);
        Objects.requireNonNull(volume);
        if (high.compareTo(low) < 0 || high.compareTo(open) < 0 || high.compareTo(close) < 0) {
            throw new IllegalArgumentException("high must contain open, low and close");
        }
        if (low.compareTo(open) > 0 || low.compareTo(close) > 0) {
            throw new IllegalArgumentException("low must contain open and close");
        }
        if (volume.signum() < 0) throw new IllegalArgumentException("volume must be non-negative");
    }
}
