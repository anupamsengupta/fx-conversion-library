package com.power.fx.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A single discount-factor pillar. The v1.0 schema admits only a
 * discount-factor representation; a zero-rate variant is an OQ-04
 * open item, not implemented here.
 *
 * @see "Tech spec S4.5, OQ-04"
 */
public record DiscountPillar(LocalDate date, BigDecimal discountFactor) {

    public DiscountPillar {
        Objects.requireNonNull(date, "date must not be null");
        Objects.requireNonNull(discountFactor, "discountFactor must not be null");
    }
}
