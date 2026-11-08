package ru.pvlapvp.ciactionsmonitor.provider.github;

import ru.pvlapvp.ciactionsmonitor.model.ActionsConnection;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.util.Locale;

/**
 * Owns GitHub URL rules for GitHub.com and GitHub Enterprise Server.
 * <p>
 * GHE.com is intentionally reserved for a later stage; the client itself only sees
 * a resolved {@link GitHubEndpoint} and will not need changes when that branch is added.
 */
public final class GitHubEndpointResolver {

    private static final String API_VERSION = "2022-11-28";

    public @NotNull String normalizeServerUrl(@NotNull String url) {
        String value = url == null ? "" : url.trim();
        if (value.isEmpty()) {
            return "";
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) {
            value = "https://" + value;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.endsWith("/api/v3")) {
            value = value.substring(0, value.length() - "/api/v3".length());
        }
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (host != null && host.equalsIgnoreCase("api.github.com")) {
                return "https://github.com";
            }
        } catch (IllegalArgumentException ignored) {
            // Let the provider validation report the malformed URL.
        }
        return value;
    }

    public @NotNull GitHubEndpoint resolve(@NotNull ActionsConnection connection) {
        String serverUrl = normalizeServerUrl(connection.serverUrl());
        if (serverUrl.isBlank()) {
            throw new IllegalArgumentException("GitHub server URL is empty");
        }

        URI webBase = URI.create(serverUrl);
        String host = webBase.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("GitHub server URL has no host: " + serverUrl);
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.equals("github.com") || normalizedHost.equals("www.github.com")) {
            URI apiBase = resolveApiUri(connection, "https://api.github.com");
            return new GitHubEndpoint(
                    GitHubDeployment.GITHUB_COM,
                    URI.create("https://github.com"),
                    apiBase,
                    API_VERSION,
                    "/rate_limit"
            );
        }

        if (normalizedHost.endsWith(".ghe.com")) {
            // Reserved branch. The rest of the client already works with GitHubEndpoint.
            throw new IllegalArgumentException("GitHub Enterprise Cloud (GHE.com) is not supported yet");
        }

        URI apiBase = resolveApiUri(connection, serverUrl + "/api/v3");
        return new GitHubEndpoint(
                GitHubDeployment.GHES,
                webBase,
                apiBase,
                null,
                "/meta"
        );
    }

    public @NotNull String resolveApiUrl(@NotNull ActionsConnection connection) {
        return resolve(connection).apiBaseUri().toString();
    }

    private static @NotNull URI resolveApiUri(@NotNull ActionsConnection connection, @NotNull String fallback) {
        String override = connection.apiUrl();
        String value = override == null || override.isBlank() ? fallback : override.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        try {
            return URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid GitHub API URL: " + value, e);
        }
    }
}
