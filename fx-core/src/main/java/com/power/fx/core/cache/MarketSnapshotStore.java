package com.power.fx.core.cache;

import com.power.fx.api.model.SnapshotKind;
import com.power.fx.core.snapshot.MarketSnapshot;

import java.util.Optional;

/**
 * Internal port (S5.4, Pattern #21 Repository, in-memory, immutable,
 * LRU-bounded).
 */
public interface MarketSnapshotStore {

    Optional<MarketSnapshot> get(String tenantId, String marketSnapshotId);

    Optional<MarketSnapshot> latest(String tenantId, SnapshotKind kind);

    /** @throws SnapshotImmutableException if {@code marketSnapshotId} already exists for the tenant */
    void put(String tenantId, MarketSnapshot snapshot);

    final class SnapshotImmutableException extends RuntimeException {
        public SnapshotImmutableException(String marketSnapshotId) {
            super("marketSnapshotId already published and is immutable: " + marketSnapshotId);
        }
    }
}
