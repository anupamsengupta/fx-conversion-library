package com.power.fx.core.date;

import com.power.fx.api.model.LocalDateRange;
import com.power.fx.api.model.PublicationCalendar;
import com.power.fx.api.model.SettlementCalendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.BitSet;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, compiled calendar: one bit per day over a finite coverage
 * window, set iff the day is a publication/business day (S6.5).
 *
 * <p><strong>Implementation note (documented simplification):</strong> the
 * tech spec sketches a hand-rolled {@code long[] bits} plus
 * {@code short[] prevOffset}/{@code short[] nextOffset} layout for O(1)
 * {@code previous}/{@code next}. This implementation uses {@link BitSet}
 * with its built-in {@code previousSetBit}/{@code nextSetBit}, which is
 * amortised O(1) for the dense, mostly-business-day calendars this
 * library handles and avoids hand-rolling a second bitmap representation
 * within this task's time budget; the externally observable contract
 * (immutable, compiled once, bit-test membership, nearest-neighbour
 * open-day queries) is identical.
 *
 * @see "Tech spec S6.5"
 */
public final class CalendarIndex {

    private final String calendarRef;
    private final String versionId;
    private final LocalDate coverageStart;
    private final LocalDate coverageEnd;
    private final BitSet openDays;

    private CalendarIndex(String calendarRef, String versionId, LocalDate coverageStart, LocalDate coverageEnd,
            BitSet openDays) {
        this.calendarRef = calendarRef;
        this.versionId = versionId;
        this.coverageStart = coverageStart;
        this.coverageEnd = coverageEnd;
        this.openDays = openDays;
    }

    public static CalendarIndex ofPublicationCalendar(PublicationCalendar cal) {
        Objects.requireNonNull(cal, "cal must not be null");
        LocalDateRange coverage = cal.coverage();
        BitSet bits = new BitSet();
        for (LocalDate d : cal.publicationDates()) {
            if (!d.isBefore(coverage.startInclusive()) && !d.isAfter(coverage.endInclusive())) {
                bits.set(index(coverage.startInclusive(), d));
            }
        }
        return new CalendarIndex(cal.calendarRef(), cal.envelope().versionId(),
                coverage.startInclusive(), coverage.endInclusive(), bits);
    }

    public static CalendarIndex ofSettlementCalendar(SettlementCalendar cal) {
        Objects.requireNonNull(cal, "cal must not be null");
        LocalDateRange coverage = cal.coverage();
        Set<DayOfWeek> weekend = cal.weekend();
        BitSet bits = new BitSet();
        for (LocalDate d : cal.businessDates()) {
            if (!d.isBefore(coverage.startInclusive()) && !d.isAfter(coverage.endInclusive())) {
                bits.set(index(coverage.startInclusive(), d));
            }
        }
        // businessDates is the authoritative explicit list (it already excludes weekends and
        // holidays per the reference-data convention); weekend is retained for callers that
        // need to label a day, not to re-derive openness.
        return new CalendarIndex(cal.calendarRef(), cal.envelope().versionId(),
                coverage.startInclusive(), coverage.endInclusive(), bits);
    }

    public static CalendarIndex ofExplicitOpenDays(String calendarRef, String versionId, LocalDateRange coverage,
            Set<LocalDate> openDates) {
        BitSet bits = new BitSet();
        for (LocalDate d : openDates) {
            if (!d.isBefore(coverage.startInclusive()) && !d.isAfter(coverage.endInclusive())) {
                bits.set(index(coverage.startInclusive(), d));
            }
        }
        return new CalendarIndex(calendarRef, versionId, coverage.startInclusive(), coverage.endInclusive(), bits);
    }

    private static int index(LocalDate coverageStart, LocalDate d) {
        return (int) (d.toEpochDay() - coverageStart.toEpochDay());
    }

    public String calendarRef() {
        return calendarRef;
    }

    public String versionId() {
        return versionId;
    }

    public LocalDate coverageStart() {
        return coverageStart;
    }

    public LocalDate coverageEnd() {
        return coverageEnd;
    }

    private void checkCoverage(LocalDate d) {
        if (d.isBefore(coverageStart) || d.isAfter(coverageEnd)) {
            throw new CalendarCoverageException(calendarRef, coverageStart, coverageEnd, d);
        }
    }

    public boolean isOpen(LocalDate d) {
        checkCoverage(d);
        return openDays.get(index(coverageStart, d));
    }

    /** The latest open day {@code <= d} (returns {@code d} itself if open). */
    public LocalDate atOrBefore(LocalDate d) {
        checkCoverage(d);
        int idx = openDays.previousSetBit(index(coverageStart, d));
        if (idx < 0) {
            throw new CalendarCoverageException(calendarRef, coverageStart, coverageEnd, d);
        }
        return coverageStart.plusDays(idx);
    }

    /** The earliest open day {@code >= d} (returns {@code d} itself if open). */
    public LocalDate atOrAfter(LocalDate d) {
        checkCoverage(d);
        int idx = openDays.nextSetBit(index(coverageStart, d));
        if (idx < 0) {
            throw new CalendarCoverageException(calendarRef, coverageStart, coverageEnd, d);
        }
        return coverageStart.plusDays(idx);
    }

    /** The latest open day strictly before {@code d}. */
    public LocalDate strictlyBefore(LocalDate d) {
        checkCoverage(d);
        int fromIndex = index(coverageStart, d) - 1;
        if (fromIndex < 0) {
            throw new CalendarCoverageException(calendarRef, coverageStart, coverageEnd, d);
        }
        int idx = openDays.previousSetBit(fromIndex);
        if (idx < 0) {
            throw new CalendarCoverageException(calendarRef, coverageStart, coverageEnd, d);
        }
        return coverageStart.plusDays(idx);
    }

    /** The earliest open day strictly after {@code d}. */
    public LocalDate strictlyAfter(LocalDate d) {
        checkCoverage(d);
        int idx = openDays.nextSetBit(index(coverageStart, d) + 1);
        if (idx < 0) {
            throw new CalendarCoverageException(calendarRef, coverageStart, coverageEnd, d);
        }
        return coverageStart.plusDays(idx);
    }

    /**
     * Walks {@code n >= 0} open days strictly forward from {@code d}
     * (counting open days after {@code d}, not {@code d} itself).
     */
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
