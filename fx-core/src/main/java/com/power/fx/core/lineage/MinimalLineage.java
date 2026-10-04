package com.power.fx.core.lineage;

import com.power.fx.api.FxVersion;
import com.power.fx.api.model.SignOffStatus;
import com.power.fx.api.request.FxRequestContext;
import com.power.fx.api.result.Lineage;
import com.power.fx.api.result.ReplayableRequest;

import java.time.Instant;
import java.util.Collections;
import java.util.TreeMap;

/**
 * Builds a placeholder {@link Lineage} for error paths where a full pin
 * (or even a tenant) was never established -- e.g. {@code
 * FX_E_NO_TENANT_CONTEXT} fails before any {@code PinnedState} exists.
 * Every {@code FxResult} implementation requires a non-null {@code
 * Lineage} even on its error path (Phase 1's own compact-constructor
 * contract), so this is the sanctioned sentinel rather than a {@code null}
 * that would violate it. Never used on a success path.
 */
public final class MinimalLineage {

    private static final String API_SCHEMA_VERSION = "1.0";

    private MinimalLineage() {
    }

    public static Lineage of(String tenantId, String marketSnapshotId, Instant knowledgeCut,
            SignOffStatus signOff, String policyId, int policyVersion, FxRequestContext context) {
        ReplayableRequest replayKey = context == null
                ? new ReplayableRequest(null, null, Instant.EPOCH.atZone(java.time.ZoneOffset.UTC).toLocalDate(),
                        null, null, null, null, null, null, null, false, null, null, java.util.Map.of(), null, null, java.util.List.of())
                : new ReplayableRequest(context.purpose(), context.runMode(), context.valuationDate(), context.amountType(),
                        context.settlementAmountState(), context.itemType(), context.accountingUnitId(), null, null,
                        null, false, context.policy(), context.tradeDates(), context.events(), context.accountingDates(),
                        context.pricingSet(), context.prices());
        return new Lineage(
                tenantId == null ? "UNKNOWN" : tenantId,
                marketSnapshotId == null ? "UNKNOWN" : marketSnapshotId,
                knowledgeCut == null ? Instant.EPOCH : knowledgeCut,
                -1L, -1L,
                signOff == null ? SignOffStatus.UNSIGNED : signOff,
                policyId == null ? "UNKNOWN" : policyId,
                policyVersion,
                null,
                FxVersion.VALUE,
                API_SCHEMA_VERSION,
                null,
                Collections.unmodifiableSortedMap(new TreeMap<>()),
                replayKey,
                () -> "{}");
    }
}
