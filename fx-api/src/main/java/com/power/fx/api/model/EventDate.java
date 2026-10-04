package com.power.fx.api.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * A single piece of event-date evidence (e.g. a bill of lading date),
 * ranked against other evidence for the same {@link EventType} and
 * flagged when estimated (see {@link EstimatedEventHandling}).
 *
 * @see "Tech spec S4.7"
 */
public record EventDate(LocalDate date, int sourceRank, String evidenceRef, boolean estimated) {

    public EventDate {
        Objects.requireNonNull(date, "date must not be null");
    }
}
