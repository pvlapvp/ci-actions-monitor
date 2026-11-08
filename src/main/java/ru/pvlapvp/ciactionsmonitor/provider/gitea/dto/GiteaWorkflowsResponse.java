package ru.pvlapvp.ciactionsmonitor.provider.gitea.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Gitea response wrapper for {@code GET /actions/workflows}.
 */
public final class GiteaWorkflowsResponse {

    public List<GiteaWorkflowDto> workflows;
    @SerializedName("total_count")
    public int totalCount;
}
