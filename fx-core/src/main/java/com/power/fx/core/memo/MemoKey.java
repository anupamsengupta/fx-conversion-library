package com.power.fx.core.memo;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Keys the resolved <em>rate</em>, not the amount, so a {@code batch} over
 * many conversions on few pairs resolves each distinct rate once per
 * pinned snapshot (S6.15, FS S18). Amount application and rounding are
 * per-item and never memoised.
 *
 * @see "Tech spec S6.15"
 */
public record MemoKey(
        String routeKindHint,
        String fromCcy,
        String toCcy,
        LocalDate resolvedFxDate,
        LocalDate valueDate,
        long policyDigest,
        String sourcePriorityDigest,
        String fixingSelectionDigest) {

    public MemoKey {
        Objects.requireNonNull(routeKindHint, "routeKindHint must not be null");
        Objects.requireNonNull(fromCcy, "fromCcy must not be null");
        Objects.requireNonNull(toCcy, "toCcy must not be null");
        Objects.requireNonNull(resolvedFxDate, "resolvedFxDate must not be null");
        Objects.requireNonNull(sourcePriorityDigest, "sourcePriorityDigest must not be null");
        Objects.requireNonNull(fixingSelectionDigest, "fixingSelectionDigest must not be null");
    }
}
