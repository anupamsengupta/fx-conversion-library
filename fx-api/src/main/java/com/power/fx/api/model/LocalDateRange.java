package com.power.fx.api.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * An inclusive date range, e.g. a calendar coverage window or a prewarm
 * request range.
 *
 * @see "Tech spec S4.3"
 */
public record LocalDateRange(LocalDate startInclusive, LocalDate endInclusive) {

    public LocalDateRange {
        Objects.requireNonNull(startInclusive, "startInclusive must not be null");
        Objects.requireNonNull(endInclusive, "endInclusive must not be null");
        if (startInclusive.isAfter(endInclusive)) {
            throw new IllegalArgumentException("startInclusive must not be after endInclusive");
        }
    }
}
