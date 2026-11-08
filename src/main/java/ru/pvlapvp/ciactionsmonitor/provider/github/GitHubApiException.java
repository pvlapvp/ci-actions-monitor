package ru.pvlapvp.ciactionsmonitor.provider.github;

import ru.pvlapvp.ciactionsmonitor.model.ProviderType;
import ru.pvlapvp.ciactionsmonitor.provider.ActionsApiException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * GitHub-specific API error.
 */
public final class GitHubApiException extends ActionsApiException {

    public GitHubApiException(int statusCode,
                              @NotNull Kind kind,
                              @NotNull String message,
                              @Nullable Instant retryAfter) {
        super(ProviderType.GITHUB, statusCode, kind, message, retryAfter);
    }
}
