package com.power.fx.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A trade-terms contract rate with an effective date range.
 *
 * @see "Tech spec S4.4"
 */
public record ContractRate(
        CurrencyPair pair,
        BigDecimal rate,
        String quotedIn,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        String reference) {

    public ContractRate {
        Objects.requireNonNull(pair, "pair must not be null");
        Objects.requireNonNull(rate, "rate must not be null");
        Objects.requireNonNull(effectiveFrom, "effectiveFrom must not be null");
        if (effectiveTo != null && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException("effectiveTo must not be before effectiveFrom");
        }
    }
}
