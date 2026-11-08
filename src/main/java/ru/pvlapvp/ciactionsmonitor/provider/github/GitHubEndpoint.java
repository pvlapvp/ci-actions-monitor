package ru.pvlapvp.ciactionsmonitor.provider.github;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;

/**
 * Resolved GitHub endpoints. The HTTP client consumes only this object, so
 * deployment-specific URL rules stay in {@link GitHubEndpointResolver}.
 */
public record GitHubEndpoint(
        @NotNull GitHubDeployment deployment,
        @NotNull URI webBaseUri,
        @NotNull URI apiBaseUri,
        @Nullable String apiVersion,
        @NotNull String connectionProbePath) {
}
