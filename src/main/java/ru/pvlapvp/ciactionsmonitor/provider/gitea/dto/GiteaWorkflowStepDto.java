package ru.pvlapvp.ciactionsmonitor.provider.gitea.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Gitea JSON representation of a job step.
 */
public final class GiteaWorkflowStepDto {

    public String name;
    public int number;
    public String status;
    public String conclusion;
    @SerializedName("started_at")
    public String startedAt;
    @SerializedName("completed_at")
    public String completedAt;
}
