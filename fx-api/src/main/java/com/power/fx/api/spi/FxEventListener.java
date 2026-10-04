package com.power.fx.api.spi;

import com.power.fx.api.error.FxIngestCode;
import com.power.fx.api.ingest.FixingCorrection;
import com.power.fx.api.ingest.FxIngestRecord;
import com.power.fx.api.ingest.SnapshotAvailability;

/**
 * Host-implemented observer over ingest-time events (Pattern #18
 * Observer). All methods have no-op defaults; this interface has exactly
 * the three methods of FS S14.1 and no more. Generation advancement is
 * reported through {@link FxMetrics}, not here, so this contract stays
 * exactly as specified.
 *
 * @see "Tech spec S5.2"
 */
public interface FxEventListener {

    default void onFixingCorrected(FixingCorrection correction) {
    }

    default void onRejected(FxIngestRecord record, FxIngestCode code, String message) {
    }

    default void onSnapshotAvailable(SnapshotAvailability availability) {
    }

    static FxEventListener noop() {
        return new FxEventListener() {
        };
    }
}
