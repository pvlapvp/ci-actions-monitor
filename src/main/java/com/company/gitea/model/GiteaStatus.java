package com.company.gitea.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * User-facing state of a workflow run, job or step.
 * <p>
 * Gitea and GitHub return GitHub-compatible values: {@code status} is one of
 * {@code queued}, {@code waiting}, {@code in_progress} or {@code completed}; for
 * completed entities the result is in {@code conclusion}. Unknown values must never
 * break the UI.
 */
public enum GiteaStatus {

    QUEUED("Queued"),
    WAITING("Waiting"),
    IN_PROGRESS("In progress"),

    SUCCESS("Success"),
    FAILED("Failed"),
    CANCELLED("Cancelled"),
    SKIPPED("Skipped"),

    NEUTRAL("Neutral"),
    ACTION_REQUIRED("Action required"),
    STALE("Stale"),

    UNKNOWN("Unknown");

    private final String displayName;

    GiteaStatus(@NotNull String displayName) {
        this.displayName = displayName;
    }

    public @NotNull String displayName() {
        return displayName;
    }

    /**
     * Whether a periodic refresh may bring a new state.
     */
    public boolean isActive() {
        return this == QUEUED || this == WAITING || this == IN_PROGRESS;
    }

    public boolean isFinished() {
        return this == SUCCESS
                || this == FAILED
                || this == CANCELLED
                || this == SKIPPED
                || this == NEUTRAL
                || this == ACTION_REQUIRED
                || this == STALE;
    }

    /**
     * Resolves a state from {@code status} and {@code conclusion}.
     * <p>
     * {@code conclusion} has priority, because {@code status=completed} alone says nothing
     * about success or failure.
     */
    public static @NotNull GiteaStatus from(@Nullable String status, @Nullable String conclusion) {
        String normalizedConclusion = normalize(conclusion);
        if (!normalizedConclusion.isEmpty()) {
            switch (normalizedConclusion) {
                case "success":
                    return SUCCESS;
                case "failure":
                case "failed":
                case "timed_out":
                case "timedout":
                case "startup_failure":
                    return FAILED;
                case "cancelled":
                case "canceled":
                    return CANCELLED;
                case "skipped":
                    return SKIPPED;
                case "neutral":
                    return NEUTRAL;
                case "action_required":
                    return ACTION_REQUIRED;
                case "stale":
                    return STALE;
                default:
                    break;
            }
        }

        String normalizedStatus = normalize(status);
        switch (normalizedStatus) {
            case "queued":
            case "pending":
            case "requested":
                return QUEUED;
            case "waiting":
            case "blocked":
                return WAITING;
            case "in_progress":
            case "in-progress":
            case "running":
            case "cancelling":
            case "canceling":
                return IN_PROGRESS;
            case "success":
            case "successful":
                return SUCCESS;
            case "failure":
            case "failed":
            case "timed_out":
            case "timedout":
            case "startup_failure":
                return FAILED;
            case "cancelled":
            case "canceled":
                return CANCELLED;
            case "skipped":
                return SKIPPED;
            case "neutral":
                return NEUTRAL;
            case "action_required":
                return ACTION_REQUIRED;
            case "stale":
                return STALE;
            case "completed":
            case "unknown":
            default:
                return UNKNOWN;
        }
    }

    private static @NotNull String normalize(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
