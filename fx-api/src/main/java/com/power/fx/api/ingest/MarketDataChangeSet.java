package com.power.fx.api.ingest;

import com.power.fx.api.model.FixingVersion;
import com.power.fx.api.model.MarketSnapshotPayload;

import java.util.List;
import java.util.Objects;

/**
 * The result of {@code MarketDataLoader.loadChangesSince}.
 *
 * @see "Tech spec S4.10"
 */
public record MarketDataChangeSet(
        List<FixingVersion> fixings,
        List<MarketSnapshotPayload> snapshots,
        MarketWatermark watermark) {

    public MarketDataChangeSet {
        Objects.requireNonNull(watermark, "watermark must not be null");
        fixings = fixings == null ? List.of() : List.copyOf(fixings);
        snapshots = snapshots == null ? List.of() : List.copyOf(snapshots);
    }
}
