package com.power.fx.core.cache;

import com.power.fx.api.model.LocalDateRange;

import java.util.HashMap;
import java.util.Map;

/**
 * Periodic hot-window maintenance (S8.4): prunes fixing entries outside
 * {@code [today - fixingHotWindowYears, today]} (the "today" bound is
 * host-supplied per D-01 -- never read from a clock in {@code fx-core}).
 * Snapshot-count eviction is already handled by {@link
 * InMemoryMarketSnapshotStore}'s own LRU bound, so this class only prunes
 * the {@link FixingView}.
 *
 * @see "Tech spec S8.4"
 */
public final class HotWindowPolicy {

    private HotWindowPolicy() {
    }

    public static FixingView prune(FixingView view, LocalDateRange newWindow) {
        Map<SeriesKey, FixingSeries> pruned = new HashMap<>();
        view.all().forEach((key, series) -> pruned.put(key, series.prunedTo(newWindow)));
        return new FixingView(pruned, newWindow, view.generation());
    }
}
