package com.power.fx.core.date;

import java.time.LocalDate;

/**
 * {@code spotDate = tradeDate + spotLag} business days over the joint
 * intersection of a pair's spot calendars (FS S7, tech spec S6.5:
 * "{@code SpotDateCalculator} applies {@code spotLag} business days over
 * {@code intersect(spotCalendars)}"). T+1 pairs fall out of
 * {@code spotLag = 1} with no special-casing.
 */
public final class SpotDateCalculator {

    public LocalDate spotDate(LocalDate tradeDate, JointCalendarIndex spotCalendars, int spotLag) {
        LocalDate rolled = spotCalendars.atOrAfter(tradeDate);
        return spotCalendars.plusBusinessDays(rolled, spotLag);
    }
}
