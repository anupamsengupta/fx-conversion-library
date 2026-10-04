package com.power.fx.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One PDR pricing-day observation, preserving PDR order (D-08, FS S10.2).
 *
 * @see "Tech spec S4.6"
 */
public record PricingObservation(
        int sequence,
        LocalDate observationDate,
        Rational weight,
        String componentRef,
        BigDecimal volume,
        boolean duplicate) {

    public PricingObservation {
        Objects.requireNonNull(observationDate, "observationDate must not be null");
        Objects.requireNonNull(weight, "weight must not be null");
        if (weight.den().signum() <= 0) {
            throw new IllegalArgumentException("weight denominator must be positive");
        }
    }
}
