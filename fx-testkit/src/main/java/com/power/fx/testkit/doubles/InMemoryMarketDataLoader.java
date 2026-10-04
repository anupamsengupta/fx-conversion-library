package com.power.fx.testkit.doubles;

import com.power.fx.api.ingest.MarketDataChangeSet;
import com.power.fx.api.ingest.MarketWatermark;
import com.power.fx.api.model.CurrencyPair;
import com.power.fx.api.model.FixingVersion;
import com.power.fx.api.model.LocalDateRange;
import com.power.fx.api.model.MarketSnapshotPayload;
import com.power.fx.api.spi.MarketDataLoader;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link MarketDataLoader} double (Task 3b.2): a host registers
 * the fixings/snapshots it would otherwise serve from a real market data
 * system.
 */
public final class InMemoryMarketDataLoader implements MarketDataLoader {

    private final ConcurrentHashMap<String, MarketSnapshotPayload> snapshotsById = new ConcurrentHashMap<>();
    private final List<FixingVersion> fixings = Collections.synchronizedList(new ArrayList<>());

    public void addSnapshot(MarketSnapshotPayload payload) {
        snapshotsById.put(payload.marketSnapshotId(), payload);
    }

    public void addFixing(FixingVersion version) {
        fixings.add(version);
    }

    @Override
    public Optional<MarketSnapshotPayload> loadSnapshot(String marketSnapshotId) {
        return Optional.ofNullable(snapshotsById.get(marketSnapshotId));
    }

    @Override
    public List<FixingVersion> loadFixings(Set<String> sourceCodes, Set<CurrencyPair> pairs, LocalDateRange dateRange,
            Instant knowledgeCut) {
        return fixings.stream()
                .filter(f -> sourceCodes.contains(f.sourceCode()))
                .filter(f -> pairs.contains(f.pair()))
                .filter(f -> !f.fixingDate().isBefore(dateRange.startInclusive())
                        && !f.fixingDate().isAfter(dateRange.endInclusive()))
                .filter(f -> !f.recordedAt().isAfter(knowledgeCut))
                .toList();
    }

    @Override
    public MarketDataChangeSet loadChangesSince(MarketWatermark watermark) {
        List<FixingVersion> changed = fixings.stream()
                .filter(f -> f.recordedAt().isAfter(watermark.recordedAtHighWatermark()))
                .toList();
        return new MarketDataChangeSet(changed, List.of(), watermark);
    }
}
