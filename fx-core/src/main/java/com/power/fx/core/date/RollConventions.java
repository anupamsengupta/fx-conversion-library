package com.power.fx.core.date;

import com.power.fx.api.model.RollConvention;

import java.time.LocalDate;

/**
 * Rolls a value date onto an open day of a joint settlement calendar per
 * {@code rollConvention} (FS S7, tech spec S6.5 point 3).
 */
public final class RollConventions {

    private RollConventions() {
    }

    public static LocalDate apply(LocalDate date, JointCalendarIndex calendar, RollConvention convention) {
        if (calendar.isOpen(date)) {
            return date;
        }
        return switch (convention) {
            case FOLLOWING -> calendar.strictlyAfter(date);
            case PRECEDING -> calendar.strictlyBefore(date);
            case MODIFIED_FOLLOWING -> {
                LocalDate next = calendar.strictlyAfter(date);
                yield next.getMonthValue() == date.getMonthValue() ? next : calendar.strictlyBefore(date);
            }
            case MODIFIED_PRECEDING -> {
                LocalDate prev = calendar.strictlyBefore(date);
                yield prev.getMonthValue() == date.getMonthValue() ? prev : calendar.strictlyAfter(date);
            }
        };
    }

    public static LocalDate apply(LocalDate date, CalendarIndex calendar, RollConvention convention) {
        if (calendar.isOpen(date)) {
            return date;
        }
        return switch (convention) {
            case FOLLOWING -> calendar.strictlyAfter(date);
            case PRECEDING -> calendar.strictlyBefore(date);
            case MODIFIED_FOLLOWING -> {
                LocalDate next = calendar.strictlyAfter(date);
                yield next.getMonthValue() == date.getMonthValue() ? next : calendar.strictlyBefore(date);
            }
            case MODIFIED_PRECEDING -> {
                LocalDate prev = calendar.strictlyBefore(date);
                yield prev.getMonthValue() == date.getMonthValue() ? prev : calendar.strictlyAfter(date);
            }
        };
    }
}
