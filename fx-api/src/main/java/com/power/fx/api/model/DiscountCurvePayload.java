package com.power.fx.api.model;

import java.util.List;
import java.util.Objects;

/**
 * A discount curve for one currency, used by {@code CipForwardCalculator}
 * (CIP forward construction is restricted to discount-factor pillars,
 * OQ-04).
 *
 * @see "Tech spec S4.5"
 */
public record DiscountCurvePayload(CurrencyCode currency, String curveRef, List<DiscountPillar> pillars) {

    public DiscountCurvePayload {
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(curveRef, "curveRef must not be null");
        pillars = pillars == null ? List.of() : List.copyOf(pillars);
    }
}
