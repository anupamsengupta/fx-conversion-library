package com.power.fx.api.model;

/**
 * A date offset expressed in a number of days against a named calendar
 * kind.
 *
 * @see "Tech spec S4.4"
 */
public record OffsetSpec(int days, OffsetCalendarKind calendarKind, String customCalendarRef) {

    public OffsetSpec {
        if (calendarKind == null) {
            throw new NullPointerException("calendarKind must not be null");
        }
        if (calendarKind == OffsetCalendarKind.CUSTOM && customCalendarRef == null) {
            throw new IllegalArgumentException("customCalendarRef is required when calendarKind is CUSTOM");
        }
    }
}
