package com.power.fx.core.snapshot;

import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.CurrencyPair;
import com.power.fx.api.model.DiscountCurvePayload;
import com.power.fx.api.model.ForwardPillar;
import com.power.fx.api.model.MarketSnapshotPayload;
import com.power.fx.api.model.SpotQuote;
import com.power.fx.core.curve.DiscountCurve;
import com.power.fx.core.decimal.FxMath;
import com.power.fx.core.entitlement.RightsSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Assembles one or more completed {@link MarketSnapshotPayload} chunks
 * (per {@link SnapshotCompletionTracker}) into one immutable {@link
 * MarketSnapshot} (S6.3, S7.1.4).
 *
 * <p><strong>TI-08 limitation, applied here as instructed:</strong> {@link
 * MarketSnapshotPayload} carries no per-pair source-code field, so this
 * assembler cannot compute true per-pillar source provenance for {@code
 * MarketSnapshot.sourceRights}. It accepts a single, caller-supplied
 * snapshot-level {@link RightsSet} assumption (the same rights object
 * applied to every pair in the snapshot) rather than inventing a per-pair
 * field the data model does not support -- this is the example the
 * architect flagged explicitly (plan Section 8.3, TI-08); forward/CIP
 * restriction propagation (closed in {@code RestrictionPropagator}, Tasks
 * 2.8/2.11) can therefore only ever be as precise as this one assumption.
 */
public final class SnapshotAssembler {

    private final FxMath fxMath;

    public SnapshotAssembler(FxMath fxMath) {
        this.fxMath = fxMath;
    }

    public MarketSnapshot assemble(List<MarketSnapshotPayload> chunks, RightsSet snapshotLevelSourceRights) {
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("at least one chunk is required");
        }
        MarketSnapshotPayload first = chunks.get(0);

        Map<CurrencyPair, SpotQuote> spots = new HashMap<>();
        Map<CurrencyPair, List<ForwardPillar>> pillars = new HashMap<>();
        Map<CurrencyCode, DiscountCurve> discountCurves = new HashMap<>();

        for (MarketSnapshotPayload chunk : chunks) {
            for (SpotQuote q : chunk.spots()) {
                spots.put(q.pair(), q);
            }
            chunk.forwards().forEach((pair, pillarList) -> {
                List<ForwardPillar> sorted = new ArrayList<>(pillarList);
                sorted.sort((a, b) -> a.valueDate().compareTo(b.valueDate()));
                pillars.put(pair, List.copyOf(sorted));
            });
            for (DiscountCurvePayload dcp : chunk.discountCurves()) {
                discountCurves.put(dcp.currency(), DiscountCurve.of(dcp, fxMath));
            }
        }

        Map<CurrencyPair, RightsSet> sourceRights = new HashMap<>();
        for (CurrencyPair p : spots.keySet()) {
            sourceRights.put(p, snapshotLevelSourceRights);
        }
        for (CurrencyPair p : pillars.keySet()) {
            sourceRights.putIfAbsent(p, snapshotLevelSourceRights);
        }

        return new MarketSnapshot(first.marketSnapshotId(), first.scope(), first.tenantId(), first.kind(),
                first.asOfDate(), first.fixingKnowledgeCut(), first.signOffStatus(), spots, pillars, discountCurves,
                sourceRights);
    }
}
