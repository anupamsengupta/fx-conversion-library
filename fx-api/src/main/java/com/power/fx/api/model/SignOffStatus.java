package com.power.fx.api.model;

/**
 * Whether a market snapshot has been signed off for official use. Used
 * with {@link RunMode#OFFICIAL} to raise {@code FX_V_UNSIGNED_SNAPSHOT}
 * (A-09).
 *
 * @see "Tech spec S4.1"
 */
public enum SignOffStatus {
    SIGNED_OFF,
    UNSIGNED
}
