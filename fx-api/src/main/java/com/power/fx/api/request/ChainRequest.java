package com.power.fx.api.request;

import com.power.fx.api.model.CurrencyCode;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Requests a full leg chain conversion (CONTRACT, ACCOUNTING_TRANSACTION,
 * TRANSLATION, and zero or more MANAGEMENT_VIEW legs for multiple report
 * currencies, A-18).
 *
 * @see "Tech spec S4.7"
 */
public record ChainRequest(
        FxRequestContext context,
        CurrencyCode priceCurrency,
        BigDecimal priceAmount,
        CurrencyCode settlementCurrency,
        PolicyRef contractPolicy,
        PolicyRef accountingPolicy,
        PolicyRef translationPolicy,
        List<CurrencyCode> presentationCurrencies,
        List<ManagementViewSpec> managementViews) implements FxRequest {

    public ChainRequest {
        Objects.requireNonNull(context, "context must not be null");
        Objects.requireNonNull(priceCurrency, "priceCurrency must not be null");
        Objects.requireNonNull(priceAmount, "priceAmount must not be null");
        Objects.requireNonNull(settlementCurrency, "settlementCurrency must not be null");
        presentationCurrencies = presentationCurrencies == null
                ? List.of()
                : List.copyOf(presentationCurrencies);
        managementViews = managementViews == null ? List.of() : List.copyOf(managementViews);
    }
}
