package com.company.gitea.provider.github;

import com.company.gitea.model.ProviderType;
import com.company.gitea.provider.ActionsApiException;
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
