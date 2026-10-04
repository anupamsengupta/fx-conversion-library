package com.power.fx.api.request;

import com.power.fx.api.model.CurrencyCode;
import com.power.fx.api.model.DateRule;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Requests the revaluation helper (D-04, FS S9.3): a signed foreign
 * amount against a caller-supplied carrying amount or carrying rate
 * (exactly one of the two is supplied).
 *
 * @see "Tech spec S4.7"
 */
public record MonetaryRevaluationRequest(
        FxRequestContext context,
        CurrencyCode foreignCurrency,
        BigDecimal signedForeignAmount,
        BigDecimal carryingFunctionalAmount,
        BigDecimal carryingRate,
        DateRule rule) implements FxRequest {

    private static final Set<DateRule> ALLOWED_RULES = EnumSet.of(DateRule.CLOSING_RATE, DateRule.SETTLEMENT_DATE);

    public MonetaryRevaluationRequest {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(foreignCurrency, "foreignCurrency must not be null");
        Objects.requireNonNull(signedForeignAmount, "signedForeignAmount must not be null");
        Objects.requireNonNull(rule, "rule must not be null");
        if (!ALLOWED_RULES.contains(rule)) {
            throw new IllegalArgumentException("rule must be CLOSING_RATE or SETTLEMENT_DATE, got: " + rule);
        }
        if ((carryingFunctionalAmount == null) == (carryingRate == null)) {
            throw new IllegalArgumentException(
                    "exactly one of carryingFunctionalAmount/carryingRate must be supplied");
        }
    }
}
