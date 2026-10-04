package com.power.fx.api.model;

/**
 * How the set of observation dates for averaging is derived.
 *
 * @see "Tech spec S4.1"
 */
public enum ObservationSetKind {
    FROM_PRICING_SET,
    FX_FIXING_DAYS_IN_WINDOW,
    DELIVERY_DAYS,
    EXPLICIT
}
