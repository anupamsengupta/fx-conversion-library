package com.power.fx.api.model;

/**
 * Whether a spot rate used for MTM is short-end adjusted to the
 * valuation date (OQ-06; this spec adopts {@code TO_VALUATION_DATE} as
 * the proposed default per {@code FxPolicy}, see S10a.2/OQ-T06 for the
 * ON/TN-absent fallback).
 *
 * @see "Tech spec S4.1"
 */
public enum SpotAdjustment {
    TO_VALUATION_DATE,
    NONE
}
