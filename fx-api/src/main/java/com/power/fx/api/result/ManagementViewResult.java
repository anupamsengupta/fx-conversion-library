package com.power.fx.api.result;

import com.power.fx.api.model.CurrencyCode;

import java.util.Objects;

/**
 * One on-read management view conversion. {@code persistable} is always
 * {@code false} for the enclosing {@link LegResult}-equivalent semantics
 * of a management view (D-16) -- this type does not itself carry a
 * {@code persistable} field because it is never persistable by
 * definition; see {@code ChainResult}.
 *
 * @see "Tech spec S4.8, D-16"
 */
public record ManagementViewResult(CurrencyCode reportCurrency, ConversionResult conversion) {

    public ManagementViewResult {
        Objects.requireNonNull(reportCurrency, "reportCurrency must not be null");
        Objects.requireNonNull(conversion, "conversion must not be null");
    }
}
