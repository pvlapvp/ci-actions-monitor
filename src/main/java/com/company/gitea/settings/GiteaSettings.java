package com.company.gitea.settings;

import com.company.gitea.api.GiteaApiClient;
import com.company.gitea.model.GiteaServer;
import com.company.gitea.service.GiteaCredentialService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Application-level persistent settings.
 * <p>
 * Only non-secret data is stored here: servers (id, name, URL, timeout) and the
 * list of repositories. Access tokens live in {@link GiteaCredentialService}
 * (IntelliJ PasswordSafe) and are keyed by the stable server id.
 * <p>
 * The old single-server fields ({@code serverUrl}, {@code token}) are kept only for
 * migration. The legacy token is removed from persistent state immediately and handed
 * to PasswordSafe in the background; it must never be written back to XML.
 */
@State(name = "GiteaSettings", storages = @Storage("gitea-actions-monitor.xml"))
public final class GiteaSettings implements PersistentStateComponent<GiteaSettings.State> {

    private static final Logger LOG = Logger.getInstance(GiteaSettings.class);
    private static final Object LOCK = new Object();

    public static final class State {
        public List<ServerState> servers = new ArrayList<>();

        // Legacy 1.0 format. Do not write new values here.
        public String serverUrl = "";
        public String token = "";
        public int requestTimeoutSeconds = 30;
    }

    public static final class ServerState {
        public String id = "";
        public String name = "";
        public String url = "";
        public int requestTimeoutSeconds = 30;
        public List<String> repositories = new ArrayList<>();
    }

    private State state = new State();

    public static @NotNull GiteaSettings getInstance() {
        return ApplicationManager.getApplication().getService(GiteaSettings.class);
    }

    @Override
    public State getState() {
        synchronized (LOCK) {
            ensureStateLocked();
            return state;
        }
    }

    @Override
    public void loadState(@NotNull State loadedState) {
        synchronized (LOCK) {
            state = loadedState;
            ensureStateLocked();
            migrateLegacyStateLocked();
        }
    }

    public @NotNull List<GiteaServer> getServers() {
        synchronized (LOCK) {
            ensureStateLocked();
            List<GiteaServer> result = new ArrayList<>(state.servers.size());
            for (ServerState server : state.servers) {
                result.add(toModel(server));
            }
            return List.copyOf(result);
        }
    }

    public @Nullable GiteaServer getServer(@NotNull String serverId) {
        synchronized (LOCK) {
            ensureStateLocked();
            for (ServerState server : state.servers) {
                if (serverId.equals(server.id)) {
                    return toModel(server);
                }
            }
            return null;
        }
    }

    /**
     * Replaces the whole server list. Callers are responsible for credential changes.
     */
    public void setServers(@NotNull List<GiteaServer> servers) {
        synchronized (LOCK) {
            ensureStateLocked();
            List<ServerState> newServers = new ArrayList<>(servers.size());
            for (GiteaServer server : servers) {
                newServers.add(toState(server));
            }
            state.servers = newServers;
        }
    }

    public void addServer(@NotNull GiteaServer server) {
        synchronized (LOCK) {
            ensureStateLocked();
            state.servers.removeIf(existing -> existing.id.equals(server.id()));
            state.servers.add(toState(server));
        }
    }

    public void removeServer(@NotNull String serverId) {
        synchronized (LOCK) {
            ensureStateLocked();
            state.servers.removeIf(server -> serverId.equals(server.id));
        }
    }

    public boolean isConfigured() {
        for (GiteaServer server : getServers()) {
            if (server.isConfigured()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Persists settings to disk. Safe to call on the EDT.
     */
    public static void saveNow() {
        try {
            ApplicationManager.getApplication().saveSettings();
        } catch (RuntimeException e) {
            LOG.warn("Cannot save Gitea plugin settings", e);
        }
    }

    private void ensureStateLocked() {
        if (state == null) {
            state = new State();
        }
        if (state.servers == null) {
            state.servers = new ArrayList<>();
        }
        for (ServerState server : state.servers) {
            if (server.id == null || server.id.isBlank()) {
                server.id = UUID.randomUUID().toString();
            }
            if (server.repositories == null) {
                server.repositories = new ArrayList<>();
            }
        }
    }

    /**
     * Migrates the old single-server format. The legacy token is removed from
     * persistent state immediately and passed to PasswordSafe in the background;
     * a plaintext token must never remain in the XML even if the credential write
     * is slow or fails.
     */
    private void migrateLegacyStateLocked() {
        String legacyUrl = state.serverUrl == null ? "" : state.serverUrl.trim();
        String legacyToken = state.token == null ? "" : state.token.trim();
        if (legacyUrl.isEmpty() && legacyToken.isEmpty()) {
            return;
        }

        String installedServerId = null;
        if (!legacyUrl.isEmpty()) {
            String normalizedUrl = GiteaApiClient.normalizeServerUrl(legacyUrl);
            for (ServerState server : state.servers) {
                if (normalizedUrl.equals(GiteaApiClient.normalizeServerUrl(server.url))) {
                    installedServerId = server.id;
                    break;
                }
            }
            if (installedServerId == null) {
                ServerState server = new ServerState();
                server.id = UUID.randomUUID().toString();
                server.name = hostName(normalizedUrl);
                server.url = normalizedUrl;
                server.requestTimeoutSeconds = state.requestTimeoutSeconds <= 0 ? 30 : state.requestTimeoutSeconds;
                state.servers.add(server);
                installedServerId = server.id;
            }
            state.serverUrl = "";
        }

        if (legacyToken.isEmpty()) {
            state.token = "";
            return;
        }
        if (installedServerId == null) {
            // Cannot bind a token to a server without a URL. Do not keep it in plaintext.
            state.token = "";
            LOG.warn("Discarded a legacy Gitea token because no server URL was configured");
            return;
        }

        final String serverId = installedServerId;
        final String tokenToMigrate = legacyToken;

        // Never keep the legacy secret in persistent state. The token only lives in
        // this transient local variable until PasswordSafe accepts it.
        state.token = "";
        state.serverUrl = "";

        GiteaCredentialService.getInstance().setToken(serverId, tokenToMigrate).whenComplete((unused, error) -> {
            if (error != null) {
                LOG.warn("Failed to migrate Gitea token to PasswordSafe for server " + serverId, error);
                return;
            }
            if (!ApplicationManager.getApplication().isDisposed()) {
                ApplicationManager.getApplication().invokeLater(GiteaSettings::saveNow);
            }
        });
    }

    private static @NotNull GiteaServer toModel(@NotNull ServerState server) {
        String url = server.url == null ? "" : server.url.trim();
        String name = server.name == null || server.name.isBlank() ? hostName(url) : server.name.trim();
        List<String> repositories = sanitizeRepositories(server.repositories);
        return new GiteaServer(
                server.id == null ? "" : server.id,
                name,
                url,
                server.requestTimeoutSeconds,
                repositories
        );
    }

    private static @NotNull ServerState toState(@NotNull GiteaServer server) {
        ServerState state = new ServerState();
        state.id = server.id();
        state.name = server.name();
        state.url = server.normalizedUrl();
        state.requestTimeoutSeconds = server.safeTimeoutSeconds();
        state.repositories = new ArrayList<>(server.repositories());
        return state;
    }

    private static @NotNull List<String> sanitizeRepositories(@Nullable List<String> repositories) {
        if (repositories == null || repositories.isEmpty()) {
            return List.of();
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String repository : repositories) {
            if (repository == null) {
                continue;
            }
            String value = repository.trim();
            if (!value.isEmpty()) {
                unique.putIfAbsent(value.toLowerCase(Locale.ROOT), value);
            }
        }
        return List.copyOf(unique.values());
    }

    private static @NotNull String hostName(@NotNull String url) {
        String normalized = GiteaApiClient.normalizeServerUrl(url);
        if (normalized.isBlank()) {
            return "Gitea";
        }
        try {
            String host = URI.create(normalized).getHost();
            return host == null || host.isBlank() ? normalized : host;
        } catch (IllegalArgumentException e) {
            return normalized;
        }
    }
}
