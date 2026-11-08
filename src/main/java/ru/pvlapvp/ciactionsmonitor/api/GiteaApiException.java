package ru.pvlapvp.ciactionsmonitor.api;

import ru.pvlapvp.ciactionsmonitor.model.ProviderType;
import ru.pvlapvp.ciactionsmonitor.provider.ActionsApiException;
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
