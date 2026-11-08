package com.company.gitea.model;

import org.jetbrains.annotations.NotNull;

/**
 * A step of a workflow job.
 */
public record WorkflowStep(
        String name,
        int number,
        String status,
        String conclusion,
        String startedAt,
        String completedAt) {

    public @NotNull String displayName() {
        String prefix = number > 0 ? number + ". " : "";
        return prefix + (name != null && !name.isBlank() ? name : "step");
    }

    public @NotNull GiteaStatus state() {
        return GiteaStatus.from(status, conclusion);
    }
}
