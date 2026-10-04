package com.power.fx.api.model;

import java.util.List;
import java.util.Objects;

/**
 * The PDR's set of pricing observations for one series, consumed by
 * value rather than by a shared {@code pdr-api} dependency (A-08). The
 * compact constructor enforces that {@code sequence} values are distinct
 * and strictly ascending, and that every observation's weight denominator
 * is positive -- never collapsing duplicates (D-08, FS S10.2).
 *
 * @see "Tech spec S4.6, A-08, D-08"
 */
public record PricingDaySet(PdrRef ref, List<PricingObservation> observations, String status) {

    public PricingDaySet {
        Objects.requireNonNull(ref, "ref must not be null");
        Objects.requireNonNull(observations, "observations must not be null");
        observations = List.copyOf(observations);

        int previousSequence = Integer.MIN_VALUE;
        boolean first = true;
        for (PricingObservation observation : observations) {
            if (observation.weight().den().signum() <= 0) {
                throw new IllegalArgumentException(
                        "weight denominator must be positive for sequence " + observation.sequence());
            }
            if (!first && observation.sequence() <= previousSequence) {
                throw new IllegalArgumentException(
                        "observation sequence values must be distinct and strictly ascending: "
                                + previousSequence + " followed by " + observation.sequence());
            }
            previousSequence = observation.sequence();
            first = false;
        }
    }
}
