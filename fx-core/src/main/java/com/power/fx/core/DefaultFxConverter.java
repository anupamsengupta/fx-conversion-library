package com.power.fx.core;

import com.power.fx.api.FxConfig;
import com.power.fx.api.FxHealth;
import com.power.fx.api.FxSnapshot;
import com.power.fx.api.error.FxErrorCode;
import com.power.fx.api.model.RunMode;
import com.power.fx.api.model.SnapshotKind;
import com.power.fx.api.model.TenantHealthStatus;
import com.power.fx.api.request.FxRequestContext;
import com.power.fx.api.spi.TenantContextProvider;
import com.power.fx.core.cache.FixingStore;
import com.power.fx.core.cache.MarketSnapshotStore;
import com.power.fx.core.cache.ReferenceStore;
import com.power.fx.core.leg.ChainEngine;
import com.power.fx.core.leg.RevaluationEngine;
import com.power.fx.core.memo.ResolutionMemo;
import com.power.fx.core.snapshot.MarketSnapshot;
import com.power.fx.core.snapshot.PinnedState;

import java.time.Instant;

/**
 * {@code FxConverter}'s facade entry point (Pattern #14): resolves tenant
 * and pin fresh on every call (stages 1-2), then dispatches to {@link
 * ConversionPipeline}/{@link ChainEngine}/{@link RevaluationEngine}.
 *
 * @see "Tech spec S6.1, S6.3, Appendix C"
 */
public final class DefaultFxConverter extends AbstractFxConverter {

    private final TenantContextProvider tenantContextProvider;
    private final ReferenceStore referenceStore;
    private final FixingStore fixingStore;
    private final MarketSnapshotStore marketSnapshotStore;
    private final FxHealth health;

    public DefaultFxConverter(TenantContextProvider tenantContextProvider, ReferenceStore referenceStore,
            FixingStore fixingStore, MarketSnapshotStore marketSnapshotStore, FxHealth health,
            ConversionPipeline pipeline, ChainEngine chainEngine, RevaluationEngine revaluationEngine, FxConfig config) {
        super(pipeline, chainEngine, revaluationEngine, config);
        this.tenantContextProvider = tenantContextProvider;
        this.referenceStore = referenceStore;
        this.fixingStore = fixingStore;
        this.marketSnapshotStore = marketSnapshotStore;
        this.health = health;
    }

    private String currentTenant() {
        return tenantContextProvider.currentTenant()
                .orElseThrow(() -> FxErrors.of(FxErrorCode.FX_E_NO_TENANT_CONTEXT, "no tenant in context"));
    }

    private void requireReady(String tenant) {
        if (health.status(tenant) != TenantHealthStatus.READY) {
            throw FxErrors.of(FxErrorCode.FX_E_TENANT_NOT_READY, "tenant not ready: " + tenant, "tenantId", tenant);
        }
    }

    @Override
    protected PinnedState resolvePin(FxRequestContext context) {
        String tenant = currentTenant();
        requireReady(tenant);

        if (context.snapshot() != null) {
            // OQ-T05: the request's own snapshot facade wins silently; only a tenant disagreement errors.
            if (!context.snapshot().tenantId().equals(tenant)) {
                throw FxErrors.of(FxErrorCode.FX_E_TENANT_MISMATCH,
                        "request snapshot tenant " + context.snapshot().tenantId() + " != context tenant " + tenant);
            }
            return ((PinnedFxSnapshot) context.snapshot()).state();
        }

        if (context.runMode() == RunMode.OFFICIAL || !config.allowImplicitPin()) {
            throw FxErrors.of(FxErrorCode.FX_V_INVALID_POLICY,
                    "an explicit snapshot is required (runMode=OFFICIAL or implicit pinning disabled)");
        }
        MarketSnapshot latest = latestSnapshot(tenant);
        if (latest == null) {
            throw FxErrors.of(FxErrorCode.FX_E_DATA_NOT_LOADED, "no snapshot available to implicitly pin for tenant " + tenant);
        }
        return buildPinnedState(tenant, latest, latest.fixingKnowledgeCut());
    }

    private MarketSnapshot latestSnapshot(String tenant) {
        return marketSnapshotStore.latest(tenant, SnapshotKind.EOD).orElse(null);
    }

    @Override
    protected String tenantIdForErrors() {
        return tenantContextProvider.currentTenant().orElse(null);
    }

    @Override
    public FxSnapshot pin(String marketSnapshotId) {
        String tenant = currentTenant();
        MarketSnapshot snap = marketSnapshotStore.get(tenant, marketSnapshotId)
                .orElseThrow(() -> FxErrors.of(FxErrorCode.FX_E_DATA_NOT_LOADED, "snapshot not found: " + marketSnapshotId));
        return new PinnedFxSnapshot(pipeline, chainEngine, revaluationEngine, config,
                buildPinnedState(tenant, snap, snap.fixingKnowledgeCut()), snap);
    }

    @Override
    public FxSnapshot pin(String marketSnapshotId, Instant knowledgeCut) {
        String tenant = currentTenant();
        MarketSnapshot snap = marketSnapshotStore.get(tenant, marketSnapshotId)
                .orElseThrow(() -> FxErrors.of(FxErrorCode.FX_E_DATA_NOT_LOADED, "snapshot not found: " + marketSnapshotId));
        return new PinnedFxSnapshot(pipeline, chainEngine, revaluationEngine, config,
                buildPinnedState(tenant, snap, knowledgeCut), snap);
    }

    private PinnedState buildPinnedState(String tenant, MarketSnapshot snapshot, Instant knowledgeCut) {
        var tenantCat = referenceStore.cataloguePinned(tenant, knowledgeCut);
        var fixings = fixingStore.view(tenant);
        return new PinnedState(tenant, referenceStore.global(), tenantCat, fixings, snapshot, knowledgeCut,
                tenantCat.generation(), fixings.generation(), new ResolutionMemo(config.memoMaxEntriesPerSnapshot()));
    }
}
