package com.power.fx.core.snapshot;

import com.power.fx.api.model.MarketSnapshotPayload;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stages incoming snapshot chunks keyed by {@code marketSnapshotId}; the
 * snapshot becomes resolvable only when the completion marker has arrived
 * and all {@code chunkCount} chunks are present (S6.3, S7.1.4). The
 * staging area is never visible to readers -- only {@link #accept} ever
 * returns the assembled chunk list, and only once, on the call that
 * completes it.
 *
 * @see "Tech spec S7.1.4"
 */
public final class SnapshotCompletionTracker {

    private static final class Staging {
        final Map<Integer, MarketSnapshotPayload> chunksByIndex = new TreeMap<>();
        volatile int chunkCount = -1;
        volatile boolean completionMarkerSeen = false;
    }

    private final ConcurrentHashMap<String, Staging> staging = new ConcurrentHashMap<>();

    /**
     * Accepts one chunk. Returns the complete, ordered chunk list exactly
     * once -- on the call (whichever arrives last) that satisfies both
     * "completion marker seen" and "all {@code chunkCount} distinct
     * indexes present". Idempotent on {@code chunkIndex}: resubmitting an
     * already-seen index simply overwrites it (S8.2 step 2 dedupe happens
     * one layer up; this call is safe either way).
     */
    public synchronized Optional<List<MarketSnapshotPayload>> accept(MarketSnapshotPayload chunk) {
        Staging s = staging.computeIfAbsent(chunk.marketSnapshotId(), k -> new Staging());
        s.chunksByIndex.put(chunk.chunkIndex(), chunk);
        if (chunk.chunkCount() > 0) {
            s.chunkCount = chunk.chunkCount();
        }
        if (chunk.completionMarker()) {
            s.completionMarkerSeen = true;
        }
        if (s.completionMarkerSeen && s.chunkCount > 0 && s.chunksByIndex.size() >= s.chunkCount) {
            staging.remove(chunk.marketSnapshotId());
            return Optional.of(List.copyOf(s.chunksByIndex.values()));
        }
        return Optional.empty();
    }

    public boolean isIncomplete(String marketSnapshotId) {
        return staging.containsKey(marketSnapshotId);
    }
}
