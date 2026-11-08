package ru.pvlapvp.ciactionsmonitor.service;

import ru.pvlapvp.ciactionsmonitor.provider.ActionsApiException;
import ru.pvlapvp.ciactionsmonitor.model.WorkflowJob;
import ru.pvlapvp.ciactionsmonitor.model.WorkflowRun;
import ru.pvlapvp.ciactionsmonitor.ui.JobNode;
import ru.pvlapvp.ciactionsmonitor.ui.RunNode;
import ru.pvlapvp.ciactionsmonitor.ui.ActionsServiceEvents;
import ru.pvlapvp.ciactionsmonitor.ui.WorkflowNode;
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
public final class AutoRefreshService implements Disposable {

    private static final Logger LOG = Logger.getInstance(AutoRefreshService.class);
    private static final int REFRESH_INTERVAL_MS = 8_000;

    private final Project project;
    private final Alarm alarm;
    private final Map<String, WatchedWorkflow> workflows = new ConcurrentHashMap<>();
    private final Map<String, WatchedRun> runs = new ConcurrentHashMap<>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private volatile long pauseUntilMillis;
    private volatile boolean disposed;

    public AutoRefreshService(@NotNull Project project) {
        this.project = project;
        this.alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);
    }

    public void registerWorkflow(@NotNull WorkflowNode node, @NotNull List<WorkflowRun> loadedRuns) {
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

    public void registerRun(@NotNull RunNode node, @NotNull List<WorkflowJob> loadedJobs) {
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
        pauseUntilMillis = 0;
        alarm.cancelAllRequests();
    }

    private void applyRateLimitPause(@NotNull ActionsApiException e) {
        if (!e.isRateLimited()) {
            return;
        }
        long now = System.currentTimeMillis();
        long retryAt = e.getRetryAfter() == null
                ? now + 60_000
                : e.getRetryAfter().toEpochMilli();
        pauseUntilMillis = Math.max(pauseUntilMillis, retryAt);
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
        if (System.currentTimeMillis() < pauseUntilMillis) {
            schedule();
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
        WorkflowNode node = watched.node;
        String key = "workflow:" + node.id();
        if (!inFlight.add(key)) {
            return;
        }
        try {
            int pages = project.getService(TreeStateService.class).getPageCount(node.id());
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
        } catch (ActionsApiException e) {
            if (e.isAuthenticationError() || e.isNotFound()) {
                workflows.remove(node.id(), watched);
            }
            applyRateLimitPause(e);
            LOG.debug("Run refresh failed for " + node.id() + ": " + e.getMessage());
        } catch (Exception e) {
            LOG.debug("Run refresh failed for " + node.id(), e);
        } finally {
            inFlight.remove(key);
        }
    }

    private void refreshRun(@NotNull WatchedRun watched) {
        RunNode node = watched.node;
        String key = "run:" + node.id();
        if (!inFlight.add(key)) {
            return;
        }
        try {
            int pages = project.getService(TreeStateService.class).getPageCount(node.id());
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
        } catch (ActionsApiException e) {
            if (e.isAuthenticationError() || e.isNotFound()) {
                runs.remove(node.id(), watched);
            }
            applyRateLimitPause(e);
            LOG.debug("Job refresh failed for " + node.id() + ": " + e.getMessage());
        } catch (Exception e) {
            LOG.debug("Job refresh failed for " + node.id(), e);
        } finally {
            inFlight.remove(key);
        }
    }

    private void fireRunDiff(@NotNull WorkflowNode parent,
                             @NotNull Map<Long, WorkflowRun> oldRuns,
                             @NotNull List<WorkflowRun> newRuns) {
        Map<Long, WorkflowRun> newById = new HashMap<>();
        for (WorkflowRun run : newRuns) {
            newById.put(run.id(), run);
            WorkflowRun oldRun = oldRuns.get(run.id());
            RunNode target = new RunNode(parent.connection(), parent.repository(), parent.workflow(), run);
            if (oldRun == null) {
                ActionsServiceEvents.fireServiceAdded(project, target, parent);
            } else if (!oldRun.equals(run)) {
                ActionsServiceEvents.fireChanged(project, target);
            }
        }
        for (Map.Entry<Long, WorkflowRun> entry : oldRuns.entrySet()) {
            if (!newById.containsKey(entry.getKey())) {
                ActionsServiceEvents.fireServiceRemoved(
                        project,
                        new RunNode(parent.connection(), parent.repository(), parent.workflow(), entry.getValue())
                );
            }
        }
    }

    private void fireJobDiff(@NotNull RunNode parent,
                             @NotNull Map<Long, WorkflowJob> oldJobs,
                             @NotNull List<WorkflowJob> newJobs) {
        Map<Long, WorkflowJob> newById = new HashMap<>();
        for (WorkflowJob job : newJobs) {
            newById.put(job.id(), job);
            WorkflowJob oldJob = oldJobs.get(job.id());
            JobNode target = new JobNode(parent.connection(), parent.repository(), job);
            if (oldJob == null) {
                ActionsServiceEvents.fireServiceAdded(project, target, parent);
            } else if (!oldJob.equals(job)) {
                ActionsServiceEvents.fireChanged(project, target);
            }
        }
        for (Map.Entry<Long, WorkflowJob> entry : oldJobs.entrySet()) {
            if (!newById.containsKey(entry.getKey())) {
                ActionsServiceEvents.fireServiceRemoved(
                        project,
                        new JobNode(parent.connection(), parent.repository(), entry.getValue())
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
        private volatile WorkflowNode node;
        private final Map<Long, WorkflowRun> runs = new HashMap<>();

        private WatchedWorkflow(@NotNull WorkflowNode node) {
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
        private volatile RunNode node;
        private final Map<Long, WorkflowJob> jobs = new HashMap<>();

        private WatchedRun(@NotNull RunNode node) {
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
