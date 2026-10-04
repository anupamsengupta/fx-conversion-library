package com.power.fx.core;

import com.power.fx.api.FxConfig;
import com.power.fx.api.FxSnapshot;
import com.power.fx.api.error.FxErrorCode;
import com.power.fx.api.model.SignOffStatus;
import com.power.fx.api.model.SnapshotKind;
import com.power.fx.api.request.FxRequestContext;
import com.power.fx.core.leg.ChainEngine;
import com.power.fx.core.leg.RevaluationEngine;
import com.power.fx.core.memo.ResolutionMemo;
import com.power.fx.core.snapshot.MarketSnapshot;
import com.power.fx.core.snapshot.PinnedState;

import java.time.Instant;
import java.time.LocalDate;

/**
 * An immutable handle over one {@link PinnedState} (Pattern #1 Value
 * Object + #14 Facade, S7.1.5, A-15). Every {@link
 * com.power.fx.api.FxConverter} method issued through this handle resolves
 * against exactly this pin -- no store is re-read.
 */
public final class PinnedFxSnapshot extends AbstractFxConverter implements FxSnapshot {

    private final PinnedState state;
    private final MarketSnapshot snapshot;

    public PinnedFxSnapshot(ConversionPipeline pipeline, ChainEngine chainEngine, RevaluationEngine revaluationEngine,
            FxConfig config, PinnedState state, MarketSnapshot snapshot) {
        super(pipeline, chainEngine, revaluationEngine, config);
        this.state = state;
        this.snapshot = snapshot;
    }

    /** Package-visible: {@code DefaultFxConverter} reads this when a request carries this facade as its snapshot (OQ-T05). */
    PinnedState state() {
        return state;
    }

    @Override
    protected PinnedState resolvePin(FxRequestContext context) {
        if (context.snapshot() != null && context.snapshot() != this) {
            if (!context.snapshot().tenantId().equals(state.tenantId())) {
                throw FxErrors.of(FxErrorCode.FX_E_TENANT_MISMATCH, "request snapshot tenant mismatch");
            }
            return ((PinnedFxSnapshot) context.snapshot()).state();
        }
        return state;
    }

    @Override
    protected String tenantIdForErrors() {
        return state.tenantId();
    }

    @Override
    public String tenantId() {
        return state.tenantId();
    }

    @Override
    public String marketSnapshotId() {
        return snapshot.marketSnapshotId();
    }

    @Override
    public SnapshotKind kind() {
        return snapshot.kind();
    }

    @Override
    public LocalDate asOfDate() {
        return snapshot.asOfDate();
    }

    @Override
    public Instant fixingKnowledgeCut() {
        return state.knowledgeCut();
    }

    @Override
    public SignOffStatus signOffStatus() {
        return snapshot.signOffStatus();
    }

    @Override
    public long referenceGeneration() {
        return state.refGeneration();
    }

    @Override
    public long fixingGeneration() {
        return state.fixingGeneration();
    }

    @Override
    public FxSnapshot pin(String marketSnapshotId) {
        if (marketSnapshotId.equals(snapshot.marketSnapshotId())) {
            return this;
        }
        throw FxErrors.of(FxErrorCode.FX_E_DATA_NOT_LOADED,
                "a pinned FxSnapshot cannot pin a different marketSnapshotId without store access; "
                        + "call FxConverter.pin(...) on the top-level converter instead");
    }

    @Override
    public FxSnapshot pin(String marketSnapshotId, Instant knowledgeCut) {
        if (marketSnapshotId.equals(snapshot.marketSnapshotId())) {
            PinnedState repinned = new PinnedState(state.tenantId(), state.global(), state.tenant(), state.fixings(),
                    snapshot, knowledgeCut, state.refGeneration(), state.fixingGeneration(),
                    new ResolutionMemo(config.memoMaxEntriesPerSnapshot()));
            return new PinnedFxSnapshot(pipeline, chainEngine, revaluationEngine, config, repinned, snapshot);
        }
        throw FxErrors.of(FxErrorCode.FX_E_DATA_NOT_LOADED,
                "a pinned FxSnapshot cannot pin a different marketSnapshotId without store access");
    }
}
