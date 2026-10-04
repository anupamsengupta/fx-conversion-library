package com.power.fx.api.model;

/**
 * One step in the fallback chain run by {@code FallbackChainRunner} when
 * primary rate selection misses (FS S11.3).
 *
 * @see "Tech spec S4.1"
 */
public enum FallbackStepKind {
    ALT_SOURCE,
    PREVIOUS_PUBLICATION_DAY,
    TRIANGULATE,
    INTERPOLATE_FIXINGS,
    FAIL
}
