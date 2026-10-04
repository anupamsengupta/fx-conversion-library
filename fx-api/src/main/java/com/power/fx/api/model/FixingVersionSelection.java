package com.power.fx.api.model;

import java.time.Instant;
import java.util.Objects;

/**
 * The resolution-time carrier of a policy's fixing version selection
 * (A-16). {@link FixingVersionPolicy} is retained separately as the bare
 * enum discriminant for CDM payload mapping; this sealed type is what
 * {@code FixingVersionSelector} (fx-core) actually consumes, because
 * {@code AS_OF_KNOWLEDGE} must carry its instant.
 *
 * @see "Tech spec S4.4, A-16"
 */
public sealed interface FixingVersionSelection
        permits FixingVersionSelection.FirstOfficial,
                FixingVersionSelection.LatestCorrected,
                FixingVersionSelection.AsOfKnowledge {

    record FirstOfficial() implements FixingVersionSelection {}

    record LatestCorrected() implements FixingVersionSelection {}

    record AsOfKnowledge(Instant asOf) implements FixingVersionSelection {
        public AsOfKnowledge {
            Objects.requireNonNull(asOf, "asOf must not be null");
        }
    }
}
