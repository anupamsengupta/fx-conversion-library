package com.power.fx.api.model;

/**
 * Whether a resolved FX date in the future is treated as requiring a
 * forward rate or a spot rate.
 *
 * @see "Tech spec S4.1"
 */
public enum FutureDateTreatment {
    FORWARD,
    SPOT
}
