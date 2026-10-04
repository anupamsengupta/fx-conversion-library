package com.power.fx.api.request;

import com.power.fx.api.model.CurrencyCode;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Requests a single amount (or unit price) be converted from one
 * currency to another.
 *
 * @see "Tech spec S4.7"
 */
public record ConversionRequest(
        FxRequestContext context,
        CurrencyCode from,
        CurrencyCode to,
        BigDecimal amount,
        boolean isUnitPrice) implements FxRequest {

    public ConversionRequest {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
    }
}
