package com.company.gitea.provider;

import com.company.gitea.model.ActionsConnection;
import com.company.gitea.model.ProviderType;
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
