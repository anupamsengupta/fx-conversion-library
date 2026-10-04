package com.power.fx.api.result;

import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.Leg;
import com.power.fx.api.model.Purpose;

import java.util.Objects;

/**
 * One resolved leg of a {@link ChainResult}.
 *
 * @see "Tech spec S4.8"
 */
public record LegResult(
        Leg leg,
        Purpose purpose,
        ConversionResult conversion,
        CurrencyCode functionalCurrency,
        String accountingUnitId,
        boolean persistable) {

    public LegResult {
        Objects.requireNonNull(leg, "leg must not be null");
        Objects.requireNonNull(purpose, "purpose must not be null");
        Objects.requireNonNull(conversion, "conversion must not be null");
    }
}
