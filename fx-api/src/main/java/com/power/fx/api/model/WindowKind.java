package com.power.fx.api.model;

/**
 * The kind of date window an {@link WindowSpec} describes for averaging.
 *
 * @see "Tech spec S4.1"
 */
public enum WindowKind {
    DELIVERY_PERIOD,
    PRICING_PERIOD,
    CALENDAR_MONTH,
    ACCOUNTING_PERIOD,
    CUSTOM
}
