package com.power.fx.api.spi;

import com.power.fx.api.error.FxErrorCode;
import com.power.fx.api.error.FxIngestCode;
import com.power.fx.api.error.FxWarningCode;
import com.power.fx.api.model.FallbackStepKind;
import com.power.fx.api.model.FxStoreKind;
import com.power.fx.api.model.Purpose;

/**
 * Host-implemented metrics sink (Pattern #11 Strategy). {@code
 * resolutionLatency} is fed by {@code MeteredFxConverter} in {@code
 * fx-guice} (A-11); {@code fx-core} itself never calls {@code
 * System.nanoTime()}.
 *
 * @see "Tech spec S5.2"
 */
public interface FxMetrics {

    void ingestApplied(String tenantId, FxStoreKind store, int count);

    void ingestRejected(String tenantId, FxIngestCode code);

    void generationAdvanced(String tenantId, FxStoreKind store, long generation);

    void snapshotPinned(String tenantId, String marketSnapshotId);

    void memoHit(String tenantId);

    void memoMiss(String tenantId);

    void curveBuilt(String tenantId, String marketSnapshotId, long nanos);

    void resolutionLatency(String tenantId, Purpose purpose, long nanos);

    void resolutionError(String tenantId, FxErrorCode code);

    void resolutionWarning(String tenantId, FxWarningCode code);

    void fallbackUsed(String tenantId, FallbackStepKind kind);

    /** Returns a thread-safe, side-effect-free singleton no-op implementation. */
    static FxMetrics noop() {
        return NoopFxMetrics.INSTANCE;
    }
}
