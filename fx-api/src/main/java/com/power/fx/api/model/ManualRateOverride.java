package com.power.fx.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A manually approved rate override for one pair on one FX date (D-11).
 *
 * @see "Tech spec S4.4"
 */
public record ManualRateOverride(
        VersionEnvelope envelope,
        Scope scope,
        CurrencyPair pair,
        LocalDate fxDate,
        String sourceCode,
        BigDecimal rate,
        String reasonCode,
        String ticketRef) {

    public ManualRateOverride {
        Objects.requireNonNull(envelope, "envelope must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(pair, "pair must not be null");
        Objects.requireNonNull(fxDate, "fxDate must not be null");
        Objects.requireNonNull(rate, "rate must not be null");
        Objects.requireNonNull(reasonCode, "reasonCode must not be null");
    }
}
