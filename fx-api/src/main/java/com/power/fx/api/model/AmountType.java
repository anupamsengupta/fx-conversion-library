package com.power.fx.api.model;

/**
 * Classifies the monetary amount carried by a request, used by D-05's
 * amount-type mismatch rejection in the leg chain.
 *
 * @see "Tech spec S4.1"
 */
public enum AmountType {
    NOMINAL,
    NOMINAL_FUTURE,
    PRESENT_VALUE
}
