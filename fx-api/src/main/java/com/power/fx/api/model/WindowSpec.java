package com.power.fx.api.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * The averaging observation window: either an explicit start/end or a
 * derived window kind, plus a publication-day lag for sourcing
 * observations relative to the window.
 *
 * @see "Tech spec S4.4"
 */
public record WindowSpec(WindowKind kind, LocalDate start, LocalDate end, int lagPublicationDays) {

    public WindowSpec {
        Objects.requireNonNull(kind, "kind must not be null");
        if (start != null && end != null && start.isAfter(end)) {
            throw new IllegalArgumentException("start must not be after end");
        }
    }
}
