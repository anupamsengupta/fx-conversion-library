package com.power.fx.testkit.fixtures;

import com.power.fx.api.model.Scope;
import com.power.fx.api.model.SignOffStatus;
import com.power.fx.api.model.SnapshotKind;
import com.power.fx.core.snapshot.MarketSnapshot;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * Minimal {@link MarketSnapshot} builders for golden vector tests (Task
 * 3b.3). The G/C/F/X vectors resolve rates from the bitemporal fixing
 * store, not from curve pillars inside a snapshot, so most golden
 * snapshots carry no spots/pillars/discount curves of their own -- the
 * snapshot's role here is purely to supply a {@code marketSnapshotId},
 * {@code asOfDate} and {@code fixingKnowledgeCut} to pin against.
 */
public final class GoldenSnapshots {

    private GoldenSnapshots() {
    }

    public static MarketSnapshot eod(String marketSnapshotId, String tenantId, LocalDate asOfDate, Instant knowledgeCut) {
        return new MarketSnapshot(marketSnapshotId, Scope.TENANT, tenantId, SnapshotKind.EOD, asOfDate, knowledgeCut,
                SignOffStatus.SIGNED_OFF, Map.of(), Map.of(), Map.of(), Map.of());
    }

    public static MarketSnapshot unsigned(String marketSnapshotId, String tenantId, LocalDate asOfDate, Instant knowledgeCut) {
        return new MarketSnapshot(marketSnapshotId, Scope.TENANT, tenantId, SnapshotKind.EOD, asOfDate, knowledgeCut,
                SignOffStatus.UNSIGNED, Map.of(), Map.of(), Map.of(), Map.of());
    }
}
