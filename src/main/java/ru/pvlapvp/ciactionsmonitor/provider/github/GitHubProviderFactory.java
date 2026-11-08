package ru.pvlapvp.ciactionsmonitor.provider.github;

import ru.pvlapvp.ciactionsmonitor.model.ActionsConnection;
import ru.pvlapvp.ciactionsmonitor.model.ProviderType;
import ru.pvlapvp.ciactionsmonitor.provider.ActionsProvider;
import ru.pvlapvp.ciactionsmonitor.provider.ActionsProviderFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Creates GitHub providers. All deployment-specific URL logic lives in
 * {@link GitHubEndpointResolver}, not in {@link GitHubApiClient}.
 */
public final class GitHubProviderFactory implements ActionsProviderFactory {

    private final GitHubEndpointResolver endpointResolver = new GitHubEndpointResolver();

    @Override
    public @NotNull ProviderType providerType() {
        return ProviderType.GITHUB;
    }

    @Override
    public @NotNull String normalizeServerUrl(@NotNull String url) {
        return endpointResolver.normalizeServerUrl(url);
    }

    @Override
    public @NotNull String resolveApiUrl(@NotNull ActionsConnection connection) {
        return endpointResolver.resolveApiUrl(connection);
    }

    @Override
    public @NotNull ActionsProvider create(@NotNull ActionsConnection connection,
                                           @NotNull String token) {
        GitHubEndpoint endpoint = endpointResolver.resolve(connection);
        GitHubApiClient client = new GitHubApiClient(endpoint, token, connection.safeTimeoutSeconds());
        GitHubActionsApi api = new GitHubActionsApi(client, endpoint);
        return new GitHubActionsProvider(connection, api);
    }
}
