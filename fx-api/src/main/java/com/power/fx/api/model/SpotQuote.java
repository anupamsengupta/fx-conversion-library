package com.power.fx.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A spot quote for a pair as of a spot date.
 *
 * @see "Tech spec S4.5"
 */
public record SpotQuote(CurrencyPair pair, BigDecimal rate, LocalDate spotDate) {

    public SpotQuote {
        Objects.requireNonNull(pair, "pair must not be null");
        Objects.requireNonNull(rate, "rate must not be null");
        Objects.requireNonNull(spotDate, "spotDate must not be null");
    }
}
