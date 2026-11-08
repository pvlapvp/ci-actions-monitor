package ru.pvlapvp.ciactionsmonitor.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.List;

/**
 * Non-secret configuration of one CI/CD connection.
 * <p>
 * The access token is not stored here: it lives in IntelliJ PasswordSafe and is
 * bound to {@link #id()}.
 *
 * @param id                    stable connection identifier (UUID), used as the credential account
 * @param providerType          Gitea or GitHub
 * @param name                  user-facing name
 * @param serverUrl             web base URL, for example {@code https://gitea.example.com}
 * @param apiUrl                optional API URL override; {@code null} means "derive from serverUrl"
 * @param requestTimeoutSeconds HTTP timeout in seconds
 * @param repositories          configured repositories in {@code owner/name} form; empty means
 *                              "all repositories accessible to the token"
 */
public record ActionsConnection(
        @NotNull String id,
        @NotNull ProviderType providerType,
        @NotNull String name,
        @NotNull String serverUrl,
        @Nullable String apiUrl,
        int requestTimeoutSeconds,
        @NotNull List<String> repositories) {

    public ActionsConnection {
        repositories = repositories == null ? List.of() : List.copyOf(repositories);
    }

    public @NotNull String displayName() {
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        String normalized = serverUrl == null ? "" : serverUrl.trim();
        if (normalized.isBlank()) {
            return providerType.displayName();
        }
        try {
            String host = URI.create(normalized).getHost();
            return host == null || host.isBlank() ? normalized : host;
        } catch (IllegalArgumentException e) {
            return normalized;
        }
    }

    public int safeTimeoutSeconds() {
        return requestTimeoutSeconds <= 0 ? 30 : Math.max(5, requestTimeoutSeconds);
    }

    public boolean isConfigured() {
        return !id.isBlank() && serverUrl != null && !serverUrl.isBlank();
    }
}
