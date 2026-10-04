package com.power.fx.core.cache;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lock-free-read {@link ReferenceStore}: one {@link AtomicReference} per
 * tenant plus one for GLOBAL. Writes are serialised by the caller
 * ({@code DefaultFxIngestor}'s per-tenant {@code ReentrantLock}, S10.4);
 * the {@link AtomicReference#compareAndSet} here is the structural,
 * mechanical guarantee that a reader never observes a torn generation.
 *
 * @see "Tech spec S7.1.2, S10.4"
 */
public final class InMemoryReferenceStore implements ReferenceStore {

    private final AtomicReference<ReferenceCatalogue> global = new AtomicReference<>();
    private final ConcurrentHashMap<String, AtomicReference<ReferenceCatalogue>> tenants = new ConcurrentHashMap<>();

    @Override
    public ReferenceCatalogue catalogue(String tenantId) {
        AtomicReference<ReferenceCatalogue> ref = tenants.get(tenantId);
        return ref == null ? null : ref.get();
    }

    @Override
    public ReferenceCatalogue cataloguePinned(String tenantId, Instant knowledgeCut) {
        return catalogue(tenantId);
    }

    @Override
    public long generation(String tenantId) {
        ReferenceCatalogue c = catalogue(tenantId);
        return c == null ? -1 : c.generation();
    }

    @Override
    public boolean swap(String tenantId, ReferenceCatalogue next) {
        AtomicReference<ReferenceCatalogue> ref = tenants.computeIfAbsent(tenantId, k -> new AtomicReference<>());
        ReferenceCatalogue prev = ref.get();
        return ref.compareAndSet(prev, next);
    }

    @Override
    public ReferenceCatalogue global() {
        return global.get();
    }

    @Override
    public boolean swapGlobal(ReferenceCatalogue next) {
        ReferenceCatalogue prev = global.get();
        return global.compareAndSet(prev, next);
    }
}
