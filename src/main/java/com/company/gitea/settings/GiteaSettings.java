package com.company.gitea.settings;

import com.company.gitea.api.GiteaApiClient;
import com.company.gitea.model.ActionsConnection;
import com.company.gitea.model.ProviderType;
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
 * Only non-secret data is stored here: connections (id, provider type, name, URL,
 * timeout) and the list of repositories. Access tokens live in
 * {@link GiteaCredentialService} (IntelliJ PasswordSafe) and are keyed by the stable
 * connection id.
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

        /**
         * Persisted as a string so future provider types can be read without changing
         * the XML schema. Missing/blank values from old versions mean Gitea.
         */
        public String providerType = "";

        public String name = "";
        public String url = "";

        /**
         * Optional API base URL override. Empty means "derive from the server URL".
         */
        public String apiUrl = "";

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

    public @NotNull List<ActionsConnection> getServers() {
        synchronized (LOCK) {
            ensureStateLocked();
            List<ActionsConnection> result = new ArrayList<>(state.servers.size());
            for (ServerState server : state.servers) {
                result.add(toModel(server));
            }
            return List.copyOf(result);
        }
    }

    public @Nullable ActionsConnection getServer(@NotNull String connectionId) {
        synchronized (LOCK) {
            ensureStateLocked();
            for (ServerState server : state.servers) {
                if (connectionId.equals(server.id)) {
                    return toModel(server);
                }
            }
            return null;
        }
    }

    /**
     * Replaces the whole connection list. Callers are responsible for credential changes.
     */
    public void setServers(@NotNull List<ActionsConnection> connections) {
        synchronized (LOCK) {
            ensureStateLocked();
            List<ServerState> newServers = new ArrayList<>(connections.size());
            for (ActionsConnection connection : connections) {
                newServers.add(toState(connection));
            }
            state.servers = newServers;
        }
    }

    public void addServer(@NotNull ActionsConnection connection) {
        synchronized (LOCK) {
            ensureStateLocked();
            state.servers.removeIf(existing -> existing.id.equals(connection.id()));
            state.servers.add(toState(connection));
        }
    }

    public void removeServer(@NotNull String connectionId) {
        synchronized (LOCK) {
            ensureStateLocked();
            state.servers.removeIf(server -> connectionId.equals(server.id));
        }
    }

    public boolean isConfigured() {
        for (ActionsConnection connection : getServers()) {
            if (connection.isConfigured()) {
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
            LOG.warn("Cannot save CI/CD plugin settings", e);
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
            if (server.url == null) {
                server.url = "";
            }
            if (server.apiUrl == null) {
                server.apiUrl = "";
            }
            server.providerType = parseProviderType(server.providerType).name();
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

        String installedConnectionId = null;
        if (!legacyUrl.isEmpty()) {
            String normalizedUrl = GiteaApiClient.normalizeServerUrl(legacyUrl);
            for (ServerState server : state.servers) {
                if (parseProviderType(server.providerType) == ProviderType.GITEA
                        && normalizedUrl.equals(GiteaApiClient.normalizeServerUrl(server.url))) {
                    installedConnectionId = server.id;
                    break;
                }
            }
            if (installedConnectionId == null) {
                ServerState server = new ServerState();
                server.id = UUID.randomUUID().toString();
                server.providerType = ProviderType.GITEA.name();
                server.name = hostName(ProviderType.GITEA, normalizedUrl);
                server.url = normalizedUrl;
                server.requestTimeoutSeconds = state.requestTimeoutSeconds <= 0 ? 30 : state.requestTimeoutSeconds;
                state.servers.add(server);
                installedConnectionId = server.id;
            }
            state.serverUrl = "";
        }

        if (legacyToken.isEmpty()) {
            state.token = "";
            return;
        }
        if (installedConnectionId == null) {
            // Cannot bind a token to a server without a URL. Do not keep it in plaintext.
            state.token = "";
            LOG.warn("Discarded a legacy Gitea token because no server URL was configured");
            return;
        }

        final String connectionId = installedConnectionId;
        final String tokenToMigrate = legacyToken;

        // Never keep the legacy secret in persistent state. The token only lives in
        // this transient local variable until PasswordSafe accepts it.
        state.token = "";
        state.serverUrl = "";

        GiteaCredentialService.getInstance().setToken(connectionId, tokenToMigrate).whenComplete((unused, error) -> {
            if (error != null) {
                LOG.warn("Failed to migrate Gitea token to PasswordSafe for connection " + connectionId, error);
                return;
            }
            if (!ApplicationManager.getApplication().isDisposed()) {
                ApplicationManager.getApplication().invokeLater(GiteaSettings::saveNow);
            }
        });
    }

    private static @NotNull ActionsConnection toModel(@NotNull ServerState server) {
        ProviderType providerType = parseProviderType(server.providerType);
        String url = server.url == null ? "" : server.url.trim();
        String name = server.name == null || server.name.isBlank()
                ? hostName(providerType, url)
                : server.name.trim();
        List<String> repositories = sanitizeRepositories(server.repositories);
        return new ActionsConnection(
                server.id == null ? "" : server.id,
                providerType,
                name,
                url,
                trimToNull(server.apiUrl),
                server.requestTimeoutSeconds,
                repositories
        );
    }

    private static @NotNull ServerState toState(@NotNull ActionsConnection connection) {
        ServerState state = new ServerState();
        state.id = connection.id();
        state.providerType = connection.providerType().name();
        state.name = connection.name();
        state.url = connection.serverUrl().trim();
        state.apiUrl = connection.apiUrl() == null ? "" : connection.apiUrl().trim();
        state.requestTimeoutSeconds = connection.safeTimeoutSeconds();
        state.repositories = new ArrayList<>(connection.repositories());
        return state;
    }

    private static @NotNull ProviderType parseProviderType(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return ProviderType.GITEA;
        }
        try {
            return ProviderType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ProviderType.GITEA;
        }
    }

    private static @Nullable String trimToNull(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    private static @NotNull String hostName(@NotNull ProviderType providerType, @NotNull String url) {
        String value = url.trim();
        if (value.isEmpty()) {
            return providerType.displayName();
        }
        String candidate = value.startsWith("http://") || value.startsWith("https://")
                ? value
                : "https://" + value;
        try {
            String host = URI.create(candidate).getHost();
            return host == null || host.isBlank() ? value : host;
        } catch (IllegalArgumentException e) {
            return value;
        }
    }
}
