package ru.pvlapvp.ciactionsmonitor.provider;

/**
 * Provider limits that affect how the monitoring layer paginates requests.
 */
public record ProviderCapabilities(int maxPageSize) {

    public ProviderCapabilities {
        maxPageSize = Math.max(1, maxPageSize);
    }
}
