package com.power.fx.api.request;

import com.power.fx.api.model.CurrencyCode;

import java.util.Objects;

/**
 * Requests a resolved rate for a pair, with no amount applied.
 *
 * @see "Tech spec S4.7"
 */
public record RateRequest(FxRequestContext context, CurrencyCode from, CurrencyCode to) implements FxRequest {

    public RateRequest {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
    }
}
