package ru.pvlapvp.ciactionsmonitor.provider.gitea.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Gitea response wrapper for workflow runs.
 */
public final class GiteaWorkflowRunsResponse {

    @SerializedName("workflow_runs")
    public List<GiteaWorkflowRunDto> workflowRuns;
    @SerializedName("total_count")
    public int totalCount;
}
