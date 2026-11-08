package com.company.gitea.service;

import com.company.gitea.api.GiteaApiClient.GiteaApiException;
import com.company.gitea.model.ActionsConnection;
import com.company.gitea.model.GiteaStatus;
import com.company.gitea.model.Repository;
import com.company.gitea.model.WorkflowJob;
import com.company.gitea.ui.LogsPanel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Owns the shared log panel and refreshes the log of the selected running job.
 * <p>
 * Gitea returns the accumulated log as {@code text/plain}; the document is replaced
 * on every update, so lines are not duplicated. Polling stops when the job finishes,
 * when another job is selected, or when the project is closed.
 */
@Service(Service.Level.PROJECT)
public final class LogsService implements Disposable {

    private static final int REFRESH_INTERVAL_MS = 4_000;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Pattern ANSI_ESCAPE = Pattern.compile("\u001B\\[[;\\d]*[ -/]*[@-~]");

    private final Project project;
    private final ActionsMonitorService monitorService;
    private final LogsPanel panel = new LogsPanel();
    private final Alarm alarm;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicBoolean loading = new AtomicBoolean();
    private volatile JobSelection current;
    private volatile boolean disposed;
    private volatile String lastRawLog = "";

    public LogsService(@NotNull Project project) {
        this.project = project;
        this.monitorService = project.getService(ActionsMonitorService.class);
        this.alarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, project);
    }

    public @NotNull LogsPanel getPanel() {
        return panel;
    }

    /**
     * Called on the EDT when a job node becomes selected.
     */
    public void selectJob(@NotNull ActionsConnection server,
                          @NotNull Repository repository,
                          @NotNull WorkflowJob job) {
        if (disposed) {
            return;
        }

        JobSelection selection = current;
        if (selection != null && selection.isSameJob(server, repository, job.id())) {
            selection.job = job;
            if (job.isActive()) {
                schedule(selection.generation);
            }
            return;
        }

        long newGeneration = generation.incrementAndGet();
        selection = new JobSelection(newGeneration, server, repository, job);
        current = selection;
        alarm.cancelAllRequests();
        panel.showLoading(buildTitle(selection));
        loadLogs(newGeneration);
    }

    /**
     * Called when a job node loses selection. Keeps the last log visible.
     */
    public void deselect(long jobId) {
        JobSelection selection = current;
        if (selection != null && selection.job.id() == jobId) {
            current = null;
            generation.incrementAndGet();
            alarm.cancelAllRequests();
        }
    }

    public void reloadCurrent() {
        JobSelection selection = current;
        if (selection == null || disposed) {
            return;
        }
        alarm.cancelAllRequests();
        loadLogs(selection.generation);
    }

    private void loadLogs(long expectedGeneration) {
        if (disposed || project.isDisposed()) {
            return;
        }
        JobSelection selection = current;
        if (selection == null || selection.generation != expectedGeneration
                || generation.get() != expectedGeneration) {
            return;
        }
        if (!loading.compareAndSet(false, true)) {
            return;
        }

        AppExecutorUtil.getAppExecutorService().execute(() -> {
            WorkflowJob freshJob = null;
            String logs = null;
            Exception jobError = null;
            Exception logError = null;

            try {
                freshJob = monitorService.loadJob(selection.server, selection.repository, selection.job.id());
            } catch (ProcessCanceledException e) {
                loading.set(false);
                return;
            } catch (Exception e) {
                jobError = e;
            }

            try {
                logs = monitorService.loadJobLogs(selection.server, selection.repository, selection.job.id());
            } catch (ProcessCanceledException e) {
                loading.set(false);
                return;
            } catch (Exception e) {
                logError = e;
            }

            String finalLogs = logs;
            WorkflowJob finalFreshJob = freshJob;
            Exception finalLogError = logError;
            Exception finalJobError = jobError;
            loading.set(false);

            ApplicationManager.getApplication().invokeLater(() -> {
                if (disposed || project.isDisposed()) {
                    return;
                }
                if (generation.get() != expectedGeneration || current != selection) {
                    // A new job was selected while this request was in flight. Its
                    // initial load was skipped by the in-flight guard, so start it now.
                    loadLogs(generation.get());
                    return;
                }
                onLogLoaded(selection, finalFreshJob, finalLogs, finalLogError, finalJobError);
            });
        });
    }

    private void onLogLoaded(@NotNull JobSelection selection,
                             @Nullable WorkflowJob freshJob,
                             @Nullable String logs,
                             @Nullable Exception logError,
                             @Nullable Exception jobError) {
        if (freshJob != null) {
            selection.job = freshJob;
        }
        String title = buildTitle(selection);
        GiteaStatus status = selection.job.state();
        boolean jobUnavailable = jobError instanceof GiteaApiException jobApiError
                && (jobApiError.isAuthenticationError() || jobApiError.isNotFound());

        if (logs != null) {
            lastRawLog = logs;
            String display = clean(logs);
            String statusText = status.displayName() + " • updated " + LocalTime.now().format(TIME_FORMAT);
            if (status.isActive() && !jobUnavailable) {
                statusText += " • auto-refresh every " + (REFRESH_INTERVAL_MS / 1000) + "s";
            }
            panel.showLogs(title, display, statusText);

            if (status.isActive() && !jobUnavailable) {
                schedule(selection.generation);
            } else {
                panel.setStatus(statusText, status == GiteaStatus.FAILED);
            }
            return;
        }

        if (logError instanceof GiteaApiException apiException) {
            if (apiException.isAuthenticationError()) {
                panel.showError(title, "Cannot load job log:\n\n" + apiException.getMessage(), "Authentication error");
                return;
            }
            if (apiException.isNotFound()) {
                if (status.isActive() && !jobUnavailable) {
                    if (panel.hasText()) {
                        panel.setStatus("Log is not available yet; retrying...", false);
                    } else {
                        panel.showMessage(title, "Waiting for the job log...", "Log not available yet");
                    }
                    schedule(selection.generation);
                } else {
                    panel.showMessage(
                            title,
                            "Log is not available. The job may not have started, its log may have been cleaned up, or the job was skipped.",
                            "Log not found"
                    );
                }
                return;
            }
        }

        String message = logError == null ? "Unknown log error" : safeMessage(logError);
        if (jobError != null) {
            message += "\n\nJob status refresh failed: " + safeMessage(jobError);
        }
        if (panel.hasText()) {
            panel.setStatus("Log temporarily unavailable: " + message, true);
        } else {
            panel.showError(title, "Cannot load job log:\n\n" + message, "Log temporarily unavailable");
        }
        if (status.isActive() && !jobUnavailable) {
            schedule(selection.generation);
        }
    }

    private void schedule(long expectedGeneration) {
        if (disposed || project.isDisposed() || current == null || generation.get() != expectedGeneration) {
            return;
        }
        alarm.cancelAllRequests();
        alarm.addRequest(() -> loadLogs(expectedGeneration), REFRESH_INTERVAL_MS);
    }

    private static @NotNull String buildTitle(@NotNull JobSelection selection) {
        return selection.server.displayName() + " / " + selection.repository.displayName()
                + " / " + selection.job.displayName();
    }

    private static @NotNull String safeMessage(@NotNull Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    public static @NotNull String clean(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String withoutAnsi = ANSI_ESCAPE.matcher(raw).replaceAll("");
        return withoutAnsi.replace("\r\n", "\n").replace('\r', '\n');
    }

    public @NotNull String getLastRawLog() {
        return lastRawLog;
    }

    @Override
    public void dispose() {
        disposed = true;
        current = null;
        generation.incrementAndGet();
        alarm.cancelAllRequests();
        alarm.dispose();
    }

    private static final class JobSelection {
        private final long generation;
        private final ActionsConnection server;
        private final Repository repository;
        private volatile WorkflowJob job;

        private JobSelection(long generation,
                             ActionsConnection server,
                             Repository repository,
                             WorkflowJob job) {
            this.generation = generation;
            this.server = server;
            this.repository = repository;
            this.job = job;
        }

        private boolean isSameJob(ActionsConnection server, Repository repository, long jobId) {
            return this.server.id().equals(server.id())
                    && this.repository.displayName().equals(repository.displayName())
                    && this.job.id() == jobId;
        }
    }
}
