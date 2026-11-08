package com.company.gitea.provider.github.dto;

import com.google.gson.annotations.SerializedName;

/**
 * GitHub JSON representation of a workflow definition.
 */
public final class GitHubWorkflowDto {

    public long id;
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
