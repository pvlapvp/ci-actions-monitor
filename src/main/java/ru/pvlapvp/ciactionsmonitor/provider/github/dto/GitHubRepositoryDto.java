package ru.pvlapvp.ciactionsmonitor.provider.github.dto;

import com.google.gson.annotations.SerializedName;

/**
 * GitHub JSON representation of a repository.
 */
public final class GitHubRepositoryDto {

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
