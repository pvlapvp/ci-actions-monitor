package ru.pvlapvp.ciactionsmonitor.model;

import org.jetbrains.annotations.NotNull;

/**
 * A workflow definition returned by a provider.
 * <p>
 * {@code id} is the provider-native key used to request runs. For Gitea it is the
 * workflow file name, for example {@code build.yml}.
 */
public record Workflow(
        String id,
        String name,
        String path,
        String state,
        String createdAt,
        String updatedAt,
        String url,
        String htmlUrl,
        String badgeUrl) {

    public @NotNull String displayName() {
        if (name != null && !name.isBlank()) {
            return name;
        }
        if (id != null && !id.isBlank()) {
            return id;
        }
        return path != null && !path.isBlank() ? path : "workflow";
    }

    public boolean isDisabled() {
        return state != null && "disabled".equalsIgnoreCase(state);
    }
}
