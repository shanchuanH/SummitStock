package com.example.portfolio.market.provider;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public final class ProviderExecutor {
    private final Clock clock;
    private final Sleeper sleeper;
    private final int maxAttempts;
    private final Duration retryDelay;
    private final Duration minimumInterval;
    private final AtomicReference<Instant> lastCall = new AtomicReference<>(Instant.MIN);

    public ProviderExecutor(
            Clock clock, Sleeper sleeper, int maxAttempts, Duration retryDelay, Duration minimumInterval) {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
        this.clock = Objects.requireNonNull(clock);
        this.sleeper = Objects.requireNonNull(sleeper);
        this.maxAttempts = maxAttempts;
        this.retryDelay = retryDelay;
        this.minimumInterval = minimumInterval;
    }

    public <T> T execute(Supplier<T> call) {
        ProviderCallException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            rateLimit();
            try {
                return call.get();
            } catch (ProviderCallException exception) {
                last = exception;
                if (!exception.retryable() || attempt == maxAttempts) throw exception;
                sleeper.sleep(retryDelay.multipliedBy(attempt));
            }
        }
        throw Objects.requireNonNull(last);
    }

    private void rateLimit() {
        var now = clock.instant();
        var previous = lastCall.get();
        var earliest = previous.equals(Instant.MIN) ? now : previous.plus(minimumInterval);
        if (now.isBefore(earliest)) sleeper.sleep(Duration.between(now, earliest));
        lastCall.set(clock.instant());
    }

    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration);
    }
}
