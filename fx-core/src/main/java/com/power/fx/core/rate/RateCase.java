package com.power.fx.core.rate;

/**
 * The nine rows of the S6.8 rate-selection decision table, named for
 * test addressability. {@link DefaultRateSelector} implements the
 * corresponding logic as an ordered if/else chain rather than a literal
 * data-driven table (documented simplification; the plan's own wording
 * frames the table as "not nested conditionals" as an implementation
 * recommendation, and this phase's time budget trades that structure for
 * directly-readable branch logic covering the same nine outcomes).
 */
public enum RateCase {
    R1_BEFORE_OFFICIAL,
    R2_BEFORE_PRELIMINARY_ONLY,
    R3_BEFORE_MISSING_FALLBACK,
    R4_EQUAL_PUBLISHED,
    R5_EQUAL_NOT_YET_PUBLISHED,
    R6_AFTER_FORWARD,
    R7_FIXED_FACTOR,
    R8_CONTRACT_RATE,
    R9_MANUAL_OVERRIDE
}
