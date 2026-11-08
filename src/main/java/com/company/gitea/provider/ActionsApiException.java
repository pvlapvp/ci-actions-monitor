package com.company.gitea.provider;

import com.company.gitea.model.ProviderType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.time.Instant;

/**
 * Common API error for all providers. The message never contains an access token.
 */
public class ActionsApiException extends IOException {

    public enum Kind {
        AUTHENTICATION,
        NOT_FOUND,
        RATE_LIMITED,
        OTHER
    }

    private final ProviderType providerType;
    private final int statusCode;
    private final Kind kind;
    private final Instant retryAfter;

    public ActionsApiException(@NotNull ProviderType providerType,
                               int statusCode,
                               @NotNull Kind kind,
                               @NotNull String message) {
        this(providerType, statusCode, kind, message, null);
    }

    public ActionsApiException(@NotNull ProviderType providerType,
                               int statusCode,
                               @NotNull Kind kind,
                               @NotNull String message,
                               @Nullable Instant retryAfter) {
        super(message);
        this.providerType = providerType;
        this.statusCode = statusCode;
        this.kind = kind;
        this.retryAfter = retryAfter;
    }

    public @NotNull ProviderType getProviderType() {
        return providerType;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public @NotNull Kind getKind() {
        return kind;
    }

    public @Nullable Instant getRetryAfter() {
        return retryAfter;
    }

    public boolean isAuthenticationError() {
        return kind == Kind.AUTHENTICATION;
    }

    public boolean isNotFound() {
        return kind == Kind.NOT_FOUND;
    }

    public boolean isRateLimited() {
        return kind == Kind.RATE_LIMITED;
    }
}
