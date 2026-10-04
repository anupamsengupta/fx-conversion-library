package com.power.fx.api.model;

import java.util.Objects;

/**
 * An ordered currency pair, base quoted in terms of quote.
 *
 * @see "Tech spec S4.3"
 */
public record CurrencyPair(CurrencyCode base, CurrencyCode quote) {

    public CurrencyPair {
        Objects.requireNonNull(base, "base must not be null");
        Objects.requireNonNull(quote, "quote must not be null");
    }

    public CurrencyPair inverse() {
        return new CurrencyPair(quote, base);
    }

    public boolean isIdentity() {
        return base.equals(quote);
    }

    /** Canonical display form, e.g. {@code "EUR/USD"}. Used in lineage and memo keys. */
    public String canonical() {
        return base.value() + "/" + quote.value();
    }
}
