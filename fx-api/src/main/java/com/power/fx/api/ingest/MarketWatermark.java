package com.power.fx.api.ingest;

import java.time.Instant;
import java.util.Objects;

/**
 * The high-watermark a host passes to {@code
 * MarketDataLoader.loadChangesSince}.
 *
 * @see "Tech spec S4.10"
 */
public record MarketWatermark(String tenantId, Instant recordedAtHighWatermark, String lastSnapshotId) {

    public MarketWatermark {
        Objects.requireNonNull(recordedAtHighWatermark, "recordedAtHighWatermark must not be null");
    }
}
