package com.power.fx.api.model;

/**
 * How observation weights are derived for averaging. {@code FROM_PRICING_SET}
 * uses the PDR's own exact {@link Rational} weights (D-08).
 *
 * @see "Tech spec S4.1"
 */
public enum Weighting {
    FROM_PRICING_SET,
    EQUAL,
    VOLUME,
    CUSTOM
}
