package com.power.fx.testkit.doubles;

import com.power.fx.api.ingest.FxIngestRecord;
import com.power.fx.api.model.FxEntityType;
import com.power.fx.api.spi.ReferenceDataLoader;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link ReferenceDataLoader} double (Task 3b.2): a host
 * registers the records it would otherwise serve from a real reference
 * data system, and this loader replays them on {@link #loadGlobal()} /
 * {@link #loadTenant(String)} / {@link #loadChangesSince} / {@link
 * #loadKey}. Records a call log so tests can assert {@code
 * DefaultFxIngestor}'s {@code FX_I_SEQUENCE_GAP} handling actually invoked
 * {@link #loadKey} (S8.2 step 4).
 */
public final class InMemoryReferenceDataLoader implements ReferenceDataLoader {

    private final List<FxIngestRecord> global = Collections.synchronizedList(new ArrayList<>());
    private final ConcurrentHashMap<String, List<FxIngestRecord>> byTenant = new ConcurrentHashMap<>();
    private final List<LoadKeyCall> loadKeyCalls = Collections.synchronizedList(new ArrayList<>());

    public record LoadKeyCall(String tenantId, FxEntityType entityType, String naturalKey) {
    }

    public void addGlobal(FxIngestRecord record) {
        global.add(Objects.requireNonNull(record));
    }

    public void addTenant(String tenantId, FxIngestRecord record) {
        byTenant.computeIfAbsent(tenantId, k -> Collections.synchronizedList(new ArrayList<>())).add(record);
    }

    public List<LoadKeyCall> loadKeyCalls() {
        return List.copyOf(loadKeyCalls);
    }

    @Override
    public List<FxIngestRecord> loadGlobal() {
        return List.copyOf(global);
    }

    @Override
    public List<FxIngestRecord> loadTenant(String tenantId) {
        return List.copyOf(byTenant.getOrDefault(tenantId, List.of()));
    }

    @Override
    public List<FxIngestRecord> loadChangesSince(String tenantId, Instant watermark) {
        return loadTenant(tenantId).stream().filter(r -> r.publishedAt().isAfter(watermark)).toList();
    }

    @Override
    public List<FxIngestRecord> loadKey(String tenantId, FxEntityType entityType, String naturalKey) {
        loadKeyCalls.add(new LoadKeyCall(tenantId, entityType, naturalKey));
        return loadTenant(tenantId).stream()
                .filter(r -> r.entityType() == entityType && r.naturalKey().equals(naturalKey))
                .toList();
    }
}
