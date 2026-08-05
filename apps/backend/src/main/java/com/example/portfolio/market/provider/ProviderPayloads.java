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
                boolean retryable = !field.equals("Error Message");
                throw new ProviderCallException(
                        retryable ? "PROVIDER_RATE_LIMIT" : "PROVIDER_REJECTED", value.asText(), 200, retryable);
            }
        }
    }
}
