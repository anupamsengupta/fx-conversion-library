package com.power.fx.api.spi;

import com.power.fx.api.error.FxErrorCode;
import com.power.fx.api.error.FxIngestCode;
import com.power.fx.api.error.FxWarningCode;
import com.power.fx.api.model.FallbackStepKind;
import com.power.fx.api.model.FxStoreKind;
import com.power.fx.api.model.Purpose;

/**
 * Stateless, side-effect-free {@link FxMetrics} singleton, safe to call
 * concurrently from any thread.
 */
final class NoopFxMetrics implements FxMetrics {

    static final NoopFxMetrics INSTANCE = new NoopFxMetrics();

    private NoopFxMetrics() {
    }

    @Override
    public void ingestApplied(String tenantId, FxStoreKind store, int count) {
    }

    @Override
    public void ingestRejected(String tenantId, FxIngestCode code) {
    }

    @Override
    public void generationAdvanced(String tenantId, FxStoreKind store, long generation) {
    }

    @Override
    public void snapshotPinned(String tenantId, String marketSnapshotId) {
    }

    @Override
    public void memoHit(String tenantId) {
    }

    @Override
    public void memoMiss(String tenantId) {
    }

    @Override
    public void curveBuilt(String tenantId, String marketSnapshotId, long nanos) {
    }

    @Override
    public void resolutionLatency(String tenantId, Purpose purpose, long nanos) {
    }

    @Override
    public void resolutionError(String tenantId, FxErrorCode code) {
    }

    @Override
    public void resolutionWarning(String tenantId, FxWarningCode code) {
    }

    @Override
    public void fallbackUsed(String tenantId, FallbackStepKind kind) {
    }
}
