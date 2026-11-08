package ru.pvlapvp.ciactionsmonitor.provider.gitea;

import ru.pvlapvp.ciactionsmonitor.api.GiteaActionsApi;
import ru.pvlapvp.ciactionsmonitor.api.GiteaApiClient;
import ru.pvlapvp.ciactionsmonitor.model.ActionsConnection;
import ru.pvlapvp.ciactionsmonitor.model.ProviderType;
import ru.pvlapvp.ciactionsmonitor.provider.ActionsProvider;
import ru.pvlapvp.ciactionsmonitor.provider.ActionsProviderFactory;
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
        String override = connection.apiUrl();
        if (override != null && !override.isBlank()) {
            return override.trim();
        }
        String baseUrl = normalizeServerUrl(connection.serverUrl());
        return baseUrl.isBlank() ? "" : baseUrl + "/api/v1";
    }

    @Override
    public @NotNull ActionsProvider create(@NotNull ActionsConnection connection,
                                           @NotNull String token) {
        String normalizedToken = token == null ? "" : token.trim();
        GiteaApiClient client = new GiteaApiClient(
                normalizeServerUrl(connection.serverUrl()),
                connection.apiUrl(),
                normalizedToken,
                connection.safeTimeoutSeconds()
        );
        return new GiteaActionsProvider(connection, new GiteaActionsApi(client));
    }
}
