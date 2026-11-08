package com.company.gitea.provider.gitea.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Gitea JSON representation of a workflow job.
 */
public final class GiteaWorkflowJobDto {

    public long id;
    public String url;
    @SerializedName("html_url")
    public String htmlUrl;
    @SerializedName("run_id")
    public long runId;
    @SerializedName("run_url")
    public String runUrl;
    public String name;
    public List<String> labels;
    @SerializedName("run_attempt")
    public int runAttempt;
    @SerializedName("head_sha")
    public String headSha;
    @SerializedName("head_branch")
    public String headBranch;
    public String status;
    public String conclusion;
    @SerializedName("runner_id")
    public long runnerId;
    @SerializedName("runner_name")
    public String runnerName;
    public List<GiteaWorkflowStepDto> steps;
    @SerializedName("created_at")
    public String createdAt;
    @SerializedName("started_at")
    public String startedAt;
    @SerializedName("completed_at")
    public String completedAt;
}
