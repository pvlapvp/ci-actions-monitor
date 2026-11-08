package ru.pvlapvp.ciactionsmonitor.provider.github;

import ru.pvlapvp.ciactionsmonitor.model.ActionsConnection;
import ru.pvlapvp.ciactionsmonitor.model.Repository;
import ru.pvlapvp.ciactionsmonitor.model.Workflow;
import ru.pvlapvp.ciactionsmonitor.model.WorkflowJob;
import ru.pvlapvp.ciactionsmonitor.model.WorkflowRun;
import ru.pvlapvp.ciactionsmonitor.model.WorkflowStep;
import ru.pvlapvp.ciactionsmonitor.provider.github.dto.GitHubRepositoryDto;
import ru.pvlapvp.ciactionsmonitor.provider.github.dto.GitHubWorkflowDto;
import ru.pvlapvp.ciactionsmonitor.provider.github.dto.GitHubWorkflowJobDto;
import ru.pvlapvp.ciactionsmonitor.provider.github.dto.GitHubWorkflowRunDto;
import ru.pvlapvp.ciactionsmonitor.provider.github.dto.GitHubWorkflowStepDto;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Converts GitHub JSON DTOs into the provider-neutral model used by the UI.
 */
public final class GitHubDtoMapper {

    private final ActionsConnection connection;

    public GitHubDtoMapper(@NotNull ActionsConnection connection) {
        this.connection = connection;
    }

    public @NotNull Repository toRepository(@Nullable GitHubRepositoryDto dto) {
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

    public @NotNull List<Repository> toRepositories(@Nullable List<GitHubRepositoryDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return List.of();
        }
        List<Repository> result = new ArrayList<>(dtos.size());
        for (GitHubRepositoryDto dto : dtos) {
            if (dto != null) {
                result.add(toRepository(dto));
            }
        }
        return List.copyOf(result);
    }

    public @NotNull Workflow toWorkflow(@Nullable GitHubWorkflowDto dto, @NotNull Repository repository) {
        if (dto == null) {
            return new Workflow(null, null, null, null, null, null, null, null, null);
        }
        return new Workflow(
                String.valueOf(dto.id),
                dto.name,
                dto.path,
                normalizeState(dto.state),
                dto.createdAt,
                dto.updatedAt,
                dto.url,
                firstNonBlank(dto.htmlUrl, workflowHtmlUrl(dto.path, repository)),
                dto.badgeUrl
        );
    }

    public @NotNull List<Workflow> toWorkflows(@Nullable List<GitHubWorkflowDto> dtos,
                                               @NotNull Repository repository) {
        if (dtos == null || dtos.isEmpty()) {
            return List.of();
        }
        List<Workflow> result = new ArrayList<>(dtos.size());
        for (GitHubWorkflowDto dto : dtos) {
            if (dto != null) {
                result.add(toWorkflow(dto, repository));
            }
        }
        return List.copyOf(result);
    }

    public @NotNull WorkflowRun toWorkflowRun(@Nullable GitHubWorkflowRunDto dto) {
        if (dto == null) {
            return new WorkflowRun(0, null, null, null, null, null, null, 0, 0, 0,
                    null, null, null, null, null, null, null, null);
        }
        return new WorkflowRun(
                dto.id,
                dto.url,
                dto.previousAttemptUrl,
                dto.htmlUrl,
                firstNonBlank(dto.displayTitle, dto.name),
                dto.path,
                dto.event,
                dto.runAttempt,
                dto.runNumber,
                dto.repository == null ? 0 : dto.repository.id,
                dto.headSha,
                dto.headBranch,
                dto.status,
                dto.conclusion,
                dto.runStartedAt,
                dto.completedAt,
                toActor(dto.actor),
                toActor(dto.triggeringActor)
        );
    }

    public @NotNull List<WorkflowRun> toWorkflowRuns(@Nullable List<GitHubWorkflowRunDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return List.of();
        }
        List<WorkflowRun> result = new ArrayList<>(dtos.size());
        for (GitHubWorkflowRunDto dto : dtos) {
            if (dto != null) {
                result.add(toWorkflowRun(dto));
            }
        }
        return List.copyOf(result);
    }

    public @NotNull WorkflowJob toWorkflowJob(@Nullable GitHubWorkflowJobDto dto) {
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

    public @NotNull List<WorkflowJob> toWorkflowJobs(@Nullable List<GitHubWorkflowJobDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return List.of();
        }
        List<WorkflowJob> result = new ArrayList<>(dtos.size());
        for (GitHubWorkflowJobDto dto : dtos) {
            if (dto != null) {
                result.add(toWorkflowJob(dto));
            }
        }
        return List.copyOf(result);
    }

    public @NotNull WorkflowStep toWorkflowStep(@Nullable GitHubWorkflowStepDto dto) {
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

    private @NotNull List<WorkflowStep> toWorkflowSteps(@Nullable List<GitHubWorkflowStepDto> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return List.of();
        }
        List<WorkflowStep> result = new ArrayList<>(dtos.size());
        for (GitHubWorkflowStepDto dto : dtos) {
            if (dto != null) {
                result.add(toWorkflowStep(dto));
            }
        }
        return List.copyOf(result);
    }

    private static @Nullable WorkflowRun.Actor toActor(@Nullable GitHubWorkflowRunDto.Actor actor) {
        return actor == null ? null : new WorkflowRun.Actor(actor.login);
    }

    private static @Nullable String normalizeState(@Nullable String state) {
        if (state == null || state.isBlank()) {
            return null;
        }
        return state.toLowerCase(Locale.ROOT).startsWith("disabled") ? "disabled" : state;
    }

    private @Nullable String repositoryHtmlUrl(@Nullable String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        String base = connection.serverUrl();
        return base == null || base.isBlank() ? null : base + "/" + fullName;
    }

    private @Nullable String workflowHtmlUrl(@Nullable String path, @NotNull Repository repository) {
        String base = repository.htmlUrl();
        if (base == null || base.isBlank()) {
            base = repositoryHtmlUrl(repository.displayName());
        }
        if (base == null || base.isBlank()) {
            return null;
        }
        String file = path == null || path.isBlank() ? "" : path.substring(path.lastIndexOf('/') + 1);
        return file.isBlank() ? base + "/actions" : base + "/actions/workflows/" + file;
    }

    private static @Nullable String firstNonBlank(@Nullable String first, @Nullable String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null || second.isBlank() ? null : second;
    }
}
