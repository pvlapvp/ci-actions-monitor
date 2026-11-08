package com.company.gitea.service;

import com.company.gitea.model.ActionsConnection;
import com.company.gitea.model.ProviderType;
import com.company.gitea.provider.ActionsProvider;
import com.company.gitea.provider.ProviderRegistry;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application-level cache of {@link ActionsProvider} instances, one per connection.
 * <p>
 * The cache is invalidated when the URL/type/timeout changes or when settings explicitly
 * call {@link #invalidate(String)}. Tokens are read from PasswordSafe only on a cache
 * miss, so expanding the Services tree does not touch the credential store for every node.
 */
@Service(Service.Level.APP)
public final class GiteaConnectionService {

    private final Map<String, CachedProvider> cache = new ConcurrentHashMap<>();

    public static @NotNull GiteaConnectionService getInstance() {
        return ApplicationManager.getApplication().getService(GiteaConnectionService.class);
    }

    public @NotNull ActionsProvider getProvider(@NotNull ActionsConnection connection) throws IOException {
        requireConfigured(connection);

        CachedProvider current = cache.get(connection.id());
        if (current != null && current.matches(connection)) {
            return current.provider;
        }

        String token = GiteaCredentialService.getInstance().getTokenBlocking(connection.id());
        if (token.isBlank()) {
            throw new IOException("No access token is stored for connection \"" + connection.displayName()
                    + "\". Open Settings | Tools | Gitea Actions Monitor and set a token.");
        }

        ActionsProvider provider = ProviderRegistry.getInstance().create(connection, token);
        cache.put(connection.id(), new CachedProvider(
                connection.providerType(),
                connection.serverUrl().trim(),
                connection.apiUrl(),
                connection.safeTimeoutSeconds(),
                provider
        ));
        return provider;
    }

    /**
     * Tests a URL/token pair. If {@code token} is blank, the stored token is used.
     * Blocking; call from a background thread.
     */
    public @NotNull String testConnection(@NotNull ActionsConnection connection,
                                          @Nullable String token) throws IOException {
        requireConfigured(connection);
        String effectiveToken = token == null ? "" : token.trim();
        if (effectiveToken.isEmpty()) {
            effectiveToken = GiteaCredentialService.getInstance().getTokenBlocking(connection.id());
        }
        if (effectiveToken.isEmpty()) {
            throw new IOException("Access token is empty");
        }
        ActionsProvider provider = ProviderRegistry.getInstance().create(connection, effectiveToken);
        return provider.testConnection().version();
    }

    public void invalidate(@NotNull String connectionId) {
        cache.remove(connectionId);
    }

    public void invalidateAll() {
        cache.clear();
    }

    private static void requireConfigured(@NotNull ActionsConnection connection) throws IOException {
        if (!connection.isConfigured()) {
            throw new IOException("CI/CD connection is not configured");
        }
    }

    /**
     * Deliberately has no {@code toString()} that could expose credentials.
     * A token change is handled by {@link #invalidate(String)} from settings.
     */
    private static final class CachedProvider {
        private final ProviderType providerType;
        private final String serverUrl;
        private final String apiUrl;
        private final int timeoutSeconds;
        private final ActionsProvider provider;

        private CachedProvider(ProviderType providerType,
                               String serverUrl,
                               String apiUrl,
                               int timeoutSeconds,
                               ActionsProvider provider) {
            this.providerType = providerType;
            this.serverUrl = serverUrl;
            this.apiUrl = apiUrl;
            this.timeoutSeconds = timeoutSeconds;
            this.provider = provider;
        }

        private boolean matches(@NotNull ActionsConnection connection) {
            return providerType == connection.providerType()
                    && Objects.equals(serverUrl, connection.serverUrl().trim())
                    && Objects.equals(apiUrl, connection.apiUrl())
                    && timeoutSeconds == connection.safeTimeoutSeconds();
        }
    }
}
