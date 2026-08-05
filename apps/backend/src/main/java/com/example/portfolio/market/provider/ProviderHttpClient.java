package com.example.portfolio.market.provider;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

public final class ProviderHttpClient {
    private final HttpClient client;
    private final ProviderExecutor executor;
    private final Duration timeout;
    private final ObjectMapper json;

    ProviderHttpClient(HttpClient client, ProviderExecutor executor, Duration timeout, ObjectMapper json) {
        this.client = client;
        this.executor = executor;
        this.timeout = timeout;
        this.json = json;
    }

    public Payload get(URI uri, Map<String, String> headers) {
        return executor.execute(() -> send(uri, headers));
    }

    private Payload send(URI uri, Map<String, String> headers) {
        var builder = HttpRequest.newBuilder(uri).GET().timeout(timeout).header("Accept", "application/json");
        headers.forEach(builder::header);
        try {
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            var status = response.statusCode();
            if (status < 200 || status >= 300) {
                boolean retryable = status == 408 || status == 429 || status >= 500;
                throw new ProviderCallException(
                        status == 429 ? "PROVIDER_RATE_LIMIT" : "PROVIDER_HTTP_ERROR",
                        "Provider returned HTTP " + status,
                        status,
                        retryable);
            }
            if (response.body() == null || response.body().isBlank()) {
                throw new ProviderCallException("PROVIDER_EMPTY", "Provider returned an empty payload", status, false);
            }
            try {
                JsonNode body = json.readTree(response.body());
                if (body == null || body.isNull()) {
                    throw new ProviderCallException(
                            "PROVIDER_EMPTY", "Provider returned an empty payload", status, false);
                }
                ProviderPayloads.rejectProviderError(body);
                return new Payload(response.body(), body);
            } catch (JacksonException exception) {
                throw new ProviderCallException(
                        "PROVIDER_MALFORMED", "Provider returned malformed JSON", status, false);
            }
        } catch (HttpTimeoutException exception) {
            throw new ProviderCallException("PROVIDER_TIMEOUT", "Provider request timed out", null, true);
        } catch (IOException exception) {
            throw new ProviderCallException(
                    "PROVIDER_IO", "Provider request failed: " + exception.getMessage(), null, true);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProviderCallException("PROVIDER_INTERRUPTED", "Provider request interrupted", null, false);
        }
    }

    public record Payload(String raw, JsonNode json) {}
}
