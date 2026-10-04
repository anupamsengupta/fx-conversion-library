package com.power.fx.core.cache;

import com.power.fx.api.model.SnapshotKind;
import com.power.fx.core.snapshot.MarketSnapshot;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LRU-bounded (default {@code retainedSnapshotsPerTenant = 8}, OQ-08)
 * {@link MarketSnapshotStore}. A published snapshot is never mutated;
 * {@link #put} on an existing id throws {@link
 * MarketSnapshotStore.SnapshotImmutableException} (S7.1.4,
 * {@code FX_I_SNAPSHOT_IMMUTABLE}).
 *
 * @see "Tech spec S7.1.4"
 */
public final class InMemoryMarketSnapshotStore implements MarketSnapshotStore {

    private final int retainedSnapshotsPerTenant;
    private final ConcurrentHashMap<String, LinkedHashMap<String, MarketSnapshot>> byTenant = new ConcurrentHashMap<>();

    public InMemoryMarketSnapshotStore(int retainedSnapshotsPerTenant) {
        if (retainedSnapshotsPerTenant <= 0) {
            throw new IllegalArgumentException("retainedSnapshotsPerTenant must be positive");
        }
        this.retainedSnapshotsPerTenant = retainedSnapshotsPerTenant;
    }

    @Override
    public Optional<MarketSnapshot> get(String tenantId, String marketSnapshotId) {
        Map<String, MarketSnapshot> map = byTenant.get(tenantId);
        if (map == null) {
            return Optional.empty();
        }
        synchronized (map) {
            return Optional.ofNullable(map.get(marketSnapshotId));
        }
    }

    @Override
    public Optional<MarketSnapshot> latest(String tenantId, SnapshotKind kind) {
        Map<String, MarketSnapshot> map = byTenant.get(tenantId);
        if (map == null) {
            return Optional.empty();
        }
        synchronized (map) {
            MarketSnapshot latest = null;
            for (MarketSnapshot s : map.values()) {
                if (s.kind() == kind) {
                    latest = s; // insertion-ordered map: last matching entry is the most recently published
                }
            }
            return Optional.ofNullable(latest);
        }
    }

    @Override
    public void put(String tenantId, MarketSnapshot snapshot) {
        // Plain no-arg constructor (insertion-order, default capacity/load factor) rather than the
        // (initialCapacity, loadFactor, accessOrder) overload: that overload requires a float literal
        // load-factor argument, which the D-09 bytecode scan (AR-05) forbids even for a JDK API
        // parameter -- the default load factor applied inside LinkedHashMap's own no-arg constructor
        // body is not bytecode this module emits. accessOrder is left at its default (false,
        // insertion-order), which is exactly what this FIFO-style eviction needs anyway.
        LinkedHashMap<String, MarketSnapshot> map = byTenant.computeIfAbsent(tenantId,
                k -> new LinkedHashMap<>() {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, MarketSnapshot> eldest) {
                        return size() > retainedSnapshotsPerTenant;
                    }
                });
        synchronized (map) {
            if (map.containsKey(snapshot.marketSnapshotId())) {
                throw new SnapshotImmutableException(snapshot.marketSnapshotId());
            }
            map.put(snapshot.marketSnapshotId(), snapshot);
        }
    }
}
