package com.power.fx.api.request;

import com.power.fx.api.model.CurrencyCode;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Requests an averaged series conversion. {@code periodAmount} is used
 * for {@code RATE_AVERAGE} applied to a period total and is nullable
 * otherwise.
 *
 * @see "Tech spec S4.7"
 */
public record SeriesRequest(
        FxRequestContext context,
        CurrencyCode from,
        CurrencyCode to,
        BigDecimal periodAmount,
        boolean isUnitPrice) implements FxRequest {

    public SeriesRequest {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(from, "from must not be null");
        Objects.requireNonNull(to, "to must not be null");
    }
}
