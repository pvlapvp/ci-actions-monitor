package com.company.gitea.model;

import com.company.gitea.api.GiteaApiClient;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Non-secret configuration of one Gitea server.
 * <p>
 * The access token is <b>not</b> stored here: it lives in IntelliJ
 * {@code PasswordSafe} and is bound to {@link #id()}.
 *
 * @param id                    stable server identifier (UUID), used as the credential account
 * @param name                  user-facing name
 * @param url                   server URL; may contain a sub-path
 * @param requestTimeoutSeconds HTTP timeout in seconds
 * @param repositories          configured repositories in {@code owner/name} form; empty means
 *                              "all repositories accessible to the token"
 */
public record GiteaServer(
        @NotNull String id,
        @NotNull String name,
        @NotNull String url,
        int requestTimeoutSeconds,
        @NotNull List<String> repositories) {

    public GiteaServer {
        repositories = repositories == null ? List.of() : List.copyOf(repositories);
    }

    public @NotNull String displayName() {
        if (name != null && !name.isBlank()) {
            return name;
        }
        String normalized = normalizedUrl();
        return normalized.isBlank() ? "Gitea" : normalized;
    }

    public @NotNull String normalizedUrl() {
        return GiteaApiClient.normalizeServerUrl(url);
    }

    public int safeTimeoutSeconds() {
        return requestTimeoutSeconds <= 0 ? 30 : Math.max(5, requestTimeoutSeconds);
    }

    public boolean isConfigured() {
        return !id.isBlank() && !normalizedUrl().isBlank();
    }
}
