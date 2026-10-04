package com.power.fx.api.model;

/**
 * Which calendar an {@link OffsetSpec} offset is counted against.
 *
 * @see "Tech spec S4.1"
 */
public enum OffsetCalendarKind {
    FIXING_SOURCE,
    PAIR_SETTLEMENT_JOINT,
    CURRENCY,
    CUSTOM,
    CALENDAR_DAYS
}
