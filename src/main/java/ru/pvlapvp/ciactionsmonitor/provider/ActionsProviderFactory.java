package ru.pvlapvp.ciactionsmonitor.provider;

import ru.pvlapvp.ciactionsmonitor.model.ActionsConnection;
import ru.pvlapvp.ciactionsmonitor.model.ProviderType;
import org.jetbrains.annotations.NotNull;

/**
 * Creates provider instances and owns all provider-specific URL rules.
 */
public interface ActionsProviderFactory {

    @NotNull ProviderType providerType();

    @NotNull String normalizeServerUrl(@NotNull String url);

    @NotNull String resolveApiUrl(@NotNull ActionsConnection connection);

    @NotNull ActionsProvider create(@NotNull ActionsConnection connection,
                                    @NotNull String token);
}
