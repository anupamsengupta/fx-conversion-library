package com.power.fx.cdm;

import com.power.fx.api.model.MarketSnapshotPayload;
import com.power.fx.api.model.Scope;
import com.power.fx.api.model.SignOffStatus;
import com.power.fx.api.model.SnapshotKind;

import java.util.List;
import java.util.Map;

/**
 * Maps a {@link CdmFxEvent} carrying {@link com.power.fx.api.model.FxEntityType#MARKET_SNAPSHOT}
 * into a {@link MarketSnapshotPayload} chunk, including
 * {@code chunkIndex}/{@code chunkCount}/{@code completionMarker} per
 * Task 3a.3's explicit acceptance detail.
 *
 * <p><strong>Scaffolded and blocked on TI-01</strong> (implementation
 * plan Phase 3a Task 3a.3): field names below are illustrative only, see
 * {@link CdmFxEvent}'s Javadoc.
 *
 * <p><strong>Known placeholder simplification:</strong> {@code spots},
 * {@code forwards} and {@code discountCurves} are nested, per-pair/
 * per-pillar structures that do not fit a flat {@code Map<String,
 * String>} payload; this mapper deliberately leaves them empty
 * (permitted by {@link MarketSnapshotPayload}'s compact constructor,
 * which coalesces {@code null}/absent collections to empty) rather than
 * inventing a flat encoding for nested market-data structure. A real,
 * structured CDM schema (post TI-01) would carry these as nested
 * objects, not flat strings.
 *
 * <p>Pure function; no transport, no I/O, no dependency on {@code fx-core}.
 *
 * @see "Implementation plan Phase 3a Task 3a.3; tech spec S6.17; TI-01"
 */
final class CdmSnapshotMapper {

    private CdmSnapshotMapper() {
    }

    static MarketSnapshotPayload map(CdmFxEvent event) {
        Map<String, String> f = event.fields();
        Scope scope = CdmFields.parseEnum(event.scope(), Scope.class, "scope");
        return new MarketSnapshotPayload(
                CdmFields.require(f, "marketSnapshotId"),
                scope,
                event.tenantId(),
                CdmFields.requireEnum(f, "kind", SnapshotKind.class),
                CdmFields.requireDate(f, "asOfDate"),
                CdmFields.requireInstant(f, "fixingKnowledgeCut"),
                CdmFields.requireEnum(f, "signOffStatus", SignOffStatus.class),
                List.of(),
                Map.of(),
                List.of(),
                CdmFields.requireInt(f, "chunkIndex"),
                CdmFields.requireInt(f, "chunkCount"),
                CdmFields.optionalBoolean(f, "completionMarker", false));
    }
}
