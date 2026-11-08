package com.company.gitea.provider.github;

import com.company.gitea.model.ActionsConnection;
import com.company.gitea.model.ProviderType;
import com.company.gitea.provider.ActionsProvider;
import com.company.gitea.provider.ActionsProviderFactory;
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
