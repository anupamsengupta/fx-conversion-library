package com.power.fx.api.request;

import com.power.fx.api.model.CurrencyCode;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * One price observation for {@code PRICE_MATCHED} averaging, keyed by
 * PDR sequence so it can be matched against the corresponding
 * {@code PricingObservation}.
 *
 * @see "Tech spec S4.7"
 */
public record ObservationPrice(int sequence, BigDecimal price, CurrencyCode priceCurrency, BigDecimal volume) {

    public ObservationPrice {
        Objects.requireNonNull(price, "price must not be null");
        Objects.requireNonNull(priceCurrency, "priceCurrency must not be null");
    }
}
