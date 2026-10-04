package com.power.fx.core;

import com.power.fx.api.FxHealth;
import com.power.fx.api.model.SnapshotKind;
import com.power.fx.api.model.TenantHealthStatus;
import com.power.fx.core.cache.MarketSnapshotStore;
import com.power.fx.core.cache.ReferenceStore;

import java.util.Optional;
import java.util.Set;

/**
 * Readiness per tenant (S6.1). READY iff a reference catalogue generation
 * exists for the tenant. {@code latestSnapshotId} reports the most
 * recently published EOD snapshot once {@link DefaultFxIngestor} (Task
 * 2.17) has actually published one -- previously always empty because no
 * ingestion path existed to populate {@link MarketSnapshotStore}.
 */
public final class DefaultFxHealth implements FxHealth {

    private final ReferenceStore referenceStore;
    private final MarketSnapshotStore marketSnapshotStore;

    public DefaultFxHealth(ReferenceStore referenceStore, MarketSnapshotStore marketSnapshotStore) {
        this.referenceStore = referenceStore;
        this.marketSnapshotStore = marketSnapshotStore;
    }

    @Override
    public TenantHealthStatus status(String tenantId) {
        return referenceStore.catalogue(tenantId) != null ? TenantHealthStatus.READY : TenantHealthStatus.NOT_LOADED;
    }

    @Override
    public Optional<String> latestSnapshotId(String tenantId) {
        return marketSnapshotStore.latest(tenantId, SnapshotKind.EOD).map(com.power.fx.core.snapshot.MarketSnapshot::marketSnapshotId);
    }

    @Override
    public Set<String> staleKeys(String tenantId) {
        var cat = referenceStore.catalogue(tenantId);
        return cat == null ? Set.of() : cat.staleKeys();
    }
}
