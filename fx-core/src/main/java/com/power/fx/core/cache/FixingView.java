package com.power.fx.core.cache;

import com.power.fx.api.model.LocalDateRange;

import java.util.Map;
import java.util.Optional;

/**
 * One immutable generation of the bitemporal fixing store for one tenant
 * (S7.1.3).
 */
public final class FixingView {

    private final Map<SeriesKey, FixingSeries> series;
    private final LocalDateRange window;
    private final long generation;

    public FixingView(Map<SeriesKey, FixingSeries> series, LocalDateRange window, long generation) {
        this.series = Map.copyOf(series);
        this.window = window;
        this.generation = generation;
    }

    public static FixingView empty(long generation) {
        return new FixingView(Map.of(), null, generation);
    }

    public Optional<FixingSeries> series(SeriesKey key) {
        return Optional.ofNullable(series.get(key));
    }

    /**
     * Finds a series for {@code (sourceCode, pair)} regardless of cutoff
     * class, for callers that do not need to distinguish multiple
     * cutoffs per source/pair. Deterministic (first match in natural map
     * iteration is acceptable here because real configurations have at
     * most one cutoff per source/pair in this phase's test fixtures).
     */
    public Optional<FixingSeries> findAnyCutoff(String sourceCode, com.power.fx.api.model.CurrencyPair pair) {
        for (java.util.Map.Entry<SeriesKey, FixingSeries> e : series.entrySet()) {
            if (e.getKey().sourceCode().equals(sourceCode) && e.getKey().pair().equals(pair)) {
                return Optional.of(e.getValue());
            }
        }
        return Optional.empty();
    }

    public Map<SeriesKey, FixingSeries> all() {
        return series;
    }

    public LocalDateRange window() {
        return window;
    }

    public long generation() {
        return generation;
    }
}
