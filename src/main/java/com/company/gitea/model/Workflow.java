package com.company.gitea.model;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A workflow from {@code GET /repos/{owner}/{repo}/actions/workflows}.
 * <p>
 * Gitea uses the workflow file name (for example {@code build.yml}) as {@code id};
 * the same value is passed to {@code /actions/workflows/{workflow_id}/runs}.
 */
public record Workflow(
        String id,
        String name,
        String path,
        String state,
        @SerializedName("created_at") String createdAt,
        @SerializedName("updated_at") String updatedAt,
        String url,
        @SerializedName("html_url") String htmlUrl,
        @SerializedName("badge_url") String badgeUrl) {

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

    public @Nullable String webUrl(@NotNull ActionsConnection connection, @NotNull Repository repository) {
        if (htmlUrl != null && !htmlUrl.isBlank()) {
            return htmlUrl;
        }
        String base = repository.webUrl(connection);
        return base == null ? null : base + "/actions?workflow=" + (id == null ? "" : id);
    }
}
