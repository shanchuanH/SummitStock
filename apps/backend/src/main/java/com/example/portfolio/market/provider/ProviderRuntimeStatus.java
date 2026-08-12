package com.example.portfolio.market.provider;

import java.util.List;

public record ProviderRuntimeStatus(String status, List<String> unavailableProviders) {
    public ProviderRuntimeStatus {
        unavailableProviders = List.copyOf(unavailableProviders);
    }

    public static ProviderRuntimeStatus complete() {
        return new ProviderRuntimeStatus("COMPLETE", List.of());
    }
}
