package com.company.gitea.provider.github.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * GitHub response wrapper for workflow jobs.
 */
public final class GitHubWorkflowJobsResponse {

    @SerializedName("total_count")
    public int totalCount;
    public List<GitHubWorkflowJobDto> jobs;
}
