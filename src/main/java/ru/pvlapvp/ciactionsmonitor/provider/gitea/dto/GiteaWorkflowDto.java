package ru.pvlapvp.ciactionsmonitor.provider.gitea.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Gitea JSON representation of a workflow definition.
 */
public final class GiteaWorkflowDto {

    public String id;
    public String name;
    public String path;
    public String state;
    @SerializedName("created_at")
    public String createdAt;
    @SerializedName("updated_at")
    public String updatedAt;
    public String url;
    @SerializedName("html_url")
    public String htmlUrl;
    @SerializedName("badge_url")
    public String badgeUrl;
}
