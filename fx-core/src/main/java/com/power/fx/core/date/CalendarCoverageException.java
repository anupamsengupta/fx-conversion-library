package com.power.fx.core.date;

import java.time.LocalDate;

/**
 * Raised when a date query falls outside a {@link CalendarIndex}'s loaded
 * coverage window. The resolution layer converts this into
 * {@code FX_E_DATA_NOT_LOADED} with {@code details.calendarRef} and the
 * loaded bounds (A-14, OQ-T02): a calendar-coverage failure reuses the
 * existing "data not loaded" code rather than minting a new one, per the
 * tech spec's own stated (if flagged-open) decision.
 *
 * @see "Tech spec S6.5, A-14"
 */
public final class CalendarCoverageException extends RuntimeException {

    private final String calendarRef;
    private final LocalDate coverageStart;
    private final LocalDate coverageEnd;
    private final LocalDate requested;

    public CalendarCoverageException(String calendarRef, LocalDate coverageStart, LocalDate coverageEnd,
            LocalDate requested) {
        super("date " + requested + " outside coverage [" + coverageStart + ", " + coverageEnd
                + "] of calendar " + calendarRef);
        this.calendarRef = calendarRef;
        this.coverageStart = coverageStart;
        this.coverageEnd = coverageEnd;
        this.requested = requested;
    }

    public String calendarRef() {
        return calendarRef;
    }

    public LocalDate coverageStart() {
        return coverageStart;
    }

    public LocalDate coverageEnd() {
        return coverageEnd;
    }

    public LocalDate requested() {
        return requested;
    }
}
