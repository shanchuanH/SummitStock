package com.example.portfolio.market.provider;

import com.example.portfolio.market.persistence.ProviderRequestJournal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ProviderExecutionPolicy {
    private final Clock clock;
    private final Sleeper sleeper;
    private final int maxAttempts;
    private final Duration retryDelay;
    private final Duration minimumInterval;
    private final ProviderRequestJournal journal;
    private final String providerId;
    private final AtomicReference<Instant> lastCall = new AtomicReference<>(Instant.MIN);

    public ProviderExecutionPolicy(
            Clock clock, Sleeper sleeper, int maxAttempts, Duration retryDelay, Duration minimumInterval) {
        this(clock, sleeper, maxAttempts, retryDelay, minimumInterval, null, null);
    }

    ProviderExecutionPolicy(
            Clock clock,
            Sleeper sleeper,
            int maxAttempts,
            Duration retryDelay,
            Duration minimumInterval,
            ProviderRequestJournal journal,
            String providerId) {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
        this.clock = Objects.requireNonNull(clock);
        this.sleeper = Objects.requireNonNull(sleeper);
        this.maxAttempts = maxAttempts;
        this.retryDelay = Objects.requireNonNull(retryDelay);
        this.minimumInterval = Objects.requireNonNull(minimumInterval);
        this.journal = journal;
        this.providerId = providerId;
    }

    public <T> T execute(Supplier<T> call) {
        return execute(null, null, call, ignored -> null);
    }

    public <T> T execute(String operation, String requestContext, Supplier<T> call, Function<T, String> responseBody) {
        var requestKey = startJournal(operation, requestContext);
        ProviderCallException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            rateLimit();
            try {
                var result = call.get();
                succeedJournal(requestKey, responseBody.apply(result));
                return result;
            } catch (ProviderCallException exception) {
                last = exception;
                if (!exception.retryable() || attempt == maxAttempts) {
                    failJournal(requestKey, exception);
                    throw exception;
                }
                sleeper.sleep(retryDelay.multipliedBy(attempt));
            }
        }
        throw Objects.requireNonNull(last);
    }

    private String startJournal(String operation, String requestContext) {
        if (journal == null) return null;
        var key = sha256(providerId + ":" + operation + ":" + UUID.randomUUID());
        journal.start(key, providerId, operation, requestContext);
        return key;
    }

    private void succeedJournal(String requestKey, String body) {
        if (requestKey == null) return;
        journal.succeed(
                requestKey, clock.instant(), sha256(body == null ? "" : body), "HEALTHY", "{\"transport\":\"HTTP\"}");
    }

    private void failJournal(String requestKey, ProviderCallException exception) {
        if (requestKey == null) return;
        journal.fail(requestKey, exception.httpStatus(), exception.code(), exception.getMessage());
    }

    private synchronized void rateLimit() {
        var now = clock.instant();
        var previous = lastCall.get();
        var earliest = previous.equals(Instant.MIN) ? now : previous.plus(minimumInterval);
        if (now.isBefore(earliest)) sleeper.sleep(Duration.between(now, earliest));
        lastCall.set(clock.instant());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    @FunctionalInterface
    public interface Sleeper {
        void sleep(Duration duration);
    }
}
