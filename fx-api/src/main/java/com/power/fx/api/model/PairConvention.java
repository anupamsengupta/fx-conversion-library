package com.power.fx.api.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Market-quoting and forward-construction convention for a currency pair.
 *
 * @see "Tech spec S4.4"
 */
public record PairConvention(
        VersionEnvelope envelope,
        CurrencyPair marketConvention,
        int pipPrecision,
        BigDecimal pointsScale,
        int spotLag,
        List<String> spotCalendars,
        CurrencyCode triangulationVia,
        ForwardMethod forwardMethod,
        InterpolationMethod interpolation,
        BigDecimal maxExtrapolationYears,
        Map<CurrencyCode, String> discountCurveRefs) {

    public PairConvention {
        Objects.requireNonNull(envelope, "envelope must not be null");
        Objects.requireNonNull(marketConvention, "marketConvention must not be null");
        Objects.requireNonNull(pointsScale, "pointsScale must not be null");
        Objects.requireNonNull(forwardMethod, "forwardMethod must not be null");
        Objects.requireNonNull(interpolation, "interpolation must not be null");
        Objects.requireNonNull(maxExtrapolationYears, "maxExtrapolationYears must not be null");
        spotCalendars = spotCalendars == null ? List.of() : List.copyOf(spotCalendars);
        discountCurveRefs = discountCurveRefs == null ? Map.of() : Map.copyOf(discountCurveRefs);
    }
}
