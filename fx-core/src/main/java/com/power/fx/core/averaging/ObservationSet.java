package com.power.fx.core.averaging;

import java.util.List;

/** An ordered set of {@link Observation}s (S6.11). {@code NONE} is a one-observation set. */
public record ObservationSet(List<Observation> observations) {

    public ObservationSet {
        observations = List.copyOf(observations);
        if (observations.isEmpty()) {
            throw new IllegalArgumentException("an observation set must have at least one observation");
        }
    }
}
