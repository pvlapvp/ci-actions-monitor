package ru.pvlapvp.ciactionsmonitor.service;

import com.intellij.openapi.components.Service;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-project UI state that is not persisted: how many pages of runs/jobs the user
 * has explicitly loaded. The Services tree itself restores expansion/selection by
 * stable descriptor ids.
 */
@Service(Service.Level.PROJECT)
public final class TreeStateService {

    private final Map<String, Integer> loadedPages = new ConcurrentHashMap<>();

    public static @NotNull TreeStateService getInstance(@NotNull com.intellij.openapi.project.Project project) {
        return project.getService(TreeStateService.class);
    }

    public int getPageCount(@NotNull String nodeId) {
        return Math.max(1, loadedPages.getOrDefault(nodeId, 1));
    }

    public int incrementPage(@NotNull String nodeId) {
        return loadedPages.merge(nodeId, 1, Integer::sum);
    }

    public void resetAll() {
        loadedPages.clear();
    }
}
