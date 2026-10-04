package com.power.fx.api.model;

import java.math.RoundingMode;
import java.util.Objects;

/**
 * Rounding behaviour for a policy: amount rounding mode (default
 * {@code HALF_UP}, {@code HALF_EVEN} configurable) and optional unit
 * price / rate / average rounding scales (FS S15).
 *
 * @see "Tech spec S4.4"
 */
public record RoundingSpec(
        RoundingMode amountRounding,
        Integer roundUnitPrice,
        Integer roundRate,
        Integer roundAverage) {

    public RoundingSpec {
        Objects.requireNonNull(amountRounding, "amountRounding must not be null");
    }
}
