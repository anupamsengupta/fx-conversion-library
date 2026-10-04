package com.power.fx.api.model;

/**
 * A stage in the leg chain built by {@code ChainEngine} (S6.14, FS S9).
 *
 * @see "Tech spec S4.1"
 */
public enum Leg {
    CONTRACT,
    ACCOUNTING_TRANSACTION,
    TRANSLATION,
    MANAGEMENT_VIEW
}
