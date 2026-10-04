package com.power.fx.api.model;

/**
 * The settlement lifecycle state of an amount. Leg-2 input selection in
 * the chain engine keys on this field only, never on {@link RateFinality}
 * (S6.14).
 *
 * @see "Tech spec S4.1"
 */
public enum SettlementAmountState {
    UNINVOICED,
    INVOICED,
    SETTLED
}
