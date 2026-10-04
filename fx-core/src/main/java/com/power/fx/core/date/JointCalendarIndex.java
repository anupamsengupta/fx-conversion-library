package com.power.fx.core.date;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Intersection of several {@link CalendarIndex}es: a day is open iff every
 * constituent calendar reports it open. Coverage is the intersection of
 * the constituent coverage windows (S6.5).
 *
 * <p>Memoised per sorted {@code (calendarRef, versionId)} set inside one
 * {@code ReferenceCatalogue} generation by {@link
 * com.power.fx.core.cache.ReferenceCatalogue#jointCalendar(List)}, so each
 * joint calendar is built at most once per generation.
 */
public final class JointCalendarIndex {

    private final List<CalendarIndex> constituents;
    private final LocalDate coverageStart;
    private final LocalDate coverageEnd;

    private JointCalendarIndex(List<CalendarIndex> constituents, LocalDate coverageStart, LocalDate coverageEnd) {
        this.constituents = constituents;
        this.coverageStart = coverageStart;
        this.coverageEnd = coverageEnd;
    }

    public static JointCalendarIndex intersect(List<CalendarIndex> calendars) {
        Objects.requireNonNull(calendars, "calendars must not be null");
        if (calendars.isEmpty()) {
            throw new IllegalArgumentException("at least one calendar is required");
        }
        LocalDate start = calendars.get(0).coverageStart();
        LocalDate end = calendars.get(0).coverageEnd();
        for (CalendarIndex c : calendars) {
            if (c.coverageStart().isAfter(start)) {
                start = c.coverageStart();
            }
            if (c.coverageEnd().isBefore(end)) {
                end = c.coverageEnd();
            }
        }
        return new JointCalendarIndex(List.copyOf(calendars), start, end);
    }

    public LocalDate coverageStart() {
        return coverageStart;
    }

    public LocalDate coverageEnd() {
        return coverageEnd;
    }

    private void checkCoverage(LocalDate d) {
        if (d.isBefore(coverageStart) || d.isAfter(coverageEnd)) {
            throw new CalendarCoverageException("joint(" + constituents.size() + ")", coverageStart, coverageEnd, d);
        }
    }

    public boolean isOpen(LocalDate d) {
        checkCoverage(d);
        for (CalendarIndex c : constituents) {
            if (!c.isOpen(d)) {
                return false;
            }
        }
        return true;
    }

    public LocalDate strictlyAfter(LocalDate d) {
        checkCoverage(d);
        LocalDate candidate = d.plusDays(1);
        while (true) {
            if (candidate.isAfter(coverageEnd)) {
                throw new CalendarCoverageException("joint(" + constituents.size() + ")", coverageStart, coverageEnd, d);
            }
            if (isOpen(candidate)) {
                return candidate;
            }
            candidate = candidate.plusDays(1);
        }
    }

    public LocalDate strictlyBefore(LocalDate d) {
        checkCoverage(d);
        LocalDate candidate = d.minusDays(1);
        while (true) {
            if (candidate.isBefore(coverageStart)) {
                throw new CalendarCoverageException("joint(" + constituents.size() + ")", coverageStart, coverageEnd, d);
            }
            if (isOpen(candidate)) {
                return candidate;
            }
            candidate = candidate.minusDays(1);
        }
    }

    public LocalDate atOrAfter(LocalDate d) {
        checkCoverage(d);
        LocalDate candidate = d;
        while (!isOpen(candidate)) {
            candidate = candidate.plusDays(1);
            checkCoverage(candidate);
        }
        return candidate;
    }

    public LocalDate atOrBefore(LocalDate d) {
        checkCoverage(d);
        LocalDate candidate = d;
        while (!isOpen(candidate)) {
            candidate = candidate.minusDays(1);
            checkCoverage(candidate);
        }
        return candidate;
    }

    public LocalDate plusBusinessDays(LocalDate d, int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n must not be negative: " + n);
        }
        LocalDate current = d;
        for (int i = 0; i < n; i++) {
            current = strictlyAfter(current);
        }
        return current;
    }
}
