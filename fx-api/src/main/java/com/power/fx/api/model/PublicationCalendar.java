package com.power.fx.api.model;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * A fixing-source publication calendar: the finite coverage window and
 * the set of dates on which the source actually publishes.
 *
 * @see "Tech spec S4.4"
 */
public record PublicationCalendar(
        VersionEnvelope envelope,
        String calendarRef,
        ZoneId zone,
        LocalDateRange coverage,
        SortedSet<LocalDate> publicationDates) {

    public PublicationCalendar {
        Objects.requireNonNull(envelope, "envelope must not be null");
        Objects.requireNonNull(calendarRef, "calendarRef must not be null");
        Objects.requireNonNull(zone, "zone must not be null");
        Objects.requireNonNull(coverage, "coverage must not be null");
        publicationDates = publicationDates == null
                ? Collections.unmodifiableSortedSet(new TreeSet<>())
                : Collections.unmodifiableSortedSet(new TreeSet<>(publicationDates));
    }
}
