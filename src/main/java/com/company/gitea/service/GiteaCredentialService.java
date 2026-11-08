package com.company.gitea.service;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Stores Gitea access tokens in IntelliJ {@link PasswordSafe}.
 * <p>
 * Credentials are bound to a stable server id (not to the token or URL), so changing
 * the server URL does not lose the token, while deleting a server can delete the
 * corresponding credential. No public method is intended to be called on the EDT:
 * use the asynchronous methods from UI code, and {@link #getTokenBlocking(String)}
 * only from background threads.
 */
@Service(Service.Level.APP)
public final class GiteaCredentialService {

    private static final Logger LOG = Logger.getInstance(GiteaCredentialService.class);
    private static final String SERVICE_NAME = "GiteaActionsMonitor";
    private static final long WAIT_FOR_PENDING_WRITE_SECONDS = 5;

    private final Map<String, CompletableFuture<Void>> pendingWrites = new ConcurrentHashMap<>();

    public static GiteaCredentialService getInstance() {
        return com.intellij.openapi.application.ApplicationManager.getApplication()
                .getService(GiteaCredentialService.class);
    }

    /**
     * Blocking read. Must be called from a background thread only.
     */
    public @NotNull String getTokenBlocking(@NotNull String serverId) {
        waitForPendingWrite(serverId);
        CredentialAttributes attributes = attributes(serverId);
        try {
            Credentials credentials = PasswordSafe.getInstance().get(attributes);
            if (credentials == null) {
                return "";
            }
            String password = credentials.getPasswordAsString();
            return password == null ? "" : password;
        } catch (RuntimeException e) {
            // Never include the credential value in the message.
            LOG.warn("Cannot read Gitea token from PasswordSafe for server " + serverId, e);
            return "";
        }
    }

    public @NotNull CompletableFuture<Boolean> hasToken(@NotNull String serverId) {
        return CompletableFuture.supplyAsync(
                () -> !getTokenBlocking(serverId).isBlank(),
                AppExecutorUtil.getAppExecutorService()
        );
    }

    /**
     * Saves the token in the background and returns a future completed when the write is done.
     */
    public @NotNull CompletableFuture<Void> setToken(@NotNull String serverId, @NotNull String token) {
        String normalizedToken = token.trim();
        if (normalizedToken.isEmpty()) {
            return deleteToken(serverId);
        }

        CompletableFuture<Void> future = CompletableFuture.runAsync(
                () -> PasswordSafe.getInstance().setPassword(attributes(serverId), normalizedToken),
                AppExecutorUtil.getAppExecutorService()
        );
        pendingWrites.put(serverId, future);
        future.whenComplete((unused, error) -> {
            pendingWrites.remove(serverId, future);
            if (error != null) {
                LOG.warn("Cannot save Gitea token to PasswordSafe for server " + serverId, error);
            }
        });
        return future;
    }

    /**
     * Deletes the token in the background.
     */
    public @NotNull CompletableFuture<Void> deleteToken(@NotNull String serverId) {
        CompletableFuture<Void> future = CompletableFuture.runAsync(
                () -> PasswordSafe.getInstance().set(attributes(serverId), null),
                AppExecutorUtil.getAppExecutorService()
        );
        pendingWrites.put(serverId, future);
        future.whenComplete((unused, error) -> {
            pendingWrites.remove(serverId, future);
            if (error != null) {
                LOG.warn("Cannot delete Gitea token from PasswordSafe for server " + serverId, error);
            }
        });
        return future;
    }

    private void waitForPendingWrite(@NotNull String serverId) {
        CompletableFuture<Void> pending = pendingWrites.get(serverId);
        if (pending == null) {
            return;
        }
        try {
            pending.get(WAIT_FOR_PENDING_WRITE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            LOG.warn("Gitea token write did not finish in time for server " + serverId, e);
        }
    }

    private static @NotNull CredentialAttributes attributes(@NotNull String serverId) {
        String account = serverId.isBlank() ? "unknown" : serverId;
        return new CredentialAttributes(SERVICE_NAME, account);
    }
}
