package com.power.fx.api.model;

/**
 * Settlement-calendar roll convention applied by {@code RollConventions}
 * when deriving a value date (FS S7).
 *
 * @see "Tech spec S4.1"
 */
public enum RollConvention {
    FOLLOWING,
    MODIFIED_FOLLOWING,
    PRECEDING,
    MODIFIED_PRECEDING
}
