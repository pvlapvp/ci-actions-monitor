package ru.pvlapvp.ciactionsmonitor.provider.github.dto;

import com.google.gson.annotations.SerializedName;

/**
 * GitHub JSON representation of a workflow run.
 */
public final class GitHubWorkflowRunDto {

    public long id;
    public String name;
    public String url;
    @SerializedName("previous_attempt_url")
    public String previousAttemptUrl;
    @SerializedName("html_url")
    public String htmlUrl;
    @SerializedName("display_title")
    public String displayTitle;
    public String path;
    public String event;
    @SerializedName("run_attempt")
    public int runAttempt;
    @SerializedName("run_number")
    public int runNumber;
    @SerializedName("workflow_id")
    public long workflowId;
    @SerializedName("head_sha")
    public String headSha;
    @SerializedName("head_branch")
    public String headBranch;
    public String status;
    public String conclusion;
    @SerializedName("created_at")
    public String createdAt;
    @SerializedName("run_started_at")
    public String runStartedAt;
    @SerializedName("completed_at")
    public String completedAt;
    public Actor actor;
    @SerializedName("triggering_actor")
    public Actor triggeringActor;
    public RepositoryRef repository;

    public static final class Actor {
        public String login;
    }

    public static final class RepositoryRef {
        public long id;
    }
}
