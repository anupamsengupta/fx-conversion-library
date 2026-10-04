package com.power.fx.api.model;

/**
 * Unified averaging method selector (D-07). {@code NONE} still passes
 * through the same {@code ObservationSetBuilder} code path as a
 * multi-observation average, producing exactly one observation -- no
 * bypass branch exists.
 *
 * @see "Tech spec S4.1"
 */
public enum AveragingMethod {
    NONE,
    RATE_AVERAGE,
    PRICE_MATCHED
}
