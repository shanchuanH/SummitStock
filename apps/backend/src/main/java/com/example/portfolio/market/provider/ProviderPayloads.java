package com.example.portfolio.market.provider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import tools.jackson.databind.JsonNode;

final class ProviderPayloads {
    private ProviderPayloads() {}

    static String sha256(String content) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    static String requiredText(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new ProviderCallException("PROVIDER_MALFORMED", "Missing provider field: " + field, 200, false);
        }
        return value.asText();
    }

    static void rejectProviderError(JsonNode root) {
        for (String field : new String[] {"Note", "Information", "Error Message"}) {
            var value = root.get(field);
            if (value != null && !value.asText().isBlank()) {
                var message = value.asText();
                var normalized = message.toLowerCase(java.util.Locale.ROOT);
                var planLimit = normalized.contains("premium")
                        || normalized.contains("subscription")
                        || normalized.contains("plan");
                var auth = normalized.contains("api key")
                        && (normalized.contains("invalid") || normalized.contains("missing"));
                var code = planLimit
                        ? ProviderErrorCode.PROVIDER_PLAN_LIMIT
                        : auth
                                ? ProviderErrorCode.PROVIDER_AUTH
                                : field.equals("Error Message")
                                        ? ProviderErrorCode.PROVIDER_MALFORMED
                                        : ProviderErrorCode.PROVIDER_RATE_LIMIT;
                boolean retryable = code == ProviderErrorCode.PROVIDER_RATE_LIMIT;
                throw new ProviderCallException(code, message, 200, retryable);
            }
        }
    }
}
