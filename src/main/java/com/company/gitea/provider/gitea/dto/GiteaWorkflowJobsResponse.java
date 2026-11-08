package com.company.gitea.provider.gitea.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Gitea response wrapper for workflow jobs.
 */
public final class GiteaWorkflowJobsResponse {

    public List<GiteaWorkflowJobDto> jobs;
    @SerializedName("total_count")
    public int totalCount;
}
