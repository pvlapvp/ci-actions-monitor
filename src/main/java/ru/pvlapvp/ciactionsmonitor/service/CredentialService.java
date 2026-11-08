package ru.pvlapvp.ciactionsmonitor.service;

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
 * Stores access tokens in IntelliJ {@link PasswordSafe}.
 * <p>
 * Credentials are bound to a stable connection id (not to the token or URL), so changing
 * the server URL does not lose the token, while deleting a connection can delete the
 * corresponding credential. No public method is intended to be called on the EDT:
 * use the asynchronous methods from UI code, and {@link #getTokenBlocking(String)}
 * only from background threads.
 */
@Service(Service.Level.APP)
public final class CredentialService {

    private static final Logger LOG = Logger.getInstance(CredentialService.class);
    private static final String SERVICE_NAME = "CiActionsMonitor";
    private static final long WAIT_FOR_PENDING_WRITE_SECONDS = 5;

    private final Map<String, CompletableFuture<Void>> pendingWrites = new ConcurrentHashMap<>();

    public static CredentialService getInstance() {
        return com.intellij.openapi.application.ApplicationManager.getApplication()
                .getService(CredentialService.class);
    }

    /**
     * Blocking read. Must be called from a background thread only.
     */
    public @NotNull String getTokenBlocking(@NotNull String connectionId) {
        waitForPendingWrite(connectionId);
        CredentialAttributes attributes = attributes(connectionId);
        try {
            Credentials credentials = PasswordSafe.getInstance().get(attributes);
            if (credentials == null) {
                return "";
            }
            String password = credentials.getPasswordAsString();
            return password == null ? "" : password;
        } catch (RuntimeException e) {
            // Never include the credential value in the message.
            LOG.warn("Cannot read access token from PasswordSafe for connection " + connectionId, e);
            return "";
        }
    }

    public @NotNull CompletableFuture<Boolean> hasToken(@NotNull String connectionId) {
        return CompletableFuture.supplyAsync(
                () -> !getTokenBlocking(connectionId).isBlank(),
                AppExecutorUtil.getAppExecutorService()
        );
    }

    /**
     * Saves the token in the background and returns a future completed when the write is done.
     */
    public @NotNull CompletableFuture<Void> setToken(@NotNull String connectionId, @NotNull String token) {
        String normalizedToken = token.trim();
        if (normalizedToken.isEmpty()) {
            return deleteToken(connectionId);
        }

        CompletableFuture<Void> future = CompletableFuture.runAsync(
                () -> PasswordSafe.getInstance().setPassword(attributes(connectionId), normalizedToken),
                AppExecutorUtil.getAppExecutorService()
        );
        pendingWrites.put(connectionId, future);
        future.whenComplete((unused, error) -> {
            pendingWrites.remove(connectionId, future);
            if (error != null) {
                LOG.warn("Cannot save access token to PasswordSafe for connection " + connectionId, error);
            }
        });
        return future;
    }

    /**
     * Deletes the token in the background.
     */
    public @NotNull CompletableFuture<Void> deleteToken(@NotNull String connectionId) {
        CompletableFuture<Void> future = CompletableFuture.runAsync(
                () -> PasswordSafe.getInstance().set(attributes(connectionId), null),
                AppExecutorUtil.getAppExecutorService()
        );
        pendingWrites.put(connectionId, future);
        future.whenComplete((unused, error) -> {
            pendingWrites.remove(connectionId, future);
            if (error != null) {
                LOG.warn("Cannot delete access token from PasswordSafe for connection " + connectionId, error);
            }
        });
        return future;
    }

    private void waitForPendingWrite(@NotNull String connectionId) {
        CompletableFuture<Void> pending = pendingWrites.get(connectionId);
        if (pending == null) {
            return;
        }
        try {
            pending.get(WAIT_FOR_PENDING_WRITE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            LOG.warn("Access token write did not finish in time for connection " + connectionId, e);
        }
    }

    private static @NotNull CredentialAttributes attributes(@NotNull String connectionId) {
        String account = connectionId.isBlank() ? "unknown" : connectionId;
        return new CredentialAttributes(SERVICE_NAME, account);
    }
}
