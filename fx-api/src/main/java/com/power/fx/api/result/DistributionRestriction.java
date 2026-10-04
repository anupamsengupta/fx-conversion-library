package com.power.fx.api.result;

import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The most-restrictive entitlement propagation outcome attached to every
 * result (D-10, FS S12).
 *
 * @see "Tech spec S4.8"
 */
public record DistributionRestriction(
        boolean valuationAllowed,
        boolean displayAllowed,
        boolean redistributionAllowed,
        SortedSet<String> contributingSources) {

    public DistributionRestriction {
        contributingSources = contributingSources == null
                ? Collections.unmodifiableSortedSet(new TreeSet<>())
                : Collections.unmodifiableSortedSet(new TreeSet<>(contributingSources));
    }
}
