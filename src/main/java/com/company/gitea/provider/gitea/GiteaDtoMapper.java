package com.company.gitea.provider.gitea;

import com.company.gitea.model.ActionsConnection;
import com.company.gitea.model.Repository;
import com.company.gitea.model.Workflow;
import com.company.gitea.model.WorkflowJob;
import com.company.gitea.model.WorkflowRun;
import com.company.gitea.model.WorkflowStep;
import com.company.gitea.provider.gitea.dto.GiteaRepositoryDto;
import com.company.gitea.provider.gitea.dto.GiteaWorkflowDto;
import com.company.gitea.provider.gitea.dto.GiteaWorkflowJobDto;
import com.company.gitea.provider.gitea.dto.GiteaWorkflowRunDto;
import com.company.gitea.provider.gitea.dto.GiteaWorkflowStepDto;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts Gitea JSON DTOs into the provider-neutral model used by the UI.
 */
public final class GiteaDtoMapper {

    private final ActionsConnection connection;

    public GiteaDtoMapper(@NotNull ActionsConnection connection) {
        this.connection = connection;
    }

    public @NotNull Repository toRepository(@Nullable GiteaRepositoryDto dto) {
        if (dto == null) {
            return new Repository(0, null, null, null, null, false, null);
        }
        Repository.Owner owner = dto.owner == null ? null : new Repository.Owner(dto.owner.login);
        return new Repository(
                dto.id,
                dto.name,
                dto.fullName,
                firstNonBlank(dto.htmlUrl, repositoryHtmlUrl(dto.fullName)),
                dto.defaultBranch,
                dto.privateRepository,
                owner
        );
    }

    public @NotNull List<Repository> toRepositories(@Nullable List<GiteaRepositoryDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return List.of();
        }
        List<Repository> result = new ArrayList<>(dtos.size());
        for (GiteaRepositoryDto dto : dtos) {
            if (dto != null) {
                result.add(toRepository(dto));
            }
        }
        return List.copyOf(result);
    }

    public @NotNull Workflow toWorkflow(@Nullable GiteaWorkflowDto dto, @NotNull Repository repository) {
        if (dto == null) {
            return new Workflow(null, null, null, null, null, null, null, null, null);
        }
        return new Workflow(
                dto.id,
                dto.name,
                dto.path,
                dto.state,
                dto.createdAt,
                dto.updatedAt,
                dto.url,
                firstNonBlank(dto.htmlUrl, workflowHtmlUrl(dto.id, repository)),
                dto.badgeUrl
        );
    }

    public @NotNull List<Workflow> toWorkflows(@Nullable List<GiteaWorkflowDto> dtos,
                                               @NotNull Repository repository) {
        if (dtos == null || dtos.isEmpty()) {
            return List.of();
        }
        List<Workflow> result = new ArrayList<>(dtos.size());
        for (GiteaWorkflowDto dto : dtos) {
            if (dto != null) {
                result.add(toWorkflow(dto, repository));
            }
        }
        return List.copyOf(result);
    }

    public @NotNull WorkflowRun toWorkflowRun(@Nullable GiteaWorkflowRunDto dto) {
        if (dto == null) {
            return new WorkflowRun(0, null, null, null, null, null, null, 0, 0, 0,
                    null, null, null, null, null, null, null, null);
        }
        return new WorkflowRun(
                dto.id,
                dto.url,
                dto.previousAttemptUrl,
                dto.htmlUrl,
                dto.displayTitle,
                dto.path,
                dto.event,
                dto.runAttempt,
                dto.runNumber,
                dto.repositoryId,
                dto.headSha,
                dto.headBranch,
                dto.status,
                dto.conclusion,
                dto.startedAt,
                dto.completedAt,
                toActor(dto.actor),
                toActor(dto.triggerActor)
        );
    }

    public @NotNull List<WorkflowRun> toWorkflowRuns(@Nullable List<GiteaWorkflowRunDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return List.of();
        }
        List<WorkflowRun> result = new ArrayList<>(dtos.size());
        for (GiteaWorkflowRunDto dto : dtos) {
            if (dto != null) {
                result.add(toWorkflowRun(dto));
            }
        }
        return List.copyOf(result);
    }

    public @NotNull WorkflowJob toWorkflowJob(@Nullable GiteaWorkflowJobDto dto) {
        if (dto == null) {
            return new WorkflowJob(0, null, null, 0, null, null, List.of(), 0,
                    null, null, null, null, 0, null, List.of(), null, null, null);
        }
        return new WorkflowJob(
                dto.id,
                dto.url,
                dto.htmlUrl,
                dto.runId,
                dto.runUrl,
                dto.name,
                dto.labels == null ? List.of() : List.copyOf(dto.labels),
                dto.runAttempt,
                dto.headSha,
                dto.headBranch,
                dto.status,
                dto.conclusion,
                dto.runnerId,
                dto.runnerName,
                toWorkflowSteps(dto.steps),
                dto.createdAt,
                dto.startedAt,
                dto.completedAt
        );
    }

    public @NotNull List<WorkflowJob> toWorkflowJobs(@Nullable List<GiteaWorkflowJobDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return List.of();
        }
        List<WorkflowJob> result = new ArrayList<>(dtos.size());
        for (GiteaWorkflowJobDto dto : dtos) {
            if (dto != null) {
                result.add(toWorkflowJob(dto));
            }
        }
        return List.copyOf(result);
    }

    public @NotNull WorkflowStep toWorkflowStep(@Nullable GiteaWorkflowStepDto dto) {
        if (dto == null) {
            return new WorkflowStep(null, 0, null, null, null, null);
        }
        return new WorkflowStep(
                dto.name,
                dto.number,
                dto.status,
                dto.conclusion,
                dto.startedAt,
                dto.completedAt
        );
    }

    private @NotNull List<WorkflowStep> toWorkflowSteps(@Nullable List<GiteaWorkflowStepDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return List.of();
        }
        List<WorkflowStep> result = new ArrayList<>(dtos.size());
        for (GiteaWorkflowStepDto dto : dtos) {
            if (dto != null) {
                result.add(toWorkflowStep(dto));
            }
        }
        return List.copyOf(result);
    }

    private static @Nullable WorkflowRun.Actor toActor(@Nullable GiteaWorkflowRunDto.Actor actor) {
        return actor == null ? null : new WorkflowRun.Actor(actor.login);
    }

    private @Nullable String repositoryHtmlUrl(@Nullable String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        String base = connection.serverUrl();
        return base == null || base.isBlank() ? null : base + "/" + fullName;
    }

    private @Nullable String workflowHtmlUrl(@Nullable String workflowId, @NotNull Repository repository) {
        String base = repository.htmlUrl();
        if (base == null || base.isBlank()) {
            base = repositoryHtmlUrl(repository.displayName());
        }
        if (base == null || base.isBlank()) {
            return null;
        }
        return base + "/actions?workflow=" + (workflowId == null ? "" : workflowId);
    }

    private static @Nullable String firstNonBlank(@Nullable String first, @Nullable String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null || second.isBlank() ? null : second;
    }
}
