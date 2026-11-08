package ru.pvlapvp.ciactionsmonitor.provider;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * One page of provider data.
 *
 * @param totalCount total number of items, or {@code -1} if the provider does not report it
 * @param hasMore    whether another page may exist
 */
public record Page<T>(@NotNull List<T> items, int totalCount, boolean hasMore) {

    public Page {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public static <T> Page<T> single(@NotNull List<T> items) {
        return new Page<>(items, items.size(), false);
    }
}
