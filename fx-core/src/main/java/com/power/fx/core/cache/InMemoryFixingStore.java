package com.power.fx.core.cache;

import com.power.fx.api.model.LocalDateRange;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lock-free-read {@link FixingStore}. See {@link InMemoryReferenceStore}
 * for the same single-writer-serialised, atomic-swap-read design (S10.4).
 */
public final class InMemoryFixingStore implements FixingStore {

    private final ConcurrentHashMap<String, AtomicReference<FixingView>> tenants = new ConcurrentHashMap<>();

    @Override
    public FixingView view(String tenantId) {
        AtomicReference<FixingView> ref = tenants.get(tenantId);
        return ref == null ? null : ref.get();
    }

    @Override
    public long generation(String tenantId) {
        FixingView v = view(tenantId);
        return v == null ? -1 : v.generation();
    }

    @Override
    public boolean swap(String tenantId, FixingView next) {
        AtomicReference<FixingView> ref = tenants.computeIfAbsent(tenantId, k -> new AtomicReference<>());
        FixingView prev = ref.get();
        return ref.compareAndSet(prev, next);
    }

    @Override
    public LocalDateRange loadedWindow(String tenantId) {
        FixingView v = view(tenantId);
        return v == null ? null : v.window();
    }
}
