package com.company.gitea.service;

import com.company.gitea.model.ActionsConnection;
import com.company.gitea.model.GiteaServer;
import com.company.gitea.provider.ActionsProvider;
import com.company.gitea.provider.ProviderRegistry;
import com.company.gitea.provider.gitea.GiteaProviderFactory;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application-level cache of {@link ActionsProvider} instances, one per server.
 * <p>
 * The cache is invalidated when the URL/timeout changes or when settings explicitly
 * call {@link #invalidate(String)}. Tokens are read from PasswordSafe only on a cache
 * miss, so expanding the Services tree does not touch the credential store for every node.
 */
@Service(Service.Level.APP)
public final class GiteaConnectionService {

    private final Map<String, CachedProvider> cache = new ConcurrentHashMap<>();

    public static @NotNull GiteaConnectionService getInstance() {
        return ApplicationManager.getApplication().getService(GiteaConnectionService.class);
    }

    public @NotNull ActionsProvider getProvider(@NotNull GiteaServer server) throws IOException {
        requireConfigured(server);

        CachedProvider current = cache.get(server.id());
        if (current != null && current.matches(server)) {
            return current.provider;
        }

        String token = GiteaCredentialService.getInstance().getTokenBlocking(server.id());
        if (token.isBlank()) {
            throw new IOException("No access token is stored for server \"" + server.displayName()
                    + "\". Open Settings | Tools | Gitea Actions Monitor and set a token.");
        }

        ActionsConnection connection = GiteaProviderFactory.toConnection(server);
        ActionsProvider provider = ProviderRegistry.getInstance().create(connection, token);
        cache.put(server.id(), new CachedProvider(
                server.normalizedUrl(),
                server.safeTimeoutSeconds(),
                provider
        ));
        return provider;
    }

    /**
     * Tests a URL/token pair. If {@code token} is blank, the stored token is used.
     * Blocking; call from a background thread.
     */
    public @NotNull String testConnection(@NotNull GiteaServer server, @Nullable String token) throws IOException {
        requireConfigured(server);
        String effectiveToken = token == null ? "" : token.trim();
        if (effectiveToken.isEmpty()) {
            effectiveToken = GiteaCredentialService.getInstance().getTokenBlocking(server.id());
        }
        if (effectiveToken.isEmpty()) {
            throw new IOException("Access token is empty");
        }
        ActionsConnection connection = GiteaProviderFactory.toConnection(server);
        ActionsProvider provider = ProviderRegistry.getInstance().create(connection, effectiveToken);
        return provider.testConnection().version();
    }

    public void invalidate(@NotNull String serverId) {
        cache.remove(serverId);
    }

    public void invalidateAll() {
        cache.clear();
    }

    private static void requireConfigured(@NotNull GiteaServer server) throws IOException {
        if (!server.isConfigured()) {
            throw new IOException("Gitea server is not configured");
        }
    }

    /**
     * Deliberately has no {@code toString()} that could expose credentials.
     * A token change is handled by {@link #invalidate(String)} from settings.
     */
    private static final class CachedProvider {
        private final String normalizedUrl;
        private final int timeoutSeconds;
        private final ActionsProvider provider;

        private CachedProvider(String normalizedUrl, int timeoutSeconds, ActionsProvider provider) {
            this.normalizedUrl = normalizedUrl;
            this.timeoutSeconds = timeoutSeconds;
            this.provider = provider;
        }

        private boolean matches(@NotNull GiteaServer server) {
            return Objects.equals(normalizedUrl, server.normalizedUrl())
                    && timeoutSeconds == server.safeTimeoutSeconds();
        }
    }
}
