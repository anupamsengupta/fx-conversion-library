package com.power.fx.api.error;

/**
 * Lineage reason labels (FS S7.1, S10.3, S11.1-11.4, S12). Not errors --
 * these annotate how a rate or amount was derived, for display and audit.
 *
 * @see "Tech spec S4.2"
 */
public enum FxReason {
    IDENTITY,
    FIXING,
    PRELIM_FIXING,
    PRE_PUBLICATION,
    FORWARD_RATE,
    FIXED_FACTOR,
    CONTRACT_RATE,
    MANUAL_OVERRIDE,
    INVERTED,
    TRIANGULATED,
    MIXED_SOURCE,
    DATE_RULE_ADJUSTED,
    SKIPPED_OBSERVATION,
    PARTIAL_PERIOD,
    AVERAGE_INVERTED,
    FALLBACK_ALT_SOURCE,
    FALLBACK_STALE,
    FALLBACK_TRIANGULATED,
    FALLBACK_INTERPOLATED,
    RATE_NOT_FOUND,
    EXTRAPOLATED,
    UPSTREAM_UNRESOLVED,
    SPOT_ADJUSTED,
    CIP_DERIVED,
    HYBRID_ANCHORED,
    SOURCE_ENTITLEMENT_RESTRICTED,
    VIEW_ONLY
}
