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
    private final ProviderExecutionPolicy policy;
    private final Duration timeout;
    private final ObjectMapper json;

    ProviderHttpClient(HttpClient client, ProviderExecutionPolicy policy, Duration timeout, ObjectMapper json) {
        this.client = client;
        this.policy = policy;
        this.timeout = timeout;
        this.json = json;
    }

    public Payload get(URI uri, Map<String, String> headers) {
        return policy.execute(operation(uri), context(uri), () -> send(uri, headers), Payload::raw);
    }

    public String getText(URI uri, Map<String, String> headers) {
        return policy.execute(operation(uri), context(uri), () -> sendProviderText(uri, headers), value -> value);
    }

    private String sendProviderText(URI uri, Map<String, String> headers) {
        var body = sendText(uri, headers);
        if (!body.stripLeading().startsWith("{")) return body;
        try {
            var candidate = json.readTree(body);
            if (candidate != null) ProviderPayloads.rejectProviderError(candidate);
            return body;
        } catch (JacksonException exception) {
            throw new ProviderCallException(
                    ProviderErrorCode.PROVIDER_MALFORMED, "Provider returned malformed text payload", 200, false);
        }
    }

    private Payload send(URI uri, Map<String, String> headers) {
        var body = sendText(uri, headers);
        try {
            JsonNode jsonBody = json.readTree(body);
            if (jsonBody == null || jsonBody.isNull()) {
                throw new ProviderCallException(
                        ProviderErrorCode.PROVIDER_MALFORMED, "Provider returned an empty payload", 200, false);
            }
            ProviderPayloads.rejectProviderError(jsonBody);
            return new Payload(body, jsonBody);
        } catch (JacksonException exception) {
            throw new ProviderCallException(
                    ProviderErrorCode.PROVIDER_MALFORMED, "Provider returned malformed JSON", 200, false);
        }
    }

    private String sendText(URI uri, Map<String, String> headers) {
        var builder = HttpRequest.newBuilder(uri).GET().timeout(timeout).header("Accept", "application/json");
        headers.forEach(builder::header);
        try {
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            var status = response.statusCode();
            if (status < 200 || status >= 300) {
                boolean retryable = status == 408 || status == 429 || status >= 500;
                var code = status == 401 || status == 403
                        ? ProviderErrorCode.PROVIDER_AUTH
                        : status == 429
                                ? ProviderErrorCode.PROVIDER_RATE_LIMIT
                                : ProviderErrorCode.PROVIDER_UNAVAILABLE;
                throw new ProviderCallException(code, "Provider returned HTTP " + status, status, retryable);
            }
            if (response.body() == null || response.body().isBlank()) {
                throw new ProviderCallException(
                        ProviderErrorCode.PROVIDER_MALFORMED, "Provider returned an empty payload", status, false);
            }
            return response.body();
        } catch (HttpTimeoutException exception) {
            throw new ProviderCallException(
                    ProviderErrorCode.PROVIDER_TIMEOUT, "Provider request timed out", null, true);
        } catch (IOException exception) {
            throw new ProviderCallException(
                    ProviderErrorCode.PROVIDER_UNAVAILABLE,
                    "Provider request failed: " + exception.getMessage(),
                    null,
                    true);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ProviderCallException(
                    ProviderErrorCode.PROVIDER_UNAVAILABLE, "Provider request interrupted", null, false);
        }
    }

    private static String operation(URI uri) {
        if (uri.getRawQuery() != null) {
            for (var part : uri.getRawQuery().split("&")) {
                if (part.regionMatches(true, 0, "function=", 0, 9))
                    return part.substring(9).toLowerCase();
            }
        }
        return "http-get";
    }

    private static String context(URI uri) {
        return "{\"host\":\"" + uri.getHost() + "\",\"path\":\"" + uri.getPath() + "\"}";
    }

    public record Payload(String raw, JsonNode json) {}
}
