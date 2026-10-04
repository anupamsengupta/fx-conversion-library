package com.power.fx.core.entitlement;

import com.power.fx.api.model.SourceRight;
import com.power.fx.api.result.DistributionRestriction;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The entitlement rights carried by one derivation step, and the sources
 * that contributed to it (D-10, S6.6). {@link #intersect} is the fold
 * operation {@code RestrictionPropagator} applies across legs/observations
 * (most-restrictive propagation); {@link #unrestricted()} is for
 * reference-data-sourced rates (fixed factor, contract rate, manual
 * override, identity) which carry no licensed market data at all.
 *
 * @see "Tech spec S6.6, D-10"
 */
public record RightsSet(Set<SourceRight> rights, SortedSet<String> contributingSources) {

    public RightsSet {
        rights = rights == null || rights.isEmpty()
                ? EnumSet.noneOf(SourceRight.class)
                : EnumSet.copyOf(rights);
        contributingSources = Collections.unmodifiableSortedSet(
                contributingSources == null ? new TreeSet<>() : new TreeSet<>(contributingSources));
    }

    public static RightsSet unrestricted() {
        return new RightsSet(EnumSet.allOf(SourceRight.class), new TreeSet<>());
    }

    public static RightsSet of(String sourceCode, Set<SourceRight> rights) {
        TreeSet<String> sources = new TreeSet<>();
        sources.add(sourceCode);
        return new RightsSet(rights, sources);
    }

    public RightsSet intersect(RightsSet other) {
        EnumSet<SourceRight> combined = rights.isEmpty() ? EnumSet.noneOf(SourceRight.class) : EnumSet.copyOf(rights);
        combined.retainAll(other.rights);
        TreeSet<String> sources = new TreeSet<>(contributingSources);
        sources.addAll(other.contributingSources);
        return new RightsSet(combined, sources);
    }

    public DistributionRestriction toDistributionRestriction() {
        return new DistributionRestriction(
                rights.contains(SourceRight.VALUATION),
                rights.contains(SourceRight.DISPLAY),
                rights.contains(SourceRight.REDISTRIBUTION),
                new TreeSet<>(contributingSources));
    }
}
