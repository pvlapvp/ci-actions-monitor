package ru.pvlapvp.ciactionsmonitor.provider;

import ru.pvlapvp.ciactionsmonitor.model.ProviderType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Result of a successful provider connection check.
 *
 * @param version provider-reported version or a readable server identifier
 * @param account authenticated account, if the provider reports one
 */
public record ConnectionInfo(
        @NotNull ProviderType providerType,
        @NotNull String version,
        @NotNull String apiUrl,
        @Nullable String account) {
}
