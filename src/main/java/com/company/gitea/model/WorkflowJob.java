package com.company.gitea.model;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A job from {@code GET /repos/{owner}/{repo}/actions/runs/{run}/jobs}
 * or {@code GET /repos/{owner}/{repo}/actions/jobs/{job_id}}.
 */
public record WorkflowJob(
        long id,
        String url,
        @SerializedName("html_url") String htmlUrl,
        @SerializedName("run_id") long runId,
        @SerializedName("run_url") String runUrl,
        String name,
        List<String> labels,
        @SerializedName("run_attempt") int runAttempt,
        @SerializedName("head_sha") String headSha,
        @SerializedName("head_branch") String headBranch,
        String status,
        String conclusion,
        @SerializedName("runner_id") long runnerId,
        @SerializedName("runner_name") String runnerName,
        List<WorkflowStep> steps,
        @SerializedName("created_at") String createdAt,
        @SerializedName("started_at") String startedAt,
        @SerializedName("completed_at") String completedAt) {

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
