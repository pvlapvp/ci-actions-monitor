package com.company.gitea.service;

import com.company.gitea.api.GiteaApiClient.GiteaApiException;
import com.company.gitea.model.WorkflowJob;
import com.company.gitea.model.WorkflowRun;
import com.company.gitea.ui.GiteaJobNode;
import com.company.gitea.ui.GiteaRunNode;
import com.company.gitea.ui.GiteaServiceEvents;
import com.company.gitea.ui.GiteaWorkflowNode;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Refreshes loaded workflow runs and run jobs while at least one entity is active.
 * <p>
 * Instead of rebuilding the whole subtree, auto-refresh sends incremental Services
 * events ({@code SERVICE_ADDED}, {@code SERVICE_CHANGED}, {@code SERVICE_REMOVED}).
 * This preserves expanded nodes and the selected job, which would otherwise be lost
 * when the Services model reloads children. Manual Refresh actions still rebuild the
 * subtree on purpose.
 */
@Service(Service.Level.PROJECT)
public final class GiteaAutoRefreshService implements Disposable {

    private static final Logger LOG = Logger.getInstance(GiteaAutoRefreshService.class);
    private static final int REFRESH_INTERVAL_MS = 8_000;

    private final Project project;
    private final Alarm alarm;
    private final Map<String, WatchedWorkflow> workflows = new ConcurrentHashMap<>();
    private final Map<String, WatchedRun> runs = new ConcurrentHashMap<>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private volatile boolean disposed;

    public GiteaAutoRefreshService(@NotNull Project project) {
        this.project = project;
        this.alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);
    }

    public void registerWorkflow(@NotNull GiteaWorkflowNode node, @NotNull List<WorkflowRun> loadedRuns) {
        if (disposed || project.isDisposed()) {
            return;
        }
        WatchedWorkflow watched = workflows.get(node.id());
        if (watched == null) {
            if (loadedRuns.stream().noneMatch(WorkflowRun::isActive)) {
                return;
            }
            watched = new WatchedWorkflow(node);
            workflows.put(node.id(), watched);
        } else {
            watched.node = node;
        }
        watched.setRuns(loadedRuns);
        if (watched.hasActiveRun()) {
            schedule();
        } else {
            workflows.remove(node.id(), watched);
        }
    }

    public void registerRun(@NotNull GiteaRunNode node, @NotNull List<WorkflowJob> loadedJobs) {
        if (disposed || project.isDisposed()) {
            return;
        }
        WatchedRun watched = runs.get(node.id());
        if (watched == null) {
            if (loadedJobs.stream().noneMatch(WorkflowJob::isActive)) {
                return;
            }
            watched = new WatchedRun(node);
            runs.put(node.id(), watched);
        } else {
            watched.node = node;
        }
        watched.setJobs(loadedJobs);
        if (watched.hasActiveJob()) {
            schedule();
        } else {
            runs.remove(node.id(), watched);
        }
    }

    public void clear() {
        workflows.clear();
        runs.clear();
        alarm.cancelAllRequests();
    }

    private void schedule() {
        if (disposed || project.isDisposed() || (workflows.isEmpty() && runs.isEmpty())) {
            return;
        }
        alarm.cancelAllRequests();
        alarm.addRequest(this::tick, REFRESH_INTERVAL_MS);
    }

    private void tick() {
        if (disposed || project.isDisposed()) {
            return;
        }
        try {
            for (WatchedWorkflow watched : List.copyOf(workflows.values())) {
                refreshWorkflow(watched);
            }
            for (WatchedRun watched : List.copyOf(runs.values())) {
                refreshRun(watched);
            }
        } catch (ProcessCanceledException ignored) {
            return;
        } finally {
            schedule();
        }
    }

    private void refreshWorkflow(@NotNull WatchedWorkflow watched) {
        GiteaWorkflowNode node = watched.node;
        String key = "workflow:" + node.id();
        if (!inFlight.add(key)) {
            return;
        }
        try {
            int pages = project.getService(GiteaTreeStateService.class).getPageCount(node.id());
            ActionsMonitorService.RunList runList = project.getService(ActionsMonitorService.class)
                    .loadRuns(node.connection(), node.repository(), node.workflow(), pages);
            if (disposed || project.isDisposed()) {
                return;
            }

            Map<Long, WorkflowRun> oldRuns = watched.snapshotRuns();
            fireRunDiff(node, oldRuns, runList.runs());
            watched.setRuns(runList.runs());

            if (runList.runs().stream().noneMatch(WorkflowRun::isActive)) {
                workflows.remove(node.id(), watched);
            }
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (GiteaApiException e) {
            if (e.isAuthenticationError() || e.isNotFound()) {
                workflows.remove(node.id(), watched);
            }
            LOG.debug("Gitea run refresh failed for " + node.id() + ": " + e.getMessage());
        } catch (Exception e) {
            LOG.debug("Gitea run refresh failed for " + node.id(), e);
        } finally {
            inFlight.remove(key);
        }
    }

    private void refreshRun(@NotNull WatchedRun watched) {
        GiteaRunNode node = watched.node;
        String key = "run:" + node.id();
        if (!inFlight.add(key)) {
            return;
        }
        try {
            int pages = project.getService(GiteaTreeStateService.class).getPageCount(node.id());
            ActionsMonitorService.JobList jobList = project.getService(ActionsMonitorService.class)
                    .loadJobs(node.connection(), node.repository(), node.run(), pages);
            if (disposed || project.isDisposed()) {
                return;
            }

            Map<Long, WorkflowJob> oldJobs = watched.snapshotJobs();
            fireJobDiff(node, oldJobs, jobList.jobs());
            watched.setJobs(jobList.jobs());

            if (jobList.jobs().stream().noneMatch(WorkflowJob::isActive)) {
                runs.remove(node.id(), watched);
            }
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (GiteaApiException e) {
            if (e.isAuthenticationError() || e.isNotFound()) {
                runs.remove(node.id(), watched);
            }
            LOG.debug("Gitea job refresh failed for " + node.id() + ": " + e.getMessage());
        } catch (Exception e) {
            LOG.debug("Gitea job refresh failed for " + node.id(), e);
        } finally {
            inFlight.remove(key);
        }
    }

    private void fireRunDiff(@NotNull GiteaWorkflowNode parent,
                             @NotNull Map<Long, WorkflowRun> oldRuns,
                             @NotNull List<WorkflowRun> newRuns) {
        Map<Long, WorkflowRun> newById = new HashMap<>();
        for (WorkflowRun run : newRuns) {
            newById.put(run.id(), run);
            WorkflowRun oldRun = oldRuns.get(run.id());
            GiteaRunNode target = new GiteaRunNode(parent.connection(), parent.repository(), parent.workflow(), run);
            if (oldRun == null) {
                GiteaServiceEvents.fireServiceAdded(project, target, parent);
            } else if (!oldRun.equals(run)) {
                GiteaServiceEvents.fireChanged(project, target);
            }
        }
        for (Map.Entry<Long, WorkflowRun> entry : oldRuns.entrySet()) {
            if (!newById.containsKey(entry.getKey())) {
                GiteaServiceEvents.fireServiceRemoved(
                        project,
                        new GiteaRunNode(parent.connection(), parent.repository(), parent.workflow(), entry.getValue())
                );
            }
        }
    }

    private void fireJobDiff(@NotNull GiteaRunNode parent,
                             @NotNull Map<Long, WorkflowJob> oldJobs,
                             @NotNull List<WorkflowJob> newJobs) {
        Map<Long, WorkflowJob> newById = new HashMap<>();
        for (WorkflowJob job : newJobs) {
            newById.put(job.id(), job);
            WorkflowJob oldJob = oldJobs.get(job.id());
            GiteaJobNode target = new GiteaJobNode(parent.connection(), parent.repository(), job);
            if (oldJob == null) {
                GiteaServiceEvents.fireServiceAdded(project, target, parent);
            } else if (!oldJob.equals(job)) {
                GiteaServiceEvents.fireChanged(project, target);
            }
        }
        for (Map.Entry<Long, WorkflowJob> entry : oldJobs.entrySet()) {
            if (!newById.containsKey(entry.getKey())) {
                GiteaServiceEvents.fireServiceRemoved(
                        project,
                        new GiteaJobNode(parent.connection(), parent.repository(), entry.getValue())
                );
            }
        }
    }

    @Override
    public void dispose() {
        disposed = true;
        workflows.clear();
        runs.clear();
        alarm.cancelAllRequests();
        alarm.dispose();
    }

    private static final class WatchedWorkflow {
        private volatile GiteaWorkflowNode node;
        private final Map<Long, WorkflowRun> runs = new HashMap<>();

        private WatchedWorkflow(@NotNull GiteaWorkflowNode node) {
            this.node = node;
        }

        private synchronized void setRuns(@NotNull List<WorkflowRun> values) {
            runs.clear();
            for (WorkflowRun run : values) {
                runs.put(run.id(), run);
            }
        }

        private synchronized Map<Long, WorkflowRun> snapshotRuns() {
            return new HashMap<>(runs);
        }

        private synchronized boolean hasActiveRun() {
            return runs.values().stream().anyMatch(WorkflowRun::isActive);
        }
    }

    private static final class WatchedRun {
        private volatile GiteaRunNode node;
        private final Map<Long, WorkflowJob> jobs = new HashMap<>();

        private WatchedRun(@NotNull GiteaRunNode node) {
            this.node = node;
        }

        private synchronized void setJobs(@NotNull List<WorkflowJob> values) {
            jobs.clear();
            for (WorkflowJob job : values) {
                jobs.put(job.id(), job);
            }
        }

        private synchronized Map<Long, WorkflowJob> snapshotJobs() {
            return new HashMap<>(jobs);
        }

        private synchronized boolean hasActiveJob() {
            return jobs.values().stream().anyMatch(WorkflowJob::isActive);
        }
    }
}
