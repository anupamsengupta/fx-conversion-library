package com.power.fx.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A single forward curve pillar: exactly one of {@code points} /
 * {@code outright} is populated.
 *
 * @see "Tech spec S4.5"
 */
public record ForwardPillar(String tenor, LocalDate valueDate, BigDecimal points, BigDecimal outright) {

    public ForwardPillar {
        Objects.requireNonNull(tenor, "tenor must not be null");
        Objects.requireNonNull(valueDate, "valueDate must not be null");
        if ((points == null) == (outright == null)) {
            throw new IllegalArgumentException(
                    "exactly one of points/outright must be non-null");
        }
    }
}
