package com.company.gitea.provider.gitea.dto;

import com.google.gson.annotations.SerializedName;

/**
 * Gitea JSON representation of a repository.
 */
public final class GiteaRepositoryDto {

    public long id;
    public String name;
    @SerializedName("full_name")
    public String fullName;
    @SerializedName("html_url")
    public String htmlUrl;
    @SerializedName("default_branch")
    public String defaultBranch;
    @SerializedName("private")
    public boolean privateRepository;
    public Owner owner;

    public static final class Owner {
        public String login;
    }
}
