package com.power.fx.api.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * A settlement (business-day) calendar: finite coverage window, business
 * dates and the weekend definition.
 *
 * @see "Tech spec S4.4"
 */
public record SettlementCalendar(
        VersionEnvelope envelope,
        String calendarRef,
        ZoneId zone,
        LocalDateRange coverage,
        SortedSet<LocalDate> businessDates,
        Set<DayOfWeek> weekend) {

    public SettlementCalendar {
        Objects.requireNonNull(envelope, "envelope must not be null");
        Objects.requireNonNull(calendarRef, "calendarRef must not be null");
        Objects.requireNonNull(zone, "zone must not be null");
        Objects.requireNonNull(coverage, "coverage must not be null");
        businessDates = businessDates == null
                ? Collections.unmodifiableSortedSet(new TreeSet<>())
                : Collections.unmodifiableSortedSet(new TreeSet<>(businessDates));
        weekend = weekend == null ? Set.of() : Set.copyOf(weekend);
    }
}
