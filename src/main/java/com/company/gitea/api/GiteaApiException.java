package com.company.gitea.api;

import com.company.gitea.model.ProviderType;
import com.company.gitea.provider.ActionsApiException;
import org.jetbrains.annotations.NotNull;

/**
 * Gitea-specific API error.
 */
public final class GiteaApiException extends ActionsApiException {

    public GiteaApiException(int statusCode,
                             @NotNull ActionsApiException.Kind kind,
                             @NotNull String message) {
        super(ProviderType.GITEA, statusCode, kind, message);
    }
}
