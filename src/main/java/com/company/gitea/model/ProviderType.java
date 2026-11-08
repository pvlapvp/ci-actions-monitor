package com.company.gitea.model;

import org.jetbrains.annotations.NotNull;

/**
 * Type of a configured CI/CD provider.
 */
public enum ProviderType {

    GITEA("Gitea"),
    GITHUB("GitHub");

    private final String displayName;

    ProviderType(@NotNull String displayName) {
        this.displayName = displayName;
    }

    public @NotNull String displayName() {
        return displayName;
    }

    @Override
    public @NotNull String toString() {
        return displayName;
    }
}
