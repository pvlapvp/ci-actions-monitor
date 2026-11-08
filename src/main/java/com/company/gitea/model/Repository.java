package com.company.gitea.model;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A Gitea repository as returned by {@code GET /repos/{owner}/{repo}}.
 */
public record Repository(
        long id,
        String name,
        @SerializedName("full_name") String fullName,
        @SerializedName("html_url") String htmlUrl,
        @SerializedName("default_branch") String defaultBranch,
        @SerializedName("private") boolean privateRepository,
        Owner owner) {

    public record Owner(String login) {
    }

    /**
     * Creates a lightweight repository reference from an {@code owner/name} string.
     * Used for repositories explicitly configured in settings without an extra API call.
     */
    public static @NotNull Repository fromFullName(@NotNull String fullName) {
        String normalized = fullName.trim();
        int slash = normalized.indexOf('/');
        if (slash <= 0 || slash == normalized.length() - 1 || normalized.indexOf('/', slash + 1) >= 0) {
            throw new IllegalArgumentException("Repository must have the form owner/name: " + fullName);
        }
        String owner = normalized.substring(0, slash);
        String name = normalized.substring(slash + 1);
        return new Repository(0, name, normalized, null, null, false, new Owner(owner));
    }

    /**
     * Returns {@code owner/name}, falling back to the short repository name.
     */
    public @NotNull String displayName() {
        return fullName != null && !fullName.isBlank() ? fullName : "repository";
    }

    public @NotNull String ownerLogin() {
        if (owner != null && owner.login() != null) {
            return owner.login();
        }
        String full = displayName();
        int slash = full.indexOf('/');
        return slash > 0 ? full.substring(0, slash) : "";
    }

    public @Nullable String webUrl(@NotNull GiteaServer server) {
        if (htmlUrl != null && !htmlUrl.isBlank()) {
            return htmlUrl;
        }
        String base = server.normalizedUrl();
        return base.isBlank() ? null : base + "/" + displayName();
    }
}
