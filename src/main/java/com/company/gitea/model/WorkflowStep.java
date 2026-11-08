package com.company.gitea.model;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;

/**
 * A step of a workflow job.
 */
public record WorkflowStep(
        String name,
        int number,
        String status,
        String conclusion,
        @SerializedName("started_at") String startedAt,
        @SerializedName("completed_at") String completedAt) {

    public @NotNull String displayName() {
        String prefix = number > 0 ? number + ". " : "";
        return prefix + (name != null && !name.isBlank() ? name : "step");
    }

    public @NotNull GiteaStatus state() {
        return GiteaStatus.from(status, conclusion);
    }
}
