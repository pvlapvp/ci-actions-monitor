package com.company.gitea.service;

import com.company.gitea.api.GiteaApiClient.GiteaApiException;
import com.company.gitea.model.ActionsConnection;
import com.company.gitea.model.GiteaStatus;
import com.company.gitea.model.Repository;
import com.company.gitea.model.WorkflowJob;
import com.company.gitea.provider.JobLogStream;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * Owns the shared log panel and refreshes the log of the selected running job.
 * <p>
 * The log is read as a stream, capped at {@link #MAX_LOG_BYTES}, and the document is
 * replaced on every update, so lines are not duplicated. Polling stops when the job
 * finishes, when another job is selected, or when the project is closed.
 */
@Service(Service.Level.PROJECT)
public final class LogsService implements Disposable {

    private static final int REFRESH_INTERVAL_MS = 4_000;
    private static final int LOG_READ_BUFFER_BYTES = 64 * 1024;
    private static final int MAX_LOG_BYTES = 10 * 1024 * 1024;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Pattern ANSI_ESCAPE = Pattern.compile("\u001B\\[[;\\d]*[ -/]*[@-~]");

    private final Project project;
    private final ActionsMonitorService monitorService;
    private final LogsPanel panel = new LogsPanel();
    private final Alarm alarm;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicBoolean loading = new AtomicBoolean();
    private volatile JobSelection current;
    private volatile JobLogStream activeStream;
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
    public void selectJob(@NotNull ActionsConnection connection,
                          @NotNull Repository repository,
                          @NotNull WorkflowJob job) {
        if (disposed) {
            return;
        }

        JobSelection selection = current;
        if (selection != null && selection.isSameJob(connection, repository, job.id())) {
            selection.job = job;
            if (job.isActive()) {
                schedule(selection.generation);
            }
            return;
        }

        long newGeneration = generation.incrementAndGet();
        closeActiveStream();
        selection = new JobSelection(newGeneration, connection, repository, job);
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
            closeActiveStream();
        }
    }

    public void reloadCurrent() {
        JobSelection selection = current;
        if (selection == null || disposed) {
            return;
        }
        alarm.cancelAllRequests();
        closeActiveStream();
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
            boolean truncated = false;
            boolean cancelled = false;
            Exception jobError = null;
            Exception logError = null;

            try {
                try {
                    freshJob = monitorService.loadJob(selection.connection, selection.repository, selection.job.id());
                } catch (ProcessCanceledException e) {
                    return;
                } catch (Exception e) {
                    jobError = e;
                }

                JobLogStream logStream = null;
                try {
                    logStream = monitorService.openJobLogs(
                            selection.connection,
                            selection.repository,
                            selection.job.id()
                    );
                    if (isCancelled(selection, expectedGeneration)) {
                        cancelled = true;
                    } else {
                        activeStream = logStream;
                        LogRead result = readLog(selection, expectedGeneration, logStream);
                        if (result == null) {
                            cancelled = true;
                        } else {
                            logs = result.text();
                            truncated = result.truncated();
                        }
                    }
                } catch (ProcessCanceledException e) {
                    cancelled = true;
                } catch (Exception e) {
                    logError = e;
                } finally {
                    if (logStream != null) {
                        if (activeStream == logStream) {
                            activeStream = null;
                        }
                        closeQuietly(logStream);
                    }
                }
            } finally {
                loading.set(false);
            }

            String finalLogs = logs;
            boolean finalTruncated = truncated;
            boolean finalCancelled = cancelled;
            WorkflowJob finalFreshJob = freshJob;
            Exception finalLogError = logError;
            Exception finalJobError = jobError;

            ApplicationManager.getApplication().invokeLater(() -> {
                if (disposed || project.isDisposed()) {
                    return;
                }
                if (generation.get() != expectedGeneration || current != selection) {
                    // A new job was selected while this request was in flight. Its
                    // initial load was skipped by the in-flight guard, so start it now.
                    if (current != null) {
                        loadLogs(generation.get());
                    }
                    return;
                }
                if (finalCancelled) {
                    return;
                }
                onLogLoaded(selection, finalFreshJob, finalLogs, finalTruncated,
                        finalLogError, finalJobError);
            });
        });
    }

    private @Nullable LogRead readLog(@NotNull JobSelection selection,
                                       long expectedGeneration,
                                       @NotNull JobLogStream logStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[LOG_READ_BUFFER_BYTES];
        boolean truncated = false;

        try (InputStream input = logStream.stream()) {
            while (true) {
                if (isCancelled(selection, expectedGeneration)) {
                    return null;
                }
                int read = input.read(chunk);
                if (read < 0) {
                    break;
                }
                int remaining = MAX_LOG_BYTES - buffer.size();
                if (remaining <= 0) {
                    truncated = true;
                    break;
                }
                int toWrite = Math.min(read, remaining);
                buffer.write(chunk, 0, toWrite);
                if (toWrite < read || buffer.size() >= MAX_LOG_BYTES) {
                    truncated = true;
                    break;
                }
            }
        }

        return new LogRead(buffer.toString(StandardCharsets.UTF_8), truncated);
    }

    private boolean isCancelled(@NotNull JobSelection selection, long expectedGeneration) {
        return disposed
                || project.isDisposed()
                || Thread.currentThread().isInterrupted()
                || generation.get() != expectedGeneration
                || current != selection;
    }

    private void onLogLoaded(@NotNull JobSelection selection,
                             @Nullable WorkflowJob freshJob,
                             @Nullable String logs,
                             boolean truncated,
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
            if (truncated) {
                statusText += " • log truncated at " + (MAX_LOG_BYTES / (1024 * 1024)) + " MB";
            }
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

    private void closeActiveStream() {
        JobLogStream stream = activeStream;
        activeStream = null;
        if (stream != null) {
            closeQuietly(stream);
        }
    }

    private static void closeQuietly(@NotNull JobLogStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // Best effort: the selection is already changing.
        }
    }

    private static @NotNull String buildTitle(@NotNull JobSelection selection) {
        return selection.connection.displayName() + " / " + selection.repository.displayName()
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
        closeActiveStream();
        alarm.dispose();
    }

    private record LogRead(@NotNull String text, boolean truncated) {
    }

    private static final class JobSelection {
        private final long generation;
        private final ActionsConnection connection;
        private final Repository repository;
        private volatile WorkflowJob job;

        private JobSelection(long generation,
                             ActionsConnection connection,
                             Repository repository,
                             WorkflowJob job) {
            this.generation = generation;
            this.connection = connection;
            this.repository = repository;
            this.job = job;
        }

        private boolean isSameJob(ActionsConnection connection, Repository repository, long jobId) {
            return this.connection.id().equals(connection.id())
                    && this.repository.displayName().equals(repository.displayName())
                    && this.job.id() == jobId;
        }
    }
}
