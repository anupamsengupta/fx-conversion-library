package com.power.fx.api.model;

/**
 * Bare-enum discriminant for fixing version selection, retained for CDM
 * payload mapping (A-16). The resolution-time carrier of this choice is
 * the sealed {@link FixingVersionSelection}, which additionally carries
 * the {@code asOfKnowledge} instant for the {@code AS_OF_KNOWLEDGE} case.
 *
 * @see "Tech spec S4.1, A-16"
 */
public enum FixingVersionPolicy {
    FIRST_OFFICIAL,
    LATEST_CORRECTED,
    AS_OF_KNOWLEDGE
}
