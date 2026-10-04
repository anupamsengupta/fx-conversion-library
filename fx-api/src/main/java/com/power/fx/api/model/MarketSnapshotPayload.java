package com.power.fx.api.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One chunk of an immutable market snapshot. A snapshot becomes
 * resolvable only when {@code chunkCount} chunks plus the completion
 * marker have all arrived (S7.1.4).
 *
 * <p><strong>Accepted limitation (v1.0):</strong> this payload carries no
 * per-pair source-provenance field (TI-08); forward/CIP restriction
 * propagation in {@code fx-core} must therefore use a single
 * snapshot-level source-rights approximation rather than true per-pillar
 * provenance until a functional-spec amendment resolves TI-08.
 *
 * @see "Tech spec S4.5, TI-08"
 */
public record MarketSnapshotPayload(
        String marketSnapshotId,
        Scope scope,
        String tenantId,
        SnapshotKind kind,
        LocalDate asOfDate,
        Instant fixingKnowledgeCut,
        SignOffStatus signOffStatus,
        List<SpotQuote> spots,
        Map<CurrencyPair, List<ForwardPillar>> forwards,
        List<DiscountCurvePayload> discountCurves,
        int chunkIndex,
        int chunkCount,
        boolean completionMarker) {

    public MarketSnapshotPayload {
        Objects.requireNonNull(marketSnapshotId, "marketSnapshotId must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(asOfDate, "asOfDate must not be null");
        Objects.requireNonNull(fixingKnowledgeCut, "fixingKnowledgeCut must not be null");
        Objects.requireNonNull(signOffStatus, "signOffStatus must not be null");
        if (chunkCount <= 0) {
            throw new IllegalArgumentException("chunkCount must be positive");
        }
        if (chunkIndex < 0 || chunkIndex >= chunkCount) {
            throw new IllegalArgumentException("chunkIndex must be in [0, chunkCount)");
        }
        spots = spots == null ? List.of() : List.copyOf(spots);
        forwards = forwards == null ? Map.of() : Map.copyOf(forwards);
        discountCurves = discountCurves == null ? List.of() : List.copyOf(discountCurves);
    }
}
