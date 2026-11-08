package com.company.gitea.provider.gitea.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Gitea JSON representation of a workflow run.
 */
public final class GiteaWorkflowRunDto {

    public long id;
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
    @SerializedName("repository_id")
    public long repositoryId;
    @SerializedName("head_sha")
    public String headSha;
    @SerializedName("head_branch")
    public String headBranch;
    public String status;
    public String conclusion;
    @SerializedName("started_at")
    public String startedAt;
    @SerializedName("completed_at")
    public String completedAt;
    public Actor actor;
    @SerializedName("trigger_actor")
    public Actor triggerActor;

    public static final class Actor {
        public String login;
    }
}
