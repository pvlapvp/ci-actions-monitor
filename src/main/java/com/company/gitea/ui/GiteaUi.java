package com.company.gitea.ui;

import com.company.gitea.model.GiteaStatus;
import com.intellij.icons.AllIcons;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;
import java.awt.Color;

/**
 * Shared presentation helpers for Gitea nodes.
 */
final class GiteaUi {

    private GiteaUi() {
    }

    static @NotNull Icon statusIcon(@NotNull GiteaStatus status) {
        return switch (status) {
            case SUCCESS -> AllIcons.RunConfigurations.TestState.Green2;
            case FAILED -> AllIcons.RunConfigurations.TestState.Red2;
            case IN_PROGRESS -> AllIcons.RunConfigurations.TestState.Run;
            case QUEUED, WAITING -> AllIcons.RunConfigurations.TestState.Yellow2;
            case ACTION_REQUIRED -> AllIcons.General.Warning;
            case CANCELLED -> AllIcons.Actions.Cancel;
            case SKIPPED, NEUTRAL, STALE, UNKNOWN -> AllIcons.General.Information;
        };
    }

    static @NotNull Color statusColor(@NotNull GiteaStatus status) {
        return switch (status) {
            case SUCCESS -> JBColor.GREEN;
            case FAILED -> JBColor.RED;
            case IN_PROGRESS -> JBColor.BLUE;
            case QUEUED, WAITING, ACTION_REQUIRED -> JBColor.ORANGE;
            case CANCELLED, SKIPPED, NEUTRAL, STALE, UNKNOWN -> JBColor.GRAY;
        };
    }
}
