package com.company.gitea.model;

import com.google.gson.annotations.SerializedName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A workflow run from {@code GET /repos/{owner}/{repo}/actions/runs/{run}}.
 * <p>
 * Fields follow Gitea 1.27's {@code ActionWorkflowRun} response. In particular,
 * the API does not return {@code name} or {@code workflow_id}; the workflow is known
 * from the tree node and {@link #path()} contains {@code workflow-file@ref}.
 */
public record WorkflowRun(
        long id,
        String url,
        @SerializedName("previous_attempt_url") String previousAttemptUrl,
        @SerializedName("html_url") String htmlUrl,
        @SerializedName("display_title") String displayTitle,
        String path,
        String event,
        @SerializedName("run_attempt") int runAttempt,
        @SerializedName("run_number") int runNumber,
        @SerializedName("repository_id") long repositoryId,
        @SerializedName("head_sha") String headSha,
        @SerializedName("head_branch") String headBranch,
        String status,
        String conclusion,
        @SerializedName("started_at") String startedAt,
        @SerializedName("completed_at") String completedAt,
        Actor actor,
        @SerializedName("trigger_actor") Actor triggerActor) {

    public record Actor(String login) {
    }

    public @NotNull String displayName() {
        String prefix = runNumber > 0 ? "#" + runNumber : "#" + id;
        String title = displayTitle != null && !displayTitle.isBlank() ? displayTitle.trim() : path;
        return title == null || title.isBlank() ? prefix : prefix + " " + title;
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

    public @NotNull String branchText() {
        return headBranch == null || headBranch.isBlank() ? "" : headBranch;
    }

    public @Nullable String actorLogin() {
        return actor != null && actor.login() != null && !actor.login().isBlank() ? actor.login() : null;
    }

    public @Nullable String webUrl() {
        return htmlUrl == null || htmlUrl.isBlank() ? null : htmlUrl;
    }

    public boolean hasStarted() {
        return startedAt != null && !startedAt.isBlank() && !startedAt.startsWith("0001-01-01");
    }
}
