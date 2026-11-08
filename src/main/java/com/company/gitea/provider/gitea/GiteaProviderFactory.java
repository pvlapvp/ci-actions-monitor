package com.company.gitea.provider.gitea;

import com.company.gitea.api.ActionsApi;
import com.company.gitea.api.GiteaApiClient;
import com.company.gitea.model.ActionsConnection;
import com.company.gitea.model.ProviderType;
import com.company.gitea.provider.ActionsProvider;
import com.company.gitea.provider.ActionsProviderFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Gitea factory. Keeps Gitea-specific URL normalization out of the UI and the
 * provider-neutral layer.
 */
public final class GiteaProviderFactory implements ActionsProviderFactory {

    @Override
    public @NotNull ProviderType providerType() {
        return ProviderType.GITEA;
    }

    @Override
    public @NotNull String normalizeServerUrl(@NotNull String url) {
        return GiteaApiClient.normalizeServerUrl(url);
    }

    @Override
    public @NotNull String resolveApiUrl(@NotNull ActionsConnection connection) {
        String baseUrl = normalizeServerUrl(connection.serverUrl());
        return baseUrl.isBlank() ? "" : baseUrl + "/api/v1";
    }

    @Override
    public @NotNull ActionsProvider create(@NotNull ActionsConnection connection,
                                           @NotNull String token) {
        String normalizedToken = token == null ? "" : token.trim();
        GiteaApiClient client = new GiteaApiClient(
                normalizeServerUrl(connection.serverUrl()),
                normalizedToken,
                connection.safeTimeoutSeconds()
        );
        return new GiteaActionsProvider(connection, new ActionsApi(client));
    }
}
