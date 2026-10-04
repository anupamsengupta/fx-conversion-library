package com.power.fx.api.model;

import java.util.List;
import java.util.Objects;

/**
 * A legal entity / accounting unit, carrying its (effective-dated, via
 * the envelope's validity window) functional currency and the
 * presentation currencies it reports in.
 *
 * @see "Tech spec S4.4"
 */
public record AccountingUnit(
        VersionEnvelope envelope,
        String unitId,
        String legalEntityId,
        CurrencyCode functionalCurrency,
        List<CurrencyCode> presentationCurrencies,
        String parentUnitId,
        String accountingPolicyRef) {

    public AccountingUnit {
        Objects.requireNonNull(envelope, "envelope must not be null");
        Objects.requireNonNull(unitId, "unitId must not be null");
        Objects.requireNonNull(legalEntityId, "legalEntityId must not be null");
        Objects.requireNonNull(functionalCurrency, "functionalCurrency must not be null");
        presentationCurrencies = presentationCurrencies == null
                ? List.of()
                : List.copyOf(presentationCurrencies);
    }
}
