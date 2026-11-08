package com.company.gitea.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A workflow job returned by a provider.
 */
public record WorkflowJob(
        long id,
        String url,
        String htmlUrl,
        long runId,
        String runUrl,
        String name,
        List<String> labels,
        int runAttempt,
        String headSha,
        String headBranch,
        String status,
        String conclusion,
        long runnerId,
        String runnerName,
        List<WorkflowStep> steps,
        String createdAt,
        String startedAt,
        String completedAt) {

    public WorkflowJob {
        steps = steps == null ? List.of() : List.copyOf(steps);
        labels = labels == null ? List.of() : List.copyOf(labels);
    }

    public @NotNull String displayName() {
        return name != null && !name.isBlank() ? name : "job " + id;
    }

    public @NotNull GiteaStatus state() {
        return GiteaStatus.from(status, conclusion);
    }

    public boolean isActive() {
        return state().isActive();
    }

    public boolean isFinished() {
        return state().isFinished();
    }

    public @Nullable String webUrl() {
        return htmlUrl == null || htmlUrl.isBlank() ? null : htmlUrl;
    }

    public @NotNull String labelsText() {
        return labels.isEmpty() ? "" : String.join(", ", labels);
    }

    public @NotNull String runnerText() {
        return runnerName == null || runnerName.isBlank() ? "" : runnerName;
    }
}
