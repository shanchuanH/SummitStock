package com.example.portfolio.market.provider;

public class ProviderQuotaDeferredException extends RuntimeException {
    public ProviderQuotaDeferredException(String provider, String operation, String reason) {
        super("Provider work deferred: provider=" + provider + ", operation=" + operation + ", reason=" + reason);
    }
}
