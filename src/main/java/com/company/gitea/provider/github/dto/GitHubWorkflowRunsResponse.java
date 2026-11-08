package com.company.gitea.provider.github.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * GitHub response wrapper for workflow runs.
 */
public final class GitHubWorkflowRunsResponse {

    @SerializedName("total_count")
    public int totalCount;
    @SerializedName("workflow_runs")
    public List<GitHubWorkflowRunDto> workflowRuns;
}
