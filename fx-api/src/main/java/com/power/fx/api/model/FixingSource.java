package com.power.fx.api.model;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;

/**
 * A fixing publication source (e.g. ECB, WMR), its cutoff and the pairs
 * it publishes.
 *
 * @see "Tech spec S4.4"
 */
public record FixingSource(
        VersionEnvelope envelope,
        String sourceCode,
        LocalTime cutoffTime,
        ZoneId cutoffZone,
        String publicationCalendarRef,
        Set<CurrencyPair> pairsPublished,
        String ndfTemplate,
        UsageClass usageClass) {

    public FixingSource {
        Objects.requireNonNull(envelope, "envelope must not be null");
        Objects.requireNonNull(sourceCode, "sourceCode must not be null");
        Objects.requireNonNull(cutoffTime, "cutoffTime must not be null");
        Objects.requireNonNull(cutoffZone, "cutoffZone must not be null");
        Objects.requireNonNull(publicationCalendarRef, "publicationCalendarRef must not be null");
        Objects.requireNonNull(usageClass, "usageClass must not be null");
        pairsPublished = pairsPublished == null ? Set.of() : Set.copyOf(pairsPublished);
    }
}
