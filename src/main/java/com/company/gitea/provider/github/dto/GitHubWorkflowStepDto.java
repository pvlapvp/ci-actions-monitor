package com.company.gitea.provider.github.dto;

import com.google.gson.annotations.SerializedName;

/**
 * GitHub JSON representation of a job step.
 */
public final class GitHubWorkflowStepDto {

    public String name;
    public int number;
    public String status;
    public String conclusion;
    @SerializedName("started_at")
    public String startedAt;
    @SerializedName("completed_at")
    public String completedAt;
}
