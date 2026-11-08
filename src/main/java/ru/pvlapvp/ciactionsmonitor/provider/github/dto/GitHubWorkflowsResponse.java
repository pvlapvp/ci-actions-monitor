package ru.pvlapvp.ciactionsmonitor.provider.github.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * GitHub response wrapper for repository workflows.
 */
public final class GitHubWorkflowsResponse {

    @SerializedName("total_count")
    public int totalCount;
    public List<GitHubWorkflowDto> workflows;
}
