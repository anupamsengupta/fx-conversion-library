package com.power.fx.api.ingest;

import com.power.fx.api.model.SignOffStatus;
import com.power.fx.api.model.SnapshotKind;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Notification that a market snapshot has become resolvable, delivered
 * to {@code FxEventListener.onSnapshotAvailable}.
 *
 * @see "Tech spec S4.10"
 */
public record SnapshotAvailability(
        String tenantId,
        String marketSnapshotId,
        SnapshotKind kind,
        LocalDate asOfDate,
        Instant fixingKnowledgeCut,
        SignOffStatus signOffStatus) {

    public SnapshotAvailability {
        Objects.requireNonNull(marketSnapshotId, "marketSnapshotId must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(asOfDate, "asOfDate must not be null");
        Objects.requireNonNull(fixingKnowledgeCut, "fixingKnowledgeCut must not be null");
        Objects.requireNonNull(signOffStatus, "signOffStatus must not be null");
    }
}
