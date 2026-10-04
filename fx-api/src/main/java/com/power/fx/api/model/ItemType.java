package com.power.fx.api.model;

/**
 * Whether an accounting item is monetary or non-monetary, and if
 * non-monetary, whether it carries a historical or fair-value rate. Used
 * to reject a {@code CLOSING_RATE} revaluation against a
 * {@code NON_MONETARY_HISTORICAL} item (F09).
 *
 * @see "Tech spec S4.1"
 */
public enum ItemType {
    MONETARY,
    NON_MONETARY_HISTORICAL,
    NON_MONETARY_FAIR_VALUE
}
