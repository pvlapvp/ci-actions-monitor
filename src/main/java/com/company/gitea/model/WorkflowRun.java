package com.company.gitea.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A workflow run returned by a provider.
 * <p>
 * Some fields are optional because providers expose slightly different subsets of
 * the same GitHub-compatible schema.
 */
public record WorkflowRun(
        long id,
        String url,
        String previousAttemptUrl,
        String htmlUrl,
        String displayTitle,
        String path,
        String event,
        int runAttempt,
        int runNumber,
        long repositoryId,
        String headSha,
        String headBranch,
        String status,
        String conclusion,
        String startedAt,
        String completedAt,
        Actor actor,
        Actor triggerActor) {

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
